
package com.pool.db;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import snaq.util.logging.LogUtil;


public abstract class BasePool<T extends Reusable> implements Comparable<BasePool<T>>
{
  /** SLF4J logger instance for writing log entries. */
  protected Logger log;
  /** Enumeration of selection strategies. */
  public enum Strategy { SELECT_FIFO, SELECT_LIFO, SELECT_RANDOM }
  /** Strategy for selecting next object from the pool. */
  private Strategy selection = Strategy.SELECT_LIFO;
  /** Random number generator instance. */
  private Random randGen = null;
  /** Custom logging utility for non-standard log writing. */
  private LogUtil logUtil;
  /** Name of the pool. */
  private String name;
  /** Counter for naming unnamed pools. */
  private static int unnamedCount = 0;
  /** Collection to hold pooled objects. */
  private List<TimeWrapper<T>> free;
  /** Collection to hold checked out objects. */
  private List<T> used;
  /** Minimum number of objects that can be pooled at any time (0=none). */
  private int minPool;
  /** Maximum number of objects that can be pooled at any time (0=none). */
  private int maxPool;
  /** Maximum number of objects that can be checked out at any time (0=infinite). */
  private int maxSize;
  /** Allowed time for pooled objects to be idle before being expired (milliseconds). */
  private long idleTimeout;
  /** Count of number of requests for objects. */
  private long requests;
  /** Count of number of pool hits for objects. */
  private long hits;
  /** Flag indicating whether the pool has been released. */
  private volatile boolean released = false;
  /** Flag determining whether object destruction occurs asynchronously. */
  private boolean asyncDestroy = false;
  /** Event dispatcher thread instance to issue events in a thread-safe manner. */
  private EventDispatcher<ObjectPoolListener<T>,ObjectPoolEvent<T>> eventDispatcher;
  /** Worker thread instance to clean up expired objects. */
  private Cleaner cleaner;
  /** Worker thread instance to initialize new objects. */
  private InitThread initer;
  /** Thread to perform shutdown/release of this pool. */
  private Thread shutdownHook = null;
  /** Shared counter for naming cleaner threads. */
  private static int cleanerCount = 0;
  /** List to hold listeners for {@link ObjectPoolEvent} events. */
  private final List<ObjectPoolListener<T>> listeners = new CopyOnWriteArrayList<>();

  /**
   * Creates new object pool.
   * @param name pool name
   * @param minPool minimum number of pooled objects, or 0 for none
   * @param maxPool maximum number of pooled objects, or 0 for none
   * @param maxSize maximum number of possible objects, or 0 for no limit
   * @param idleTimeout idle timeout for pooled objects, or 0 for no timeout
   */
  @SuppressWarnings("unchecked")
  protected BasePool(String name, int minPool, int maxPool, int maxSize, long idleTimeout)
  {
    Class<? extends List> type = getPoolClass();
    if (type == null)
      throw new NullPointerException("Invalid pool class type specified: null");
    else if (!List.class.isAssignableFrom(type))
      throw new IllegalArgumentException("Invalid pool class type specified: " + type.getName() + " (must implement java.util.List)");
    try
    {
      free = (List<TimeWrapper<T>>)type.newInstance();
      used = (List<T>)type.newInstance();
    }
    catch (InstantiationException | IllegalAccessException ex)
    {
      throw new RuntimeException("Unable to instantiate pool class type: " + type.getName());
    }
    if (name == null || name.equals(""))
      this.name = "unknown" + unnamedCount++;
    else
      this.name = name;
    log = LoggerFactory.getLogger(getClass().getName() + "." + name);
    // Set pooling parameters.
    // This starts cleaner thread too, which is potentially dangerous in a
    // constructor, so cleaner must be responsible and not change state yet.
    setParameters(minPool, maxPool, maxSize, idleTimeout);
  }

  /**
   * Creates new object pool (with {@code minPool=0}).
   * @param name pool name
   * @param maxPool maximum number of pooled objects, or 0 for none
   * @param maxSize maximum number of possible objects, or 0 for no limit
   * @param idleTimeout idle timeout for pooled objects, or 0 for no timeout
   */
  protected BasePool(String name, int maxPool, int maxSize, long idleTimeout)
  {
    this(name, 0, maxPool, maxSize, idleTimeout);
  }

