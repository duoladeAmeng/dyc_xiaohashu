package com.dyc.xiaohashu.kv;


import com.dyc.framework.common.util.JsonUtils;
import com.dyc.xiaohashu.kv.domain.dataobject.NoteContentDO;
import com.dyc.xiaohashu.kv.domain.repository.NoteContentRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Slf4j
public class Data {
    @Resource
    private NoteContentRepository noteContentRepository;

    /**
     * 测试插入数据
     */
    @Test
    void testInsert() {
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.randomUUID())
                .content("代码测试笔记内容插入")
                .build();

        noteContentRepository.save(nodeContent);
    }

    /**
     * 测试修改数据
     */
    @Test
    void testUpdate() {
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.fromString("4c7f77d6-92bb-471e-af85-f0c6da0fdc51"))
                .content("ssssssssssssssssss")
                .build();

        noteContentRepository.save(nodeContent);
    }

    /**
     * 测试查询数据
     */
    @Test
    void testSelect() {
//        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString("eaad1222-f091-40be-b824-0c9f275724a7"));
//        optional.ifPresent(noteContentDO -> log.info("查询结果：{}", JsonUtils.toJsonString(noteContentDO)));
        List<NoteContentDO> all = noteContentRepository.findAll();
        for (NoteContentDO nodeContent : all) {
            System.out.println(nodeContent);
        }

    }

    /**
     * 测试删除数据
     */
    @Test
    void testDelete() {
        noteContentRepository.deleteById(UUID.fromString("f1eab1d0-155a-455d-973a-887f2024502d"));
    }

}
