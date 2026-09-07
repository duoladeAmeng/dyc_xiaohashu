package com.dyc.xiaohashu.id.generator.core;

/**
 * Raised when generator configuration cannot safely produce unique IDs.
 */
public class InvalidIdGeneratorConfigurationException extends IllegalArgumentException {

    public InvalidIdGeneratorConfigurationException(String message) {
        super(message);
    }
}
