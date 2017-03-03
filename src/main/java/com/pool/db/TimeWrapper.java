
package com.pool.db;

/**
 * Time-tracking wrapper class for an object.
 * 
 * @param <E> class type of object to wrap
 * @see snaq.util.ObjectPool
 * @author Giles Winstanley
 */
public class TimeWrapper<E>
{
  /** Object to be held in this wrapper instance. */
  private final E obj;
  /** Time at which this object expires. */
  private long expiryTime = 0L;
  /** Last access time (updated by method call). */
  private long accessed;

  /**
   * Creates a new wrapped object.
   * @param obj object to be referenced
   * @param expiry object's idle time before death in milliseconds (0 - eternal)
   */
  public TimeWrapper(E obj, long expiry)
  {
    this.obj = obj;
    this.accessed = System.currentTimeMillis();
    if (expiry > 0)
      this.expiryTime = this.accessed + expiry;
  }

  /**
   * Returns the object referenced by this wrapper.
   * NOTE: this does not update the last access time, which must be done
   * explicitly with the {@link #updateAccessed()} method.
   * @return The object referenced by this wrapper
   */
  public E getObject()
  {
    return obj;
  }

  /**
   * Whether this item has expired.
   * @return true if item has expired, false otherwise
   */
  public synchronized boolean isExpired()
  {
    return expiryTime > 0 && System.currentTimeMillis() > expiryTime;
  }

  /**
   * Sets idle time allowed before this item expires.
   * @param expiryTime idle time before expiry (0 = eternal)
   */
  synchronized void setLiveTime(long expiry)
  {
    if (expiry < 0)
      throw new IllegalArgumentException("Invalid expiry time");
    else if (expiry > 0)
      this.expiryTime = System.currentTimeMillis() + expiry;
    else
      this.expiryTime = 0;
  }

  /**
   * Updates the time this object was last accessed.
   */
  synchronized void updateAccessed()
  {
    accessed = System.currentTimeMillis();
  }

  /**
   * Returns the time this object was last accessed.
   * NOTE: this does not update the last access time, which must be done
   * explicitly with the {@link #updateAccessed()} method.
   */
  synchronized long getAccessed()
  {
    return accessed;
  }
}
