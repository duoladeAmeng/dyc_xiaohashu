package com.dyc.xiaohashu.id.generator.jdbc;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientException;

/**
 * Classifies SQL failures that are safe to retry.
 */
public final class SqlExceptionClassifier {

    private SqlExceptionClassifier() {
    }

    public static boolean isRetryable(SQLException exception) {
        return exception instanceof SQLTransientException
                || exception instanceof SQLRecoverableException
                || exception instanceof SQLTransactionRollbackException
                || exception instanceof SQLIntegrityConstraintViolationException;
    }
}
