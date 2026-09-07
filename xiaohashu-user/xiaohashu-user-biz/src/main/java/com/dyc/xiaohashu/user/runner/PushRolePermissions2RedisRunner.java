package com.dyc.xiaohashu.user.runner;

import com.dyc.xiaohashu.user.constant.RedisKeyConstants;
import com.dyc.xiaohashu.user.domain.dataobject.PermissionDO;
import com.dyc.xiaohashu.user.domain.dataobject.RoleDO;
import com.dyc.xiaohashu.user.domain.dataobject.RolePermissionDO;
import com.dyc.xiaohashu.user.domain.mapper.PermissionDOMapper;
import com.dyc.xiaohashu.user.domain.mapper.RoleDOMapper;
import com.dyc.xiaohashu.user.domain.mapper.RolePermissionDOMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private PermissionDOMapper permissionDOMapper;
    @Resource
    private RolePermissionDOMapper rolePermissionDOMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean canPush = stringRedisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS);
            if (!Boolean.TRUE.equals(canPush)) {
                return;
            }

            List<RoleDO> roles = roleDOMapper.selectEnabledList();
            if (CollectionUtils.isEmpty(roles)) {
                return;
            }

            List<Long> roleIds = roles.stream().map(RoleDO::getId).toList();
            List<RolePermissionDO> rolePermissions = rolePermissionDOMapper.selectByRoleIds(roleIds);
            if (CollectionUtils.isEmpty(rolePermissions)) {
                return;
            }

            Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissions.stream()
                    .collect(Collectors.groupingBy(RolePermissionDO::getRoleId,
                            Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList())));

            List<PermissionDO> permissions = permissionDOMapper.selectAppEnabledList();
            if (CollectionUtils.isEmpty(permissions)) {
                return;
            }

            Map<Long, PermissionDO> permissionMap = permissions.stream()
                    .collect(Collectors.toMap(PermissionDO::getId, permission -> permission));
            Map<String, List<String>> roleKeyPermissionsMap = new HashMap<>();

            for (RoleDO role : roles) {
                List<Long> permissionIds = roleIdPermissionIdsMap.get(role.getId());
                if (CollectionUtils.isEmpty(permissionIds)) {
                    continue;
                }
                List<String> permissionKeys = new ArrayList<>();
                for (Long permissionId : permissionIds) {
                    PermissionDO permission = permissionMap.get(permissionId);
                    if (Objects.nonNull(permission)) {
                        permissionKeys.add(permission.getPermissionKey());
                    }
                }
                roleKeyPermissionsMap.put(role.getRoleKey(), permissionKeys);
            }

            roleKeyPermissionsMap.forEach((roleKey, permissionKeys) ->
                    stringRedisTemplate.opsForValue().set(RedisKeyConstants.buildRolePermissionsKey(roleKey),
                            toJson(permissionKeys)));
        } catch (Exception e) {
            log.error("同步角色权限到 Redis 失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("对象转 JSON 失败", e);
        }
    }
}
