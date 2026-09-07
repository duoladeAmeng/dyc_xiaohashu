package com.dyc.xiaohashu.id.generator.core;

public class GeneratorUnavailableException extends IdGeneratorException {

    public GeneratorUnavailableException(String message) {
        super(message);
    }

    public GeneratorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
