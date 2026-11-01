package com.dyc.xiaohashu.user.domain.mapper;

import com.dyc.xiaohashu.user.domain.dataobject.PermissionDO;

import java.util.List;

public interface PermissionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(PermissionDO record);

    int insertSelective(PermissionDO record);

    PermissionDO selectByPrimaryKey(Long id);

    /**
     * 查询所有被启用的权限
     *
     * @return
     */
    List<PermissionDO> selectAppEnabledList();

    int updateByPrimaryKeySelective(PermissionDO record);

    int updateByPrimaryKey(PermissionDO record);

}