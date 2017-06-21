package com.pool.api;

/**
 *
 */
public interface PoolItem {

    public void increaseUsages();

    public int usagesCount();

    public PoolItem recycle();
}
