package com.dyc.xiaohashu.user.domain.mapper;

import com.dyc.xiaohashu.user.domain.dataobject.UserCountDO;

public interface UserCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserCountDO record);

    int insertSelective(UserCountDO record);

    UserCountDO selectByPrimaryKey(Long id);

    UserCountDO selectByUserId(Long userId);

    int updateByPrimaryKeySelective(UserCountDO record);

    int updateByPrimaryKey(UserCountDO record);
}
