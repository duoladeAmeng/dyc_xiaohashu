package com.dyc.xiaohashu.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.dyc.framework.common.response.PageResponse;
import com.dyc.xiaohashu.note.domain.dataobject.NoteDO;
import com.dyc.xiaohashu.note.domain.mapper.NoteDOMapper;
import com.dyc.xiaohashu.note.dto.req.FindDiscoverNotePageListReq;
import com.dyc.xiaohashu.note.dto.rsp.FindDiscoverNoteRsp;
import com.dyc.xiaohashu.note.enums.NoteTypeEnum;
import com.dyc.xiaohashu.note.rpc.UserRpcService;
import com.dyc.xiaohashu.note.service.DiscoverService;
import com.dyc.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DiscoverServiceImpl implements DiscoverService {

    @Resource
    private NoteDOMapper noteDOMapper;

    @Resource
    private UserRpcService userRpcService;

    @Override
    public PageResponse<FindDiscoverNoteRsp> findNoteList(FindDiscoverNotePageListReq findDiscoverNoteListReq) {
        Long channelId = findDiscoverNoteListReq.getChannelId();
        Integer pageNo = findDiscoverNoteListReq.getPageNo();

        // 每页展示的数据量
        long pageSize = 10;

        // TODO: 为快速完成前后端联调，目前走数据库，后续需改成查询 Elasticserach
        int count = noteDOMapper.selectTotalCount(channelId);

        // 若评论总数为 0，则直接响应
        if (count == 0) {
            return PageResponse.success(null, pageNo, 0);
        }

        // 计算分页查询的偏移量 offset
        long offset = PageResponse.getOffset(pageNo, pageSize);
        long totalPage = PageResponse.getTotalPage(count, pageSize);

        // 若请求的页码大于总页数，直接响应
        if (pageNo > totalPage) {
            return PageResponse.success(null, pageNo, totalPage);
        }

        List<NoteDO> noteDOS = noteDOMapper.selectPageList(channelId, offset, pageSize);
        if (CollUtil.isEmpty(noteDOS)) {
            return PageResponse.success(null, pageNo, count, pageSize);
        }

        List<Long> creatorIds = noteDOS.stream().map(NoteDO::getCreatorId).toList();

        // RPC: 调用用户服务，批量获取用户信息（头像、昵称等）
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(creatorIds);
        Map<Long, FindUserByIdRspDTO> userIdAndDTOMap = CollUtil.isEmpty(findUserByIdRspDTOS)
                ? Collections.emptyMap()
                : findUserByIdRspDTOS.stream()
                .collect(Collectors.toMap(FindUserByIdRspDTO::getId, dto -> dto, (oldValue, newValue) -> oldValue));

        List<FindDiscoverNoteRsp> noteRspList = new ArrayList<>();
        // 分页返参
        for (NoteDO noteDO : noteDOS) {
            Integer type = noteDO.getType();
            FindDiscoverNoteRsp findDiscoverNoteRsp = FindDiscoverNoteRsp.builder()
                    .id(String.valueOf(noteDO.getId()))
                    .title(noteDO.getTitle())
                    .type(type)
                    .likeTotal("0")
                    .build();

            NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
            if (Objects.equals(noteTypeEnum, NoteTypeEnum.IMAGE_TEXT)) {
                // 提取第一张图片作为封面图
                String imgUris = noteDO.getImgUris();
                if (StringUtils.hasText(imgUris)) {
                    findDiscoverNoteRsp.setCover(imgUris.split(",")[0]);
                }
            } else if (Objects.equals(noteTypeEnum, NoteTypeEnum.VIDEO)) {
                findDiscoverNoteRsp.setVideoUri(noteDO.getVideoUri());
            }

            // 设置发布者信息
            Long creatorId = noteDO.getCreatorId();
            FindUserByIdRspDTO findUserByIdRspDTO = userIdAndDTOMap.get(creatorId);
            if (Objects.nonNull(findUserByIdRspDTO)) {
                findDiscoverNoteRsp.setCreatorId(creatorId);
                findDiscoverNoteRsp.setNickname(findUserByIdRspDTO.getNickName());
                findDiscoverNoteRsp.setAvatar(findUserByIdRspDTO.getAvatar());
            }

            noteRspList.add(findDiscoverNoteRsp);
        }

        return PageResponse.success(noteRspList, pageNo, count, pageSize);
    }
}
