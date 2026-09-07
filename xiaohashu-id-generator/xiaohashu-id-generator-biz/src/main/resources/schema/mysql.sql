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
) engine=InnoDB default charset=utf8mb4;

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
) engine=InnoDB default charset=utf8mb4;
