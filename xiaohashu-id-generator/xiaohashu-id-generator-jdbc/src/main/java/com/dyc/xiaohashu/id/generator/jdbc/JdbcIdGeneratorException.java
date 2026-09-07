package com.dyc.xiaohashu.id.generator.jdbc;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

/**
 * Raised when JDBC persistence fails.
 */
public class JdbcIdGeneratorException extends IdGeneratorException {

    public JdbcIdGeneratorException(String message) {
        super(message);
    }

    public JdbcIdGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
