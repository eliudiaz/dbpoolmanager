package com.pool.db;

/**
 *
 * @author eliud
 * @param <T>
 */
public class InitializerProcess<T> extends Thread {

    private final PoolBase<T> pool;

    private final int num;
    /**
     * Flag init thread has been stopped.
     */
    private volatile boolean stopped = false;
    /**
     * determining init thread has been completed working.
     */
    private volatile boolean done = false;

    public InitializerProcess(PoolBase<T> pool, int num) {
        assert pool != null;
        assert num >= 0 && (num <= pool.getMaxSize());
        this.pool = pool;
        this.num = pool.getMaxSize();
        this.setDaemon(true);
    }

    /**
     * Halts this thread (use instead of {@link #stop()}).
     */
    public void halt() {
        stopped = true;
    }

    /**
     * Populates the pool with the given number of items. If the pool already
     * contains used items then they will be counted towards the number created
     * by this method.
     */
    @Override
    public void run() {
        int count = 0;

        while (!stopped && !done) {
            synchronized (pool) {
                if (PoolBase.initer != Thread.currentThread()) {
                    stopped = true;
                    continue;
                }
                if (count >= num || pool.getFreeCount() >= num) {
                    done = true;
                }
                if (!stopped && !done) {
                    try {
                        T o = pool.create();
                        if (!pool.validate(o)) {
                            //firePoolEvent(ObjectPoolEvent.Type.VALIDATION_ERROR);
                            throw new RuntimeException("Unable to create a valid item");
                        } else {
                            pool.checkIn(o);
//                            free.add(new TimeWrapper<>(o, pool.idleTimeout));
//                            pool.notifyAll();
                            count++;
//                            log_debug("Initialized new item in pool");
                        }
                    } catch (Exception ex) {
//                        log_wasrn("Unable to initialize items in pool", ex);
                        stopped = true;
                    }
                }
            }
        }
        synchronized (pool) {
//            if (!stopped && done) {
//                log_debug("Initialized pool with " + count + (count != 1 ? " new items" : " new item"));
//                firePoolEvent(ObjectPoolEvent.Type.INIT_COMPLETED);
//            }
            if (pool.initer != Thread.currentThread()) {
                pool.initer = null;
            }
        }
    }
}
