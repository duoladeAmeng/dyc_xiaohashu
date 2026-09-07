package com.dyc.xiaohashu.id.generator.core;

/**
 * Raised when an ID generator must refuse service to preserve uniqueness.
 */
public class GeneratorUnavailableException extends IdGeneratorException {

    public GeneratorUnavailableException(String message) {
        super(message);
    }

    public GeneratorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
