package com.dyc.xiaohashu.id.generator.core;

/**
 * Base runtime exception for ID generation failures.
 */
public class IdGeneratorException extends RuntimeException {

    public IdGeneratorException(String message) {
        super(message);
    }

    public IdGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
