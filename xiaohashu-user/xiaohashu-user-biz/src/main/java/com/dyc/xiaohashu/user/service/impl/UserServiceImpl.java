package com.dyc.xiaohashu.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.dyc.framework.biz.context.holder.LoginUserContextHolder;
import com.dyc.framework.common.exception.BizException;
import com.dyc.framework.common.response.Response;
import com.dyc.framework.common.util.JsonUtils;
import com.dyc.xiaohashu.user.constant.RedisKeyConstants;
import com.dyc.xiaohashu.user.constant.RoleConstants;
import com.dyc.xiaohashu.user.domain.dataobject.RoleDO;
import com.dyc.xiaohashu.user.domain.dataobject.UserCountDO;
import com.dyc.xiaohashu.user.domain.dataobject.UserDO;
import com.dyc.xiaohashu.user.domain.dataobject.UserRoleDO;
import com.dyc.xiaohashu.user.domain.mapper.RoleDOMapper;
import com.dyc.xiaohashu.user.domain.mapper.UserCountDOMapper;
import com.dyc.xiaohashu.user.domain.mapper.UserDOMapper;
import com.dyc.xiaohashu.user.domain.mapper.UserRoleDOMapper;
import com.dyc.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.dyc.xiaohashu.user.dto.req.FindUserByPhoneReqDTO;
import com.dyc.xiaohashu.user.dto.req.FindUsersByIdsReqDTO;
import com.dyc.xiaohashu.user.dto.req.RegisterUserReqDTO;
import com.dyc.xiaohashu.user.dto.req.UpdateUserPasswordReqDTO;
import com.dyc.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.dyc.xiaohashu.user.dto.resp.FindUserByPhoneRspDTO;
import com.dyc.xiaohashu.user.enums.ResponseCodeEnum;
import com.dyc.xiaohashu.user.enums.SexEnum;
import com.dyc.xiaohashu.user.model.vo.FindUserProfileReq;
import com.dyc.xiaohashu.user.model.vo.FindUserProfileRsp;
import com.dyc.xiaohashu.user.model.vo.UpdateUserInfoReq;
import com.dyc.xiaohashu.user.rpc.DistributedIdGeneratorRpcService;
import com.dyc.xiaohashu.user.rpc.OssRpcService;
import com.dyc.xiaohashu.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private UserDOMapper userDOMapper;
    @Resource
    private UserRoleDOMapper userRoleDOMapper;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    /**
     * 用户信息本地缓存
     */
    private static final Cache<Long, FindUserByIdRspDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();
    private static final long LOCAL_CACHE_EXPIRE_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Pattern NICK_NAME_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern XIAOHASHU_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReq updateUserInfoReqVO) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BizException(ResponseCodeEnum.CURRENT_USER_NOT_FOUND);
        }

        UserDO userDO = new UserDO();
        userDO.setId(currentUserId);
        boolean needUpdate = false;

        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();
        if (avatarFile != null) {
            String avatar = ossRpcService.uploadFile(avatarFile);
            if (!StringUtils.hasText(avatar)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }
            userDO.setAvatar(avatar);
            needUpdate = true;
        }

        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.hasText(nickname)) {
            checkArgument(checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL);
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        String xiaohashuId = updateUserInfoReqVO.getXiaohashuId();
        if (StringUtils.hasText(xiaohashuId)) {
            checkArgument(checkXiaohashuId(xiaohashuId), ResponseCodeEnum.XIAOHASHU_ID_VALID_FAIL);
            userDO.setXiaohashuId(xiaohashuId);
            needUpdate = true;
        }

        Integer sex = updateUserInfoReqVO.getSex();
        if (sex != null) {
            checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL);
            userDO.setSex(sex);
            needUpdate = true;
        }

        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (birthday != null) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.hasText(introduction)) {
            checkArgument(checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL);
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (backgroundImgFile != null) {
            String backgroundImg = ossRpcService.uploadFile(backgroundImgFile);
            if (!StringUtils.hasText(backgroundImg)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }
            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
//       TODO     evictUserInfoCache(currentUserId);
        }
        return Response.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
        String phone = registerUserReqDTO.getPhone();
        UserDO exists = userDOMapper.selectByPhone(phone);
        if (exists != null) {
            return Response.success(exists.getId());
        }

        String xiaohashuId = distributedIdGeneratorRpcService.getXiaohashuId();
        Long userId = Long.valueOf(distributedIdGeneratorRpcService.getUserId());
        LocalDateTime now = LocalDateTime.now();

        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .xiaohashuId(xiaohashuId)
                .nickname("小哈薯" + xiaohashuId)
                .status(0)
                .createTime(now)
                .updateTime(now)
                .isDeleted(false)
                .build();
        userDOMapper.insert(userDO);

        UserRoleDO userRoleDO = UserRoleDO.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(now)
                .updateTime(now)
                .isDeleted(false)
                .build();
        userRoleDOMapper.insert(userRoleDO);

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
        String roleKey = roleDO == null || !StringUtils.hasText(roleDO.getRoleKey())
                ? RoleConstants.COMMON_USER_ROLE_KEY
                : roleDO.getRoleKey();
        stringRedisTemplate.opsForValue()
                .set(RedisKeyConstants.buildUserRoleKey(userId), toJson(Collections.singletonList(roleKey)));

        userCountDOMapper.insert(UserCountDO.builder().userId(userId).build());
        return Response.success(userId);
    }

    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        UserDO userDO = userDOMapper.selectByPhone(findUserByPhoneReqDTO.getPhone());
        if (userDO == null) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }
        return Response.success(FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .password(userDO.getPassword())
                .build());
    }

    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BizException(ResponseCodeEnum.CURRENT_USER_NOT_FOUND);
        }

        UserDO userDO = UserDO.builder()
                .id(currentUserId)
                .password(updateUserPasswordReqDTO.getEncodePassword())
                .updateTime(LocalDateTime.now())
                .build();
        userDOMapper.updateByPrimaryKeySelective(userDO);
        return Response.success();
    }

    @Override
    public Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();
        FindUserByIdRspDTO findUserByIdRspDTO =LOCAL_CACHE.getIfPresent(userId);
        if (Objects.nonNull(findUserByIdRspDTO)) {
            return Response.success(findUserByIdRspDTO);
        }

        String redisKey = RedisKeyConstants.buildUserInfoKey(userId);
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.hasText(redisValue) && !"null".equals(redisValue)) {
            FindUserByIdRspDTO dto = parseJson(redisValue, FindUserByIdRspDTO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(userId, dto);
            });
            return Response.success(dto);
        }

        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);
        if (userDO == null) {
            threadPoolTaskExecutor.execute(() -> stringRedisTemplate.opsForValue()
                    .set(redisKey, "null", 60 + randomSeconds(60), TimeUnit.SECONDS));
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByIdRspDTO dto = toFindUserByIdRspDTO(userDO);


        // 异步将用户信息存入 Redis 缓存，提升响应速度
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            stringRedisTemplate.opsForValue()
                    .set(redisKey, JsonUtils.toJsonString(dto), expireSeconds, TimeUnit.SECONDS);
        });

        return Response.success(dto);

    }

    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        List<Long> userIds = findUsersByIdsReqDTO.getIds();
        if (CollectionUtils.isEmpty(userIds)) {
            return Response.success(Collections.emptyList());
        }

        List<FindUserByIdRspDTO> result = new ArrayList<>();
        List<Long> needQueryIds = new ArrayList<>();

        for (Long userId : userIds) {
            FindUserByIdRspDTO cached = LOCAL_CACHE.getIfPresent(userId);
            if (cached != null) {
                result.add(cached);
            } else {
                needQueryIds.add(userId);
            }
        }

        if (!needQueryIds.isEmpty()) {
            List<String> redisKeys = needQueryIds.stream().map(RedisKeyConstants::buildUserInfoKey).toList();
            List<String> redisValues = stringRedisTemplate.opsForValue().multiGet(redisKeys);
            Set<Long> redisHitUserIds = new HashSet<>();

            if (redisValues != null) {
                for (String value : redisValues) {
                    if (StringUtils.hasText(value) && !"null".equals(value)) {
                        FindUserByIdRspDTO dto = parseJson(value, FindUserByIdRspDTO.class);
                        result.add(dto);
                        redisHitUserIds.add(dto.getId());
//                        putLocalCache(dto.getId(), dto);
                    }
                }
            }

            List<Long> dbQueryIds = needQueryIds.stream()
                    .filter(id -> !redisHitUserIds.contains(id))
                    .toList();
            if (!dbQueryIds.isEmpty()) {
                List<UserDO> users = userDOMapper.selectByIds(dbQueryIds);
                if (!CollectionUtils.isEmpty(users)) {
                    List<FindUserByIdRspDTO> dbDtos = users.stream()
                            .map(this::toFindUserByIdRspDTO)
                            .toList();
                    result.addAll(dbDtos);
                    //TODO
//                    taskExecutor.execute(() -> dbDtos.forEach(dto ->
//                            cacheUserInfo(RedisKeyConstants.buildUserInfoKey(dto.getId()), dto.getId(), dto)));
                }
            }
        }

        return Response.success(result);
    }

    @Override
    public Response<FindUserProfileRsp> findUserProfile(FindUserProfileReq findUserProfileReqVO) {
        Long userId = findUserProfileReqVO.getUserId();
        if (userId == null) {
            userId = LoginUserContextHolder.getUserId();
        }
        if (userId == null) {
            throw new BizException(ResponseCodeEnum.CURRENT_USER_NOT_FOUND);
        }

        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);
        if (userDO == null) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserProfileRsp rspVO = FindUserProfileRsp.builder()
                .userId(userDO.getId())
                .avatar(userDO.getAvatar())
                .nickname(userDO.getNickname())
                .xiaohashuId(userDO.getXiaohashuId())
                .sex(userDO.getSex())
                .introduction(userDO.getIntroduction())
                .build();

        LocalDate birthday = userDO.getBirthday();
        if (birthday != null) {
            rspVO.setAge(Period.between(birthday, LocalDate.now()).getYears());
            rspVO.setBirthday(birthday);
        }

        UserCountDO countDO = userCountDOMapper.selectByUserId(userId);
        if (countDO != null) {
            long fansTotal = nullToZero(countDO.getFansTotal());
            long followingTotal = nullToZero(countDO.getFollowingTotal());
            long likeTotal = nullToZero(countDO.getLikeTotal());
            long collectTotal = nullToZero(countDO.getCollectTotal());
            rspVO.setFansTotal(formatNumberString(fansTotal));
            rspVO.setFollowingTotal(formatNumberString(followingTotal));
            rspVO.setLikeAndCollectTotal(formatNumberString(likeTotal + collectTotal));
        }

        return Response.success(rspVO);
    }

    private FindUserByIdRspDTO toFindUserByIdRspDTO(UserDO userDO) {
        return FindUserByIdRspDTO.builder()
                .id(userDO.getId())
                .nickName(userDO.getNickname())
                .avatar(userDO.getAvatar())
                .introduction(userDO.getIntroduction())
                .build();
    }




    private long randomSeconds(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void checkArgument(boolean expression, ResponseCodeEnum responseCodeEnum) {
        if (!expression) {
            throw new IllegalArgumentException(responseCodeEnum.getErrorMessage());
        }
    }

    private boolean checkNickname(String nickname) {
        return nickname != null && nickname.length() >= 2 && nickname.length() <= 24
                && !NICK_NAME_PATTERN.matcher(nickname).find();
    }

    private boolean checkXiaohashuId(String xiaohashuId) {
        return xiaohashuId != null && xiaohashuId.length() >= 6 && xiaohashuId.length() <= 15
                && XIAOHASHU_ID_PATTERN.matcher(xiaohashuId).matches();
    }

    private boolean checkLength(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }

    private String formatNumberString(long number) {
        if (number < 10_000) {
            return String.valueOf(number);
        }
        if (number < 100_000_000) {
            long integerPart = number / 10_000;
            long decimalPart = number % 10_000 / 1_000;
            return decimalPart == 0 ? integerPart + "万" : integerPart + "." + decimalPart + "万";
        }
        return "9999万";
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("对象转 JSON 失败", e);
        }
    }

    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转对象失败", e);
        }
    }


}
