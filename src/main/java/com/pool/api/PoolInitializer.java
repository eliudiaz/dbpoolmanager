package com.pool.api;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @param <T>
 */
@Slf4j
public class PoolInitializer<T extends PoolItem> extends Thread {

    private final PoolBase<T> pool;

    private final int targetCount;
    private volatile boolean stopped = false;
    private volatile boolean done = false;

    public PoolInitializer(PoolBase<T> pool, int num) {
        assert pool != null;
        assert num >= 0 && (num <= pool.getMaxSize());
        this.pool = pool;
        this.targetCount = num;
        this.setDaemon(true);
    }

    /**
     * Halts this thread (use instead of {@link #stop()}).
     */
    public void halt() {
        stopped = true;
    }

    /**
     * Add new items to current pool
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
                if (count >= targetCount || (pool.getMaxSize() > 0 && pool.getSize() >= pool.getMaxSize())) {
                    done = true;
                }
                if (!stopped && !done) {
                    try {
                        T o = pool.create();
                        if (!pool.validate(o)) {
                            throw new RuntimeException("Unable to create a valid item");
                        } else {
                            pool.checkIn(o);
                            count++;
                            log.debug("Initialized new item in pool");
                        }
                    } catch (Exception ex) {
                        log.warn("Unable to initialize items in pool", ex);
                        stopped = true;
                    }
                }
            }
        }
        synchronized (pool) {
            if (!stopped && done) {
                log.debug("Initialized pool with " + count + (count != 1 ? " new items" : " new item"));
//                firePoolEvent(ObjectPoolEvent.Type.INIT_COMPLETED);
            }
            if (pool.initer != Thread.currentThread()) {
                pool.initer = null;
            }
        }
    }
}
