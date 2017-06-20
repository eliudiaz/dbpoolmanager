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
