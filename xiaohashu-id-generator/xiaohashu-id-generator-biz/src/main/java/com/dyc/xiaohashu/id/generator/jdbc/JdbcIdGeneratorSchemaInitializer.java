package com.dyc.xiaohashu.id.generator.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcIdGeneratorSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdGeneratorSchemaInitializer.class);

    private final DataSource dataSource;

    public JdbcIdGeneratorSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize(String namespace, String segmentName, long segmentStep, String segmentChainName, long segmentChainStep, long initialMaxId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    create table if not exists id_generator_machine (
                      name varchar(128) not null,
                      namespace varchar(64) not null,
                      machine_id int not null,
                      instance_id varchar(128) not null default '',
                      stable_instance boolean not null default false,
                      last_timestamp bigint not null,
                      last_heartbeat timestamp(3) null,
                      status varchar(16) not null,
                      version bigint not null default 0,
                      distribute_time timestamp(3) null,
                      revert_time timestamp(3) null,
                      primary key (name),
                      unique key uk_id_generator_machine_namespace_machine (namespace, machine_id),
                      key idx_id_generator_machine_instance (namespace, instance_id),
                      key idx_id_generator_machine_reclaim (namespace, status, last_timestamp)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            statement.executeUpdate("""
                    create table if not exists id_generator_segment (
                      namespace varchar(64) not null,
                      name varchar(64) not null,
                      last_max_id bigint not null,
                      step bigint not null,
                      version bigint not null default 0,
                      last_fetch_time timestamp(3) null,
                      create_time timestamp(3) not null default current_timestamp(3),
                      update_time timestamp(3) not null default current_timestamp(3) on update current_timestamp(3),
                      primary key (namespace, name)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            upsertSegment(connection, namespace, segmentName, segmentStep, initialMaxId);
            upsertSegment(connection, namespace, segmentChainName, segmentChainStep, initialMaxId);
            log.info("Initialized JDBC id-generator schema for namespace {}.", namespace);
        } catch (SQLException exception) {
            throw new IllegalStateException("Initialize id-generator schema failed.", exception);
        }
    }

    private void upsertSegment(Connection connection, String namespace, String name, long step, long initialMaxId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into id_generator_segment(namespace,name,last_max_id,step,last_fetch_time)
                values(?,?,?,?,current_timestamp(3))
                on duplicate key update namespace=namespace
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, name);
            statement.setLong(3, initialMaxId);
            statement.setLong(4, step);
            statement.executeUpdate();
        }
    }
}
