package com.dyc.xiaohashu.note.domain.mapper;

import com.dyc.xiaohashu.note.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    NoteDO selectByPrimaryKey(Long id);

    int selectTotalCount(@Param("channelId") Long channelId);

    List<NoteDO> selectPageList(@Param("channelId") Long channelId,
                                @Param("offset") long offset,
                                @Param("pageSize") long pageSize);

    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    int updateVisibleOnlyMe(NoteDO noteDO);

    int updateIsTop(NoteDO noteDO);
}
