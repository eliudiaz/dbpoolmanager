package com.pool.api.exception;

/**
 *
 */
public class MaxPoolSizeReachedException extends RuntimeException {

    public MaxPoolSizeReachedException(String message) {
        super(message);
    }

}
