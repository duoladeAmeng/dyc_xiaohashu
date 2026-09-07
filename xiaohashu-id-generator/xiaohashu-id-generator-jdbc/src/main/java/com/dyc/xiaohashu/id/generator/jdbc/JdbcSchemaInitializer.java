package com.dyc.xiaohashu.id.generator.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Initializes JDBC tables for tests and simple deployments.
 */
public class JdbcSchemaInitializer {

    private static final String CREATE_MACHINE_TABLE = """
            create table if not exists id_machine (
              namespace varchar(64) not null,
              machine_id int not null,
              instance_id varchar(128) not null,
              status varchar(16) not null,
              stable boolean not null default false,
              last_timestamp bigint not null default 0,
              last_heartbeat_at timestamp(3) not null,
              lease_expires_at timestamp(3) not null,
              version bigint not null default 0,
              created_at timestamp(3) not null,
              updated_at timestamp(3) not null,
              primary key (namespace, machine_id),
              unique (namespace, instance_id)
            )
            """;

    private static final String CREATE_RECLAIM_INDEX = """
            create index idx_machine_reclaim
            on id_machine (namespace, status, lease_expires_at)
            """;

    private static final String CREATE_STABLE_INDEX = """
            create index idx_machine_stable
            on id_machine (namespace, stable)
            """;

    private static final String CREATE_HEARTBEAT_INDEX = """
            create index idx_machine_heartbeat
            on id_machine (namespace, last_heartbeat_at)
            """;

    private static final String CREATE_MACHINE_SEQUENCE_TABLE = """
            create table if not exists id_machine_sequence (
              namespace varchar(64) not null,
              next_machine_id int not null default 0,
              created_at timestamp(3) not null,
              updated_at timestamp(3) not null,
              primary key (namespace)
            )
            """;

    private static final String CREATE_SEGMENT_TABLE = """
            create table if not exists id_segment (
              namespace varchar(64) not null,
              tag varchar(64) not null,
              max_id bigint not null default 0,
              step bigint not null,
              version bigint not null default 0,
              last_fetch_at timestamp(3) default null,
              updated_at timestamp(3) not null,
              created_at timestamp(3) not null,
              primary key (namespace, tag)
            )
            """;

    private final DataSource dataSource;

    public JdbcSchemaInitializer(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_MACHINE_TABLE);
            createIndexIfAbsent(connection, statement, "id_machine", "idx_machine_reclaim", CREATE_RECLAIM_INDEX);
            createIndexIfAbsent(connection, statement, "id_machine", "idx_machine_stable", CREATE_STABLE_INDEX);
            createIndexIfAbsent(connection, statement, "id_machine", "idx_machine_heartbeat", CREATE_HEARTBEAT_INDEX);
            statement.execute(CREATE_MACHINE_SEQUENCE_TABLE);
            statement.execute(CREATE_SEGMENT_TABLE);
        } catch (SQLException exception) {
            throw new JdbcIdGeneratorException("initialize schema failed", exception);
        }
    }

    private void createIndexIfAbsent(
            Connection connection,
            Statement statement,
            String tableName,
            String indexName,
            String createIndexSql
    ) throws SQLException {
        if (!indexExists(connection, tableName, indexName)) {
            statement.execute(createIndexSql);
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        return indexExists(connection, tableName, indexName, tableName)
                || indexExists(connection, tableName, indexName, tableName.toUpperCase());
    }

    private boolean indexExists(Connection connection, String tableName, String indexName, String metadataTableName)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, metadataTableName, false, false)) {
            while (resultSet.next()) {
                String existingIndexName = resultSet.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existingIndexName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
