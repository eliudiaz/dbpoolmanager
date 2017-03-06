/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.db;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author eliud
 * @param <T>
 */
public abstract class PoolBase<T> {

    private final long expirationTime;
    private final Map<T, Long> locked, unlocked;
    private final Integer maxSize;
    private final Integer minSize;
    public static InitializerProcess initer;

    public PoolBase(Integer maxSize, Integer minSize) {
        expirationTime = 30000; //default time
        locked = new Hashtable<>();
        unlocked = new HashMap<>();
        this.maxSize = maxSize;
        this.minSize = minSize;
    }

    public PoolBase() {
        expirationTime = 30000; //default time
        locked = new Hashtable<>();
        unlocked = new HashMap<>();
        this.maxSize = 10;
        this.minSize = 5;
    }

    protected abstract T create();

    public abstract boolean validate(T o);

    public abstract void expire(T o);

    public synchronized T checkOut() {
        final long now = System.currentTimeMillis();
        T t;
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
                    return (t);
                } else {
                    // object failed validation
                    unlocked.remove(t);
                    expire(t);
                    t = null;
                }
            }
            t = create();

            locked.put(t, now);
            return (t);
        }

        return null;
    }

    public int getSize() {
        return unlocked.size() + locked.size();
    }

    public int getFreeCount() {
        return unlocked.size();
    }

    public synchronized void checkIn(T t) {
        locked.remove(t);
        unlocked.put(t, System.currentTimeMillis());
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public Integer getMinSize() {
        return minSize;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

}