  /**
   * Registers a shutdown hook for this ConnectionPoolManager instance
   * to ensure it is released if the JVM exits.
   */
  public synchronized void registerShutdownHook()
  {
    if (shutdownHook != null)
      return;
    try
    {
      shutdownHook = new Releaser(this);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    catch (IllegalArgumentException iax)
    {
      System.err.println("Shutdown-hook not registered (unsupported) for pool " + this);
    }
    catch (Exception ex)
    {
      System.err.println("Error registering shutdown-hook for pool " + this);
      ex.printStackTrace();
    }
  }

  /**
   * Unregisters a registered shutdown hook for this ConnectionPoolManager instance.
   */
  public synchronized void removeShutdownHook()
  {
    if (shutdownHook != null)
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    shutdownHook = null;
  }

  /** Returns a descriptive string for this pool instance. */
  @Override
  public synchronized String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getName());
    sb.append(getParametersString());
    return sb.toString();
  }

  /**
   * Returns a summary string of the pool's parameters.
   * @return A summary string of the pool's parameters
   */
  public String getParametersString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("name=");
    sb.append(getName());
    sb.append(",minPool=");
    sb.append(getMinPool());
    sb.append(",maxPool=");
    sb.append(getMaxPool());
    sb.append(",maxSize=");
    sb.append(getMaxSize());
    sb.append(",idleTimeout=");
    sb.append(getIdleTimeout());
    sb.append("]");
    return sb.toString();
  }

  /**
   * Initializes the pool with the default (i.e. minpool) number of items.
   * This spawns a new thread to create them in the background, but only if
   * there is currently no other initialization thread running.
   * The most common use of this method is immediately after creation of a
   * pool, to ensure it starts to populate with minPool items.
   */
  public final synchronized void init()
  {
    if (initer != null)
      return;
    int dif = minPool - getSize();
    if (dif > 0)
      init(minPool);
  }

  /**
   * Asynchronously initializes up to the specified number of items in the pool.
   * This spawns a new thread to create them in the background.
   * Note that the number of items specified for initialization is the final
   * number of free items required in the pool, not the number to initialize.
   * If free items already exist in the pool, these are included in the count.
   * <p>This method is somewhat redundant since the introduction
   * of the {@code minPool} parameter, which should be used preferentially where
   * applicable. It can sometimes be useful to create initial pooled items
   * for a pool with minPool=0.</p>
   * @param num number of objects to initialize
   */
  public final synchronized void init(int num)
  {
    if (num == 0)
      return;
    // Validate the number of items requested.
    if (num < 0)
      throw new IllegalArgumentException("Invalid number of items specified for initialization: " + num);
    else if (idleTimeout == 0 && maxSize == 0 && num > maxPool)
      throw new IllegalArgumentException("Invalid number of items specified for initialization: " + num + " (max " + getMaxPool() + ")");
    else if (maxSize > 0 && num > maxSize)
      throw new IllegalArgumentException("Invalid number of items specified for initialization: " + num + " (max " + getMaxSize() + ")");

    if (initer != null)
    {
      initer.halt();
      try { initer.join(); }
      catch (InterruptedException ix) { log_warn(ix.getMessage(), ix); }
    }
    initer = new InitThread(this, num);
    initer.start();
  }

  /**
   * Checks out an item from the pool.
   * If no free item is available, a new item is created unless the maximum
   * number of items has been reached. If a free item is not valid it is
   * removed from the pool and another is retrieved.
   * @return item from the pool, or {@code null} if nothing available
   * @throws Exception if there is an error creating a new object
   */
  public final synchronized T checkOut() throws Exception
  {
    if (released)
      throw new IllegalStateException("Pool no longer valid for use");
    int preTotal = used.size() + free.size();

    TimeWrapper<T> tw = null;
    T o = null;
    if (free.size() > 0)
    {
      // Get an object from the free list.
      switch(selection)
      {
        case SELECT_FIFO:
          tw = free.remove(0);
          break;
        case SELECT_RANDOM:
          tw = free.remove(randGen.nextInt(free.size()));
          break;
        case SELECT_LIFO:
        default:
          tw = free.remove(free.size() - 1);
      }
      o = tw.getObject();
      boolean valid = isValid(o);
      while (!valid && free.size() > 0)
      {
        destroyObject(o);
        log_info("Removed invalid item from pool");
        firePoolEvent(ObjectPoolEvent.Type.VALIDATION_ERROR);
        switch(selection)
        {
          case SELECT_FIFO:
            tw = free.remove(0);
            break;
          case SELECT_RANDOM:
            tw = free.remove(randGen.nextInt(free.size()));
            break;
          case SELECT_LIFO:
          default:
            tw = free.remove(free.size() - 1);
        }
        o = tw.getObject();
        valid = isValid(o);
      }
      if (free.isEmpty() && !valid)
        o = null;
    }
    boolean hit = (o != null);

    // If no free items and can create more...create new item.
    if (o == null)
    {
      if (maxSize > 0 && used.size() == maxSize)
        firePoolEvent(ObjectPoolEvent.Type.MAX_SIZE_LIMIT_ERROR);
      else if (maxSize == 0 || used.size() < maxSize)
      {
        o = create();
        if (!isValid(o))
        {
          firePoolEvent(ObjectPoolEvent.Type.VALIDATION_ERROR);
          throw new RuntimeException("Unable to create a valid item");
        }
      }
    }

    // If an item has been obtained/created, add it to used items collection.
    if (o != null)
    {
      used.add(o);
      requests++;
      if (hit)
        hits++;
      firePoolEvent(ObjectPoolEvent.Type.CHECKOUT);
      // Check for limit reaching so events can be fired.
      // (Events only fired on increase of pool numbers).
      int postTotal = used.size() + free.size();
      if (postTotal == maxPool && postTotal > preTotal)
        firePoolEvent(ObjectPoolEvent.Type.MAX_POOL_LIMIT_REACHED);
      else if (postTotal == maxPool + 1 && postTotal > preTotal)
        firePoolEvent(ObjectPoolEvent.Type.MAX_POOL_LIMIT_EXCEEDED);
      if (postTotal == maxSize && postTotal > preTotal)
        firePoolEvent(ObjectPoolEvent.Type.MAX_SIZE_LIMIT_REACHED);
    }
    if (log.isDebugEnabled())
    {
      String ratio = used.size() + "/" + (used.size() + free.size());
      String hitRate = " (HitRate=" + (getPoolHitRate() * 100f) + "%)";
      log_debug("Checkout - " + ratio + hitRate + (o == null ? " - null returned" : ""));
    }
    return o;
  }

  /**
   * Checks out an item from the pool.
   * If there is no pooled item available and the maximum number
   * possible has not been reached, another is created.
   * If a free item is detected as being invalid it is removed
   * from the pool and the another is retrieved.
   * If an item is not available and the maximum number possible
   * has been reached, the method waits for the timeout period
   * for one to become available by being checked in.
   * @param timeout timeout value in milliseconds
   * @return item from the pool, or {@code null} if nothing available within timeout period
   * @throws Exception if there is an error creating a new object
   */
  public final synchronized T checkOut(long timeout) throws Exception
  {
    long time = System.currentTimeMillis();
    T o = checkOut();
    while (o == null && (System.currentTimeMillis() - time < timeout))
    {
      try
      {
        log_debug("No pooled items spare...waiting for up to " + timeout + "ms");
        wait(timeout);  // Wait to be notified of available item, or timeout.
        o = checkOut();  // Try again, returning null if timeout.
      }
      catch (InterruptedException e)
      {
        log_warn("Checkout interrupted", e);
      }
    }
    return o;
  }

  /**
   * Checks an object into the pool, and notifies other threads that may be
   * waiting for one to become available.
   * @param o object to check in
   */
  public final void checkIn(T o)
  {
    if (o == null)
    {
      log_info("Attempt to return null item");
      return;
    }

    synchronized(this)
    {
      firePoolEvent(ObjectPoolEvent.Type.CHECKIN);

      // Check if item is from this pool.
      if (!used.remove(o))
      {
        log_warn("Attempt to return item not belonging to pool");
        throw new IllegalArgumentException("Attempt to return item not belonging to pool " + name);
      }

      // Determine whether to recycle or destroy the object.
      // This is the primary deterministic logic for the pooling strategy.
      // Checked-in item is non-recyclable if either:
      //     1) Max items are limited & #extant items >= maxPool
      // or  2) Max items   unlimited & #free   items >= maxPool
      boolean nonRecyclable = (maxSize > 0 && getSize() >= maxPool) ||
                              (maxSize == 0 && getFreeCount() >= maxPool);
      if (o.isDirty() || nonRecyclable)
      {
        destroyObject(o);
        log_debug("Checkin* - " + used.size() + "/" + (used.size() + free.size()));
      }
      else
      {
        try
        {
          // Recycle object for next use.
          o.recycle();
          // Add object to free list.
          free.add(new TimeWrapper<>(o, idleTimeout));
          log_debug("Checkin  - " + used.size() + "/" + (used.size()+free.size()));
          notifyAll();  // Notify waiting threads of available item.
        }
        catch (Exception e)
        {
          // If unable to recycle object, destroy it.
          destroyObject(o);
          log_info("Unable to recycle item - destroyed", e);
        }
      }
    }
  }

  /**
   * Releases all items from the pool, and shuts the pool down.
   * If any items are still checked-out, this method waits until all items have
   * been checked-in before returning.
   */
  public final void release()
  {
    release(-1);
  }

  /**
   * Returns whether the pool has been released (and can no longer be used).
   * @return true if pool has been released, false otherwise
   */
  public final boolean isReleased()
  {
    return released;
  }

  /**
   * Forcibly releases all items from the pool, and shuts the pool down.
   * If any items are still checked-out, this method forcibly destroys them
   * and then returns.
   * This method is being deprecated due to API change allowing forcible timeout
   * release via the {@link #release(long)} method. The equivalent behaviour
   * can be achieved using {@link #releaseImmediately()}.
   * @deprecated Use {@link #releaseImmediately()} instead
   */
  @Deprecated
  public final void releaseForcibly()
  {
    releaseImmediately();
  }

  /**
   * Immediately releases all items from the pool, and shuts the pool down.
   * If any items are still checked-out, this method forcibly destroys them
   * and then returns.
   */
  public final void releaseImmediately()
  {
    release(0);
  }

  /**
   * Releases all items from the pool, and shuts the pool down.
   * This method returns immediately; a background thread is created to perform the release.
   */
  public final void releaseAsync()
  {
    releaseAsync(-1);
  }

  /**
   * Releases all items from the pool, and shuts the pool down.
   * This method returns immediately; a background thread is created to perform the release.
   */
  public final void releaseAsync(final long timeout)
  {
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        release(timeout);
      }
    });
    t.start();
  }

  /**
   * Releases all items from the pool, and shuts the pool down, allowing
   * {@code timeout} milliseconds for the connections to be gracefully returned
   * to the pool before they are forcibly destroyed.
   * A negative timeout is equivalent to no timeout, and the method will wait
   * for items to be checked in before destruction. If timeout &gt;= 0 then
   * items will be forcibly destroyed after the specified time has elapsed.
   * @param timeout timeout after which to forcibly destroy items (-1 for no timeout)
   */
  public final void release(long timeout)
  {
    if (released)
      return;
    long startTime = System.currentTimeMillis();
    boolean hasTimeout = (timeout >= 0);
    // Set released flag to prevent check-out of new items.
    released = true;
    // Allow sub-class to clean up.
    preRelease();

    synchronized(this)
    {
      // Destroy init thread.
      if (initer != null)
      {
        initer.halt();
        initer = null;
      }
      // Destroy cleaner thread.
      if (cleaner != null)
      {
        cleaner.halt();
        cleaner = null;
      }

      int releasedCount = 0, failedCount = 0;
      // Destroy all currently free items.
      for (TimeWrapper<T> tw : free)
      {
        T o = tw.getObject();
        try
        {
          destroy(o);
          releasedCount++;
        }
        catch (Exception ex)
        {
          failedCount++;
          log_warn("Unable to release item in pool", ex);
        }
      }
      free.clear();

      // Destroy all items still in use.
      if (log.isDebugEnabled() && !used.isEmpty())
        log_debug("Waiting for used items to be checked-in...");
      long dif = System.currentTimeMillis() - startTime;
      while (!used.isEmpty() && hasTimeout && dif < timeout)
      {
        try
        {
          wait(timeout - dif);
        }
        catch (InterruptedException ix)
        {
          log_warn(ix.getMessage(), ix);
        }
        dif = System.currentTimeMillis() - startTime;
      }
      // If timeout expired, forcibly destroy items.
      if (!used.isEmpty() && hasTimeout && dif > timeout)
      {
        for (T o : used)
        {
          try
          {
            destroy(o);
            releasedCount++;
          }
          catch (Exception ex)
          {
            failedCount++;
            log_warn("Unable to release item in pool", ex);
          }
        }
        used.clear();
      }

      // Destroy log reference.
      if (log.isDebugEnabled())
      {
        String s = "Released " + releasedCount + (releasedCount != 1 ? " items" : " item");
        if (failedCount > 0)
          s += " (failed to release " + failedCount + (failedCount != 1 ? " items)" : " item)");
        log_debug(s);
      }
    }
    // Fire released event, synchronously to ensure listeners receive it
    // before the event-dispatcher is shutdown.
    firePoolReleasedEvent();

    synchronized(this)
    {
      // Close custom log if open.
      if (logUtil != null)
        logUtil.close();

      // Destroy event dispatch thread.
      listeners.clear();
      try
      {
        if (eventDispatcher != null)
        {
          eventDispatcher.halt();
          if (hasTimeout)
          {
            long wait = startTime + timeout - System.currentTimeMillis();
            if (wait > 0)
              eventDispatcher.join(wait);
            else
              eventDispatcher.join();
          }
          else
            eventDispatcher.join();
        }
      }
      catch (InterruptedException ix)
      {
        log_warn("Interrupted during halting of event dispatch thread", ix);
      }
      eventDispatcher = null;
    }
    // Allow sub-class to clean up.
    postRelease();
    shutdownHook = null;
  }

  /**
   * Method to give a sub-class the opportunity to cleanup resources before
   * the pool is officially released. This method is called as the first thing
   * before the remaining {@code ObjectPool} release implementation.
   */
  protected void preRelease()
  {
  }

  /**
   * Method to give a sub-class the opportunity to cleanup resources after
   * the pool is officially released. This method is called as the last thing
   * after the main {@code ObjectPool} release implementation.
   * Be aware that the event-dispatch thread, cleaner thread, any init threads,
   * and the custom log have all been terminated before this method is called.
   */
  protected void postRelease()
  {
  }

  /**
   * Object creation method.
   * This method is called when a new item needs to be created following a call
   * to one of the check-out methods.
   * @return A new instance of the pooled type
   * @throws Exception if unable to create the item
   */
  protected abstract T create() throws Exception;

  /**
   * Object validation method.
   * This method is called when checking-out an item to see if it is valid for use.
   * When overridden by the sub-class it is recommended that this method perform
   * suitable checks to ensure the object can be used without problems.
   * @param o object to check for validity
   * @return true if o is valid, false otherwise
   */
  protected abstract boolean isValid(final T o);

  /**
   * Object destruction method.
   * This method is called when an object needs to be destroyed due to pool
   * pruning/cleaning, or final release of the pool.
   * @param o object to destroy
   */
  protected abstract void destroy(final T o);

  /**
   * Destroys the given object (asynchronously if necessary).
   */
  private void destroyObject(final T o)
  {
    if (o == null)
      return;
    if (asyncDestroy)
    {
      Thread t = new Thread(new Runnable()
      {
        @Override
        public void run()
        {
          destroy(o);
        }
      });
      t.start();
    }
    else
      destroy(o);
  }

  /**
   * Determines whether to perform asynchronous object destruction.
   * If set to true then each time an object is destroyed (invalid object
   * during pool operation, or when the pool is finally released) the operation
   * is done in a separate thread, allowing the method to return immediately.
   * This can be useful when calling the destroy method on an object takes a
   * long time to complete.
   * @param b whether to enable asynchronous object destruction
   */
  public final void setAsyncDestroy(boolean b)
  {
    asyncDestroy = b;
  }

  /**
   * Returns whether asynchronous object destruction is enabled.
   * (Default: false)
   * @return true if asynchronous object destruction is enabled, false otherwise
   */
  public final boolean isAsyncDestroy()
  {
    return asyncDestroy;
  }

  /**
   * Sets the custom log stream.
   * In addition to regular logging, this enables a specific {@code PrintWriter}
   * to receive log information.
   * @param writer {@code PrintWriter} to which to write log entries
   */
  public void setLog(PrintWriter writer)
  {
    if (logUtil == null)
      logUtil = new LogUtil();
    logUtil.setLog(writer);
  }

  /**
   * Returns the custom {@code LogUtil} instance being used,
   * or null if it doesn't exist.
   * @return the custom {@code LogUtil} instance being used,
   * or null if it doesn't exist
   */
  public LogUtil getCustomLogger()
  {
    return logUtil;
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   */
  protected void log_error(String s)
  {
    String msg = name + ": " + s;
    log.error(msg);
    if (logUtil != null)
      logUtil.log(msg);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   * @param throwable {@code Throwable} instance to log
   */
  protected void log_error(String s, Throwable throwable)
  {
    String msg = name + ": " + s;
    log.error(msg, throwable);
    if (logUtil != null)
      logUtil.log(msg, throwable);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   */
  protected void log_warn(String s)
  {
    String msg = name + ": " + s;
    log.warn(msg);
    if (logUtil != null)
      logUtil.log(msg);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   * @param throwable {@code Throwable} instance to log
   */
  protected void log_warn(String s, Throwable throwable)
  {
    String msg = name + ": " + s;
    log.warn(msg, throwable);
    if (logUtil != null)
      logUtil.log(msg, throwable);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   */
  protected void log_info(String s)
  {
    String msg = name + ": " + s;
    log.info(msg);
    if (logUtil != null)
      logUtil.log(msg);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   * @param throwable {@code Throwable} instance to log
   */
  protected void log_info(String s, Throwable throwable)
  {
    String msg = name + ": " + s;
    log.info(msg, throwable);
    if (logUtil != null)
      logUtil.log(msg, throwable);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   */
  protected void log_debug(String s)
  {
    String msg = name + ": " + s;
    log.debug(msg);
    if (logUtil != null)
      logUtil.debug(msg);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   * @param throwable {@code Throwable} instance to log
   */
  protected void log_debug(String s, Throwable throwable)
  {
    String msg = name + ": " + s;
    log.debug(msg, throwable);
    if (logUtil != null)
      logUtil.debug(msg, throwable);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   */
  protected void log_trace(String s)
  {
    log.trace(name + ": " + s);
  }

  /**
   * Logging relay method (to prefix pool name).
   * @param s string to log
   * @param throwable {@code Throwable} instance to log
   */
  protected void log_trace(String s, Throwable throwable)
  {
    log.trace(name + ": " + s, throwable);
  }

  /**
   * Returns the pool name.
   * @return The pool name
   */
  public final String getName()
  {
    return this.name;
  }

  /**
   * Returns the minimum number of items that should be kept pooled.
   * @return The minimum number of items that should be kept pooled
   */
  public synchronized final int getMinPool()
  {
    return minPool;
  }

  /**
   * Returns the maximum number of items that can be pooled.
   * @return The maximum number of items that can be pooled
   */
  public synchronized final int getMaxPool()
  {
    return maxPool;
  }

  /**
   * Returns the maximum number of items that can be created.
   * @return The maximum number of items that can be created
   */
  public synchronized final int getMaxSize()
  {
    return maxSize;
  }

  /**
   * Returns the idle timeout for unused items in the pool (in milliseconds).
   * @return The idle timeout for unused items in the pool (in milliseconds)
   */
  protected synchronized long getIdleTimeoutUnadjusted()
  {
    return idleTimeout;
  }

  /**
   * Returns the idle timeout for unused items in the pool.
   * The returned value is scaled using the pool-specific time measurement,
   * which is determined by the{@code getIdleTimeoutMultiplier()} method,
   * and may be overridden by sub-classes to provide idleTimeout scaling
   * (default of 1 for milliseconds, e.g. 1000 changes to seconds).
   * @return {@link #getIdleTimeoutUnadjusted()}/{@link #getIdleTimeoutMultiplier()}
   */
  public synchronized long getIdleTimeout()
  {
    return (long)(idleTimeout / getIdleTimeoutMultiplier());
  }

  /**
   * Returns the multiplier for adjusting the idle timeout unit.
   * This is a convenience method for sub-classes to override if they want
   * to adjust the time measurement for the idleTimeout parameter.
   * By default it returns 1.0 which denotes milliseconds.
   * A sub-class wanting to operate with seconds would override it to
   * return 1000, or to operate in minutes, 60000.
   * @return The multiplier for adjusting the idle timeout unit
   */
  protected float getIdleTimeoutMultiplier()
  {
    return 1f;
  }

  /**
   * Specifies the minimum time interval between cleaning attempts of
   * the {@code Cleaner} thread (milliseconds).
   * @return The minimum time interval between cleaning attempts of
   * the {@code Cleaner} thread (milliseconds)
   */
  protected long getMinimumCleaningInterval()
  {
    return 200L;
  }

  /**
   * Specifies the maximum time interval between cleaning attempts of
   * the {@code Cleaner} thread (milliseconds).
   * @return the maximum time interval between cleaning attempts of
   * the {@code Cleaner} thread (milliseconds)
   */
  protected long getMaximumCleaningInterval()
  {
    return 5000L;
  }

  /**
   * Sets the pooling parameters (excluding {@code minPool}).
   * Any items currently in the pool will remain, subject to the new parameters.
   * (The hit rate counters are reset when this method is called.)
   * @param maxPool maximum number of items to be kept in pool
   * @param maxSize maximum number of items to be created
   * @param idleTimeout idle timeout for unused items (0 = no timeout)
   */
  public final void setParameters(int maxPool, int maxSize, long idleTimeout)
  {
    this.setParameters(this.minPool, maxPool, maxSize, idleTimeout);
  }

  /**
   * Sets the pooling parameters.
   * Any items currently in the pool will remain, subject to the new parameters.
   * (The hit rate counters are reset when this method is called.)
   * @param minPool minimum number of items to be kept in pool
   * @param maxPool maximum number of items to be kept in pool
   * @param maxSize maximum number of items to be created
   * @param idleTimeout idle timeout for unused items (0 = no timeout)
   */
  public final synchronized void setParameters(int minPool, int maxPool, int maxSize, long idleTimeout)
  {
    if (minPool < 0 || maxPool < 0 || maxSize < 0 || idleTimeout < 0)
      throw new IllegalArgumentException("Negative values not accepted as pool parameters");
    if (maxPool < minPool)
      throw new IllegalArgumentException("Invalid minPool/maxPool values: " + minPool + "/" + maxPool);
    if (maxSize > 0 && maxSize < maxPool)
      throw new IllegalArgumentException("Invalid maxPool/maxSize values: " + maxPool + "/" + maxSize);

    if (cleaner != null)
    {
      cleaner.halt();
      cleaner = null;
    }

    // Copy parameter values.
    this.minPool = minPool;
    this.maxPool = maxPool;
    this.maxSize = maxSize;
    this.idleTimeout = (long)(idleTimeout * getIdleTimeoutMultiplier());
    resetHitCounter();

    // Update pooled items to use new idle timeout.
    for (TimeWrapper<T> tw : free)
      tw.setLiveTime(this.idleTimeout);
    // Creates cleaner thread with check interval between 200ms and 5 seconds.
    // Too often and cleaner will use too much processor resource.
    // Too seldom and cleaner will fail to clean up fast enough.
    if (this.idleTimeout > 0)
    {
      long min = getMinimumCleaningInterval();
      long max = getMaximumCleaningInterval();
      if (min < 0 || max < 0 || min >= max)
        throw new IllegalStateException("Invalid min/max cleaner interval specified");
      long iVal = Math.max(min, Math.min(max, this.idleTimeout / 5));
      (cleaner = new Cleaner(this, iVal)).start();
    }

    if (log.isDebugEnabled())
    {
      StringBuilder sb = new StringBuilder();
      sb.append("minpool=");
      sb.append(getMinPool());
      sb.append(",maxpool=");
      sb.append(getMaxPool());
      sb.append(",maxsize=");
      sb.append(getMaxSize());
      sb.append(",idleTimeout=");
      long it = getIdleTimeout();
      if (it == 0)
        sb.append("none");
      else
      {
        sb.append(it);
        sb.append(",cleanInterval=");
        sb.append(cleaner.interval);
      }
      log_debug("Parameters changed (" + sb.toString() + ")");
    }
    firePoolEvent(ObjectPoolEvent.Type.PARAMETERS_CHANGED);
  }

  /**
   * Returns the total number of objects held (available and checked-out).
   * @return The total number of objects held (available and checked-out)
   */
  public final synchronized int getSize()
  {
    return free.size() + used.size();
  }

  /**
   * Returns the number of items that are currently checked-out.
   * @return The number of items that are currently checked-out
   */
  public final synchronized int getCheckedOut()
  {
    return used.size();
  }

  /**
   * Returns the number of items held in the pool that are free to be checked-out.
   * @return The number of items held in the pool that are free to be checked-out
   */
  public final synchronized int getFreeCount()
  {
    return free.size();
  }

  /**
   * Returns the number of check-out requests that have been made to the pool
   * since either its creation or the last time the {@link #resetHitCounter()}
   * method was called.
   * @return The number of check-out requests that have been made to the pool
   * since either its creation or the last time the {@link #resetHitCounter()}
   * method was called
   */
  public final synchronized long getRequestCount()
  {
    return requests;
  }

  /**
   * Returns hit rate of the pool (between 0 and 1).
   * The hit rate is the proportion of requests for an item which result
   * in the return of an item which is in the pool, rather than which
   * results in the creation of a new item.
   * @return Hit rate of the pool (between 0 and 1)
   */
  public final synchronized float getPoolHitRate()
  {
    return (requests == 0) ? 0f : ((float)hits / requests);
  }

  /**
   * Returns miss rate of the pool (between 0 and 1).
   * The miss rate is the proportion of requests for an item for which no
   * pooled item can be retrieved.
   * @return Miss rate of the pool (between 0 and 1)
   */
  public final synchronized float getPoolMissRate()
  {
    return (requests == 0) ? 0f : ((float)(requests - hits) / requests);
  }

  /**
   * Resets the counters for determining the pool's hit/miss rates.
   */
  public final synchronized void resetHitCounter()
  {
    requests = hits = 0;
  }

  /**
   * Sets the pool selection strategy.
   * @param selection selection strategy
   */
  public final synchronized void setSelectionStrategy(Strategy selection)
  {
    if (selection == null)
    {
      log.info("Cannot set null pool selection strategy; using default: LIFO");
      this.selection = Strategy.SELECT_LIFO;
    }
    else
    {
      this.selection = selection;
    }
    if (this.selection == Strategy.SELECT_RANDOM && this.randGen == null)
      randGen = new Random();
  }

  /**
   * Returns the class to use for the pool collection.
   * This can be over-ridden by a sub-class to provide a different List
   * type for the pool, which may give performance benefits in certain situations.
   * Only instances of {@link List} collections should be used.
   * (Default: {@code java.util.ArrayList} class)
   * For reference, pool items are checked-in to the tail end of the list.
   * @return The class to use for the pool collection
   */
  protected Class<? extends List> getPoolClass()
  {
    return ArrayList.class;
  }

  /**
   * Flushes the pool of all currently available items, emptying the pool.
   */
  public final void flush()
  {
    int count = 0;
    synchronized(this)
    {
      TimeWrapper<T> tw = null;
      for (Iterator<TimeWrapper<T>> iter = free.iterator(); iter.hasNext();)
      {
        tw = iter.next();
        iter.remove();
        destroyObject(tw.getObject());
        count++;
      }
      if (count > 0)
        log_debug("Flushed all spare items from pool");
      // Notify event listeners.
      firePoolEvent(ObjectPoolEvent.Type.POOL_FLUSHED);
      // Create new pooled items as required.
      if (idleTimeout == 0 && minPool > 0)
        init();
    }
  }

  /**
   * Purges expired objects from the pool.
   * This method is called by the cleaner thread to purge expired items.
   * @return false if pool is empty after purging (no further purge required until items added), true otherwise
   */
  final synchronized boolean purge()
  {
    log_trace("Checking for expired items");
    if (free.isEmpty())
      return false;
    int count = 0;
    TimeWrapper<T> tw = null;
    for (Iterator<TimeWrapper<T>> iter = free.iterator(); iter.hasNext();)
    {
      tw = iter.next();
      if (tw.isExpired())
      {
        iter.remove();
        destroyObject(tw.getObject());
        count++;
      }
    }
    return free.size() > 0 || count > 0;
  }

  /**
   * Indicates whether some other object is &quot;equal to&quot; this one.
   * This implementation performs checks on the following fields:
   * {name, minPool, maxPool, maxSize, idleTimeout}.
   * @param o object to check for equality against this instance
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized boolean equals(Object o)
  {
    if (o == null)
      return false;
    if (getClass() != o.getClass())
      return false;
    final BasePool<T> op = (BasePool<T>)o;
    synchronized (op)
    {
      if ((this.name == null) ? (op.name != null) : !this.name.equals(op.name))
        return false;
      if (this.minPool != op.minPool)
        return false;
      if (this.maxPool != op.maxPool)
        return false;
      if (this.maxSize != op.maxSize)
        return false;
      if (this.idleTimeout != op.idleTimeout)
        return false;
    }
    return true;
  }

  /**
   * Returns a hash code value for the object.
   * This implementation hashes on the pool name.
   */
  @Override
  public synchronized int hashCode()
  {
    int hash = 7;
    hash = 29 * hash + (this.name != null ? this.name.hashCode() : 0);
    return hash;
  }

  /**
   * Compares this object with the specified object for order.
   * This implementation is consistent with the implementation of
   * {@link #equals(Object)}, comparing the same fields.
   * @param pool pool to compare against this instance
   */
  @Override
  public synchronized int compareTo(BasePool<T> pool)
  {
    if (pool == null)
      throw new NullPointerException("Invalid pool specified: null");
    int i = this.getName().compareTo(pool.getName());
    if (i != 0)
      return i;
    i = Integer.valueOf(minPool).compareTo(pool.getMinPool());
    if (i != 0)
      return i;
    i = Integer.valueOf(maxPool).compareTo(pool.getMaxPool());
    if (i != 0)
      return i;
    i = Integer.valueOf(maxSize).compareTo(pool.getMaxSize());
    if (i != 0)
      return i;
    i = Long.valueOf(idleTimeout).compareTo(pool.getIdleTimeoutUnadjusted());
    return i;
  }

  //************************
  // Event-handling methods
  //************************

  /**
   * Adds an listener to the event notification list.
   * @param x listener to add
   */
  public final void addObjectPoolListener(ObjectPoolListener<T> x)
  {
    listeners.add(x);
  }

  /**
   * Removes a listener from the event notification list.
   * @param x listener to remove
   */
  public final void removeObjectPoolListener(ObjectPoolListener<T> x)
  {
    listeners.remove(x);
  }

  /**
   * Fires an ObjectPoolEvent to all listeners.
   * 'type' should be one of ObjectPoolEvent types.
   */
  private void firePoolEvent(ObjectPoolEvent.Type type)
  {
    if (listeners.isEmpty())
      return;
    ObjectPoolEvent<T> poolEvent = null;
    // Setup event dispatch thread if necessary.
    if (eventDispatcher == null)
    {
      eventDispatcher = new EventDispatcher<>(listeners, new Notifier<T>());
      eventDispatcher.start();
    }
    // Dispatch event.
    synchronized (this)
    {
      poolEvent = new ObjectPoolEvent<>(this, type);
      poolEvent.setMinPool(getMinPool());
      poolEvent.setMaxPool(getMaxPool());
      poolEvent.setMaxSize(getMaxSize());
      poolEvent.setIdleTimeout(getIdleTimeout());
      poolEvent.setCheckOut(getCheckedOut());
      poolEvent.setFreeCount(getFreeCount());
      poolEvent.setSize(getSize());
      poolEvent.setPoolHitRate(getPoolHitRate());
    }
    eventDispatcher.dispatchEvent(poolEvent);
  }

  /**
   * Fires a ObjectPoolEvent.POOL_RELEASED event to all listeners.
   * This method performs the listener notification synchronously to ensure
   * all listeners receive the event before the event-dispatch thread is
   * shutdown.
   */
  private void firePoolReleasedEvent()
  {
    if (listeners.isEmpty())
      return;
    ObjectPoolEvent<T> poolEvent = null;
    synchronized (this)
    {
      poolEvent = new ObjectPoolEvent<>(this, ObjectPoolEvent.Type.POOL_RELEASED);
      poolEvent.setMinPool(getMinPool());
      poolEvent.setMaxPool(getMaxPool());
      poolEvent.setMaxSize(getMaxSize());
      poolEvent.setIdleTimeout(getIdleTimeout());
      poolEvent.setCheckOut(getCheckedOut());
      poolEvent.setFreeCount(getFreeCount());
      poolEvent.setSize(getSize());
      poolEvent.setPoolHitRate(getPoolHitRate());
    }
    // No copy of listeners needs to be taken as the collection is thread-safe.
    for (ObjectPoolListener<T> listener : listeners)
    {
      try
      {
        listener.poolReleased(poolEvent);
      }
      catch (RuntimeException rx)
      {
        log_warn("Exception thrown by listener on pool release", rx);
      }
    }
  }


  /**
   * Thread to perform clean-up of expired objects in pool.
   * Each time nothing is cleaned because the pool is empty the cleaner waits
   * until an item is returned, when it is woken up and starts cleaning again.
   */
  private final class Cleaner extends Thread
  {
    /** Reference to the pool instance to be cleaned. */
    private final BasePool<T> pool;
    /** Cleaning period/interval (milliseconds). */
    private long interval;
    /** Flag determining whether the cleaner has been stopped. */
    private volatile boolean stopped;

    private Cleaner(BasePool<T> pool, long interval)
    {
      assert pool != null && interval > 0;
      this.setName("Cleaner-thread-" + Integer.toString(cleanerCount++));
      this.pool = pool;
      this.interval = interval;
      this.setDaemon(true);
    }

    @Override
    public void start()
    {
      stopped = false;
      super.start();
    }

    /**
     * Halts this thread (use instead of {@link #stop()}).
     */
    public void halt()
    {
      stopped = true;
      this.interrupt();
    }

    /**
     * Handles the expiry of old objects.
     */
    @Override
    public void run()
    {
      // Initialize test condition based on current items.
      // Cleaner MUST be configured to NOT call init() during initial creation
      // of a pool, otherwise item creation could be requested before
      // sub-class constructor has completed.
      boolean purged = getSize() > 0;
      // Loop while the cleaner is valid.
      while (!stopped)
      {
        synchronized(pool)
        {
          if (pool.cleaner != Thread.currentThread())
            stopped = true;
          else
          {
            // If nothing purged & nothing to monitor, wait for notification.
            if (!purged && pool.getSize() == 0)
            {
              try { pool.wait(); }
              catch (InterruptedException ix) {}  // Ignore interruptions.
            }
            if (!stopped)
            {
              // Purge expired items from pool.
              purged = pool.purge();
              // Repopulate pool as necessary.
              pool.init();
            }
          }
        }
        if (!stopped)
        {
          try
          {
            sleep(interval);
          }
          catch (InterruptedException ix)
          {
            // No need to catch, as just loops around again.
          }
        }
      }
    }
  }


  /**
   * Thread to initialize items in pool.
   * This thread simply performs a check-out/in of new items up to the specified
   * number to ensure the pool is populated.
   * Note that the number of items specified for initialization is the final
   * number of free items required in the pool, not the number to initialize.
   * If free items already exist in the pool, these are included in the count.
   */
  private final class InitThread extends Thread
  {
    /** Reference to the pool instance to be cleaned. */
    private final BasePool<T> pool;
    /** Number of items to initialize. */
    private final int num;
    /** Flag determining whether the init thread has been stopped. */
    private volatile boolean stopped = false;
    /** Flag determining whether the init thread has been completed working. */
    private volatile boolean done = false;

    private InitThread(BasePool<T> pool, int num)
    {
      assert pool != null;
      assert num >= 0 && (num <= pool.getMaxSize() || getMaxSize() == 0);
      this.pool = pool;
      // If items can expire, then allow initialization up to maxSize,
      // otherwise only allow up to maxPool.
      if (pool.getIdleTimeoutUnadjusted() > 0)
        this.num = Math.min(getMaxSize(), Math.max(num, 0));
      else
        this.num = Math.min(getMaxPool(), Math.max(num, 0));
      this.setDaemon(true);
    }

    /**
     * Halts this thread (use instead of {@link #stop()}).
     */
    public void halt()
    {
      stopped = true;
    }

    /**
     * Populates the pool with the given number of items.
     * If the pool already contains used items then they will be counted
     * towards the number created by this method.
     */
    @Override
    public void run()
    {
      int count = 0;

      while (!stopped && !done)
      {
        synchronized(pool)
        {
          if (pool.initer != Thread.currentThread())
          {
            stopped = true;
            continue;
          }
          if (count >= num || getFreeCount() >= num || (getMaxSize() > 0 && getSize() >= getMaxSize()))
            done = true;
          if (!stopped && !done)
          {
            try
            {
              T o = create();
              if (!isValid(o))
              {
                firePoolEvent(ObjectPoolEvent.Type.VALIDATION_ERROR);
                throw new RuntimeException("Unable to create a valid item");
              }
              else
              {
                free.add(new TimeWrapper<>(o, pool.idleTimeout));
                pool.notifyAll();
                count++;
                log_debug("Initialized new item in pool");
              }
            }
            catch (Exception ex)
            {
              log_warn("Unable to initialize items in pool", ex);
              stopped = true;
            }
          }
        }
      }
      synchronized(pool)
      {
        if (!stopped && done)
        {
          log_debug("Initialized pool with " + count + (count != 1 ? " new items" : " new item"));
          firePoolEvent(ObjectPoolEvent.Type.INIT_COMPLETED);
        }
        if (pool.initer != Thread.currentThread())
          pool.initer = null;
      }
    }
  }

  /**
   * Utility class to release ObjectPool instances (used by shutdown-hook).
   */
  private static final class Releaser extends Thread
  {
    private final BasePool<?> instance;

    private Releaser(BasePool<?> pool)
    {
      instance = pool;
      setDaemon(true);
    }

    @Override
    public void run()
    {
      if (!instance.isReleased())
        instance.releaseForcibly();
    }
  }

  /**
   * {@link EventNotifier} implementation to notify event listeners of events.
   */
  private final class Notifier<T extends Reusable> implements EventNotifier<ObjectPoolListener<T>, ObjectPoolEvent<T>>
  {
    @Override
    public void notifyListener(ObjectPoolListener<T> opl, ObjectPoolEvent<T> evt)
    {
      try
      {
        switch (evt.getType())
        {
          case INIT_COMPLETED:
            opl.poolInitCompleted(evt);
            break;
          case CHECKOUT:
            opl.poolCheckOut(evt);
            break;
          case CHECKIN:
            opl.poolCheckIn(evt);
            break;
          case VALIDATION_ERROR:
            opl.validationError(evt);
            break;
          case MAX_POOL_LIMIT_REACHED:
            opl.maxPoolLimitReached(evt);
            break;
          case MAX_POOL_LIMIT_EXCEEDED:
            opl.maxPoolLimitExceeded(evt);
            break;
          case MAX_SIZE_LIMIT_REACHED:
            opl.maxSizeLimitReached(evt);
            break;
          case MAX_SIZE_LIMIT_ERROR:
            opl.maxSizeLimitError(evt);
            break;
          case PARAMETERS_CHANGED:
            opl.poolParametersChanged(evt);
            break;
          case POOL_FLUSHED:
            opl.poolFlushed(evt);
            break;
          case POOL_RELEASED:
            opl.poolReleased(evt);
            break;
          default:
        }
      }
      catch (RuntimeException rx)
      {
        log_warn("Exception raised by pool listener", rx);
      }
    }
  }
}
