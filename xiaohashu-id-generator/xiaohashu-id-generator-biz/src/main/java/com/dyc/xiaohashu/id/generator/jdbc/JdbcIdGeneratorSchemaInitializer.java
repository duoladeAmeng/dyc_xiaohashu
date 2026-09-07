package com.dyc.xiaohashu.id.generator.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class JdbcIdGeneratorSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdGeneratorSchemaInitializer.class);

    public static final String INIT_COSID_TABLE_SQL =
            "create table if not exists cosid\n"
                    + "(\n"
                    + "    name            varchar(100) not null comment '{namespace}.{name}',\n"
                    + "    last_max_id     bigint unsigned not null default 0,\n"
                    + "    last_fetch_time bigint unsigned not null default 0,\n"
                    + "    constraint cosid_pk\n"
                    + "        primary key (name)\n"
                    + ") engine = InnoDB;";

    public static final String INIT_ID_SEGMENT_SQL = "insert into cosid (name, last_max_id,last_fetch_time) value (?, ?,unix_timestamp());";

    public static final String INIT_COSID_MACHINE_TABLE_SQL =
            "create table if not exists cosid_machine\n"
                    + "(\n"
                    + "    name            varchar(100)     not null comment '{namespace}.{machine_id}',\n"
                    + "    namespace       varchar(100)     not null,\n"
                    + "    machine_id      integer unsigned not null default 0,\n"
                    + "    last_timestamp  bigint unsigned  not null default 0,\n"
                    + "    instance_id     varchar(100)     not null default '',\n"
                    + "    distribute_time bigint unsigned  not null default 0,\n"
                    + "    revert_time     bigint unsigned  not null default 0,\n"
                    + "    constraint cosid_machine_pk\n"
                    + "        primary key (name),\n"
                    + "    key idx_namespace (namespace),\n"
                    + "    key idx_instance_id (instance_id)\n"
                    + ") engine = InnoDB;";

    private final DataSource dataSource;

    public JdbcIdGeneratorSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize(String namespace, String segmentName, long segmentStep, String segmentChainName, long segmentChainStep, long initialMaxId) {
        tryInitCosIdTable();
        tryInitCosIdMachineTable();
        tryInitIdSegment(namespace + "." + segmentName, initialMaxId);
        tryInitIdSegment(namespace + "." + segmentChainName, initialMaxId);
    }

    public int initCosIdTable() throws SQLException {
        log.info("Init CosIdTable");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INIT_COSID_TABLE_SQL)) {
            return statement.executeUpdate();
        }
    }

    public boolean tryInitCosIdTable() {
        try {
            initCosIdTable();
            return true;
        } catch (Throwable throwable) {
            log.info("Try Init CosIdTable failed.[{}]", throwable.getMessage());
            return false;
        }
    }

    public int initIdSegment(String segmentName, long offset) throws SQLException, SQLIntegrityConstraintViolationException {
        if (segmentName == null || segmentName.isEmpty()) {
            throw new IllegalArgumentException("segmentName can not be empty!");
        }
        if (offset < 0) {
            throw new IllegalArgumentException(String.format("offset:[%s] must be greater than or equal to 0!", offset));
        }
        log.info("Init IdSegment - segmentName:[{}] - offset:[{}]", segmentName, offset);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement initStatement = connection.prepareStatement(INIT_ID_SEGMENT_SQL)) {
            initStatement.setString(1, segmentName);
            initStatement.setLong(2, offset);
            return initStatement.executeUpdate();
        }
    }

    public boolean tryInitIdSegment(String segmentName, long offset) {
        try {
            initIdSegment(segmentName, offset);
            return true;
        } catch (Throwable throwable) {
            log.info("Try Init IdSegment failed.[{}]", throwable.getMessage());
            return false;
        }
    }

    public void initCosIdMachineTable() throws SQLException {
        log.info("Init CosIdMachineTable");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement initStatement = connection.prepareStatement(INIT_COSID_MACHINE_TABLE_SQL)) {
            initStatement.executeUpdate();
        }
    }

    public boolean tryInitCosIdMachineTable() {
        try {
            initCosIdMachineTable();
            return true;
        } catch (Throwable throwable) {
            log.info("Try Init CosIdMachineTable failed.[{}]", throwable.getMessage());
            return false;
        }
    }
}
