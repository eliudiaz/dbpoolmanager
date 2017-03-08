/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.api;

/**
 *
 */
public interface PoolItem {

    public void increaseUsages();

    public int usagesCount();
    
    public PoolItem recycle();
}
