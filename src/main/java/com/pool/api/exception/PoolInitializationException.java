/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pool.api.exception;

/**
 *
 */
public class PoolInitializationException extends RuntimeException {

    public PoolInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public PoolInitializationException(String message) {
        super(message);
    }
    
    
}
