/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.api;

import com.pool.api.exception.MaxPoolSizeReachedException;
import com.pool.api.exception.PoolInitializationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @param <T>
 */
@Slf4j
public abstract class PoolBase<T extends PoolItem> {

    public static PoolInitializer initer;
    private final Map<T, Long> locked, unlocked;
    /**
     * check connection expiration time
     */
    @Getter
    private final long expirationTime;
    @Getter
    private final Integer maxSize;
    @Getter
    private final Integer minSize;

    public PoolBase(Integer maxSize, Integer minSize, Long expirationTime) {
        this.expirationTime = expirationTime * 1000L; //comes in seconds
        this.locked = new HashMap<>();
        this.unlocked = new HashMap<>();
        this.maxSize = maxSize;
        this.minSize = minSize;
        validate();
    }

    public PoolBase() {
        expirationTime = 30000; //default time
        locked = new HashMap<>();
        unlocked = new HashMap<>();
        this.maxSize = 10;
        this.minSize = 5;
    }

    private void validate() {

        if (expirationTime <= 0
                || maxSize <= 0
                || minSize > maxSize) {
            throw new PoolInitializationException("Error validating pool configuration");
        }

    }

    protected abstract T create();

    public abstract boolean validate(T o);

    public abstract void expire(T o);

    public synchronized T checkOut() {
        if (locked.size() >= maxSize) {
            throw new MaxPoolSizeReachedException("Max pool size reached!");
        }
        final long now = System.currentTimeMillis();
        T t = null;
        if (unlocked.size() > 0) {
            for (Iterator<Map.Entry<T, Long>> it = unlocked.entrySet().iterator(); it.hasNext();) {
                t = it.next().getKey();
                if ((now - unlocked.get(t)) > expirationTime) {
                    // object has expired
                    unlocked.remove(t);
                    expire(t);
                    t = null;
                } else if (validate(t)) {
                    unlocked.remove(t);
                    locked.put(t, now);
                    if (t.usagesCount() > 0) {
                        log.warn("You are getting a recycled connection object."
                                + " Usages count: {0}", t.usagesCount());
                    }
                    t.increaseUsages();
                    return (t);
                } else {
                    // object failed validation
                    unlocked.remove(t);
                    expire(t);
                    t = null;
                }
            }

        }
        t = create();
        t.increaseUsages();
        locked.put(t, now);
        return (t);
    }

    public synchronized void checkIn(T t) {
        locked.remove(t);
        unlocked.put(t, System.currentTimeMillis());
    }

    public int getSize() {
        return unlocked.size() + locked.size();
    }

    public int getFreeCount() {
        return unlocked.size();
    }

}
