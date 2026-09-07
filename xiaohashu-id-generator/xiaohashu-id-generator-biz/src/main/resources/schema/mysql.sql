create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint unsigned not null default 0,
    last_fetch_time bigint unsigned not null default 0,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;

create table if not exists cosid_machine
(
    name            varchar(100)     not null comment '{namespace}.{machine_id}',
    namespace       varchar(100)     not null,
    machine_id      integer unsigned not null default 0,
    last_timestamp  bigint unsigned  not null default 0,
    instance_id     varchar(100)     not null default '',
    distribute_time bigint unsigned  not null default 0,
    revert_time     bigint unsigned  not null default 0,
    constraint cosid_machine_pk
        primary key (name),
    key idx_namespace (namespace),
    key idx_instance_id (instance_id)
) engine = InnoDB;
