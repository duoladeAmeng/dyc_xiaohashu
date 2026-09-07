package com.dyc.xiaohashu.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.note.domain.dataobject.ChannelDO;
import com.dyc.xiaohashu.note.domain.mapper.ChannelDOMapper;
import com.dyc.xiaohashu.note.dto.rsp.FindChannelRsp;
import com.dyc.xiaohashu.note.service.ChannelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class ChannelServiceImpl implements ChannelService {

    @Resource
    private ChannelDOMapper channelDOMapper;

    /**
     * 查询所有频道
     *
     * @return
     */
    @Override
    public Response<List<FindChannelRsp>> findChannelList() {
        // TODO: 加二级缓存

        List<ChannelDO> channelDOS = channelDOMapper.selectAll();

        List<FindChannelRsp> channelRsps = new ArrayList<>();

        // 默认添加一个 “全部” 分类
        // FindChannelRspVO allChannel = FindChannelRspVO.builder()
        //         .id(0L)
        //         .name("全部")
        //         .build();
        // channelRspVOS.add(allChannel);

        if (CollUtil.isNotEmpty(channelDOS)) {
            CollUtil.addAll(channelRsps, channelDOS.stream()
                    .map(channelDO -> FindChannelRsp.builder()
                            .id(channelDO.getId())
                            .name(channelDO.getName())
                            .build())
                    .toList());
        }

        return Response.success(channelRsps);
    }
}
