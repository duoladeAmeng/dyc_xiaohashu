create table if not exists id_machine (
  namespace varchar(64) not null,
  machine_id int unsigned not null,
  instance_id varchar(128) not null,
  status varchar(16) not null,
  stable boolean not null default false,
  last_timestamp bigint unsigned not null default 0,
  last_heartbeat_at datetime(3) not null,
  lease_expires_at datetime(3) not null,
  version bigint unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace, machine_id),
  unique key uk_machine_instance (namespace, instance_id),
  key idx_machine_stable (namespace, stable),
  key idx_machine_reclaim (namespace, status, lease_expires_at),
  key idx_machine_heartbeat (namespace, last_heartbeat_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists id_machine_sequence (
  namespace varchar(64) not null,
  next_machine_id int unsigned not null default 0,
  created_at datetime(3) not null,
  updated_at datetime(3) not null,
  primary key (namespace)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists id_segment (
  namespace varchar(64) not null,
  tag varchar(64) not null,
  max_id bigint unsigned not null default 0,
  step bigint unsigned not null,
  version bigint unsigned not null default 0,
  last_fetch_at datetime(3) default null,
  updated_at datetime(3) not null,
  created_at datetime(3) not null,
  primary key (namespace, tag)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
