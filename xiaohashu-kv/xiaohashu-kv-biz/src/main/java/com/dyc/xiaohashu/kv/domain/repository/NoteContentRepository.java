package com.dyc.xiaohashu.kv.domain.repository;

import com.dyc.xiaohashu.kv.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;


public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {

}
