package com.dyc.xiaohashu.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Sets;
import com.dyc.framework.biz.context.holder.LoginUserContextHolder;
import com.dyc.framework.common.exception.BizException;
import com.dyc.framework.common.response.Response;
import com.dyc.framework.common.util.JsonUtils;
import com.dyc.xiaohashu.note.constant.MQConstants;
import com.dyc.xiaohashu.note.constant.RedisKeyConstants;
import com.dyc.xiaohashu.note.domain.dataobject.ChannelDO;
import com.dyc.xiaohashu.note.domain.dataobject.NoteDO;
import com.dyc.xiaohashu.note.domain.dataobject.TopicDO;
import com.dyc.xiaohashu.note.domain.mapper.ChannelDOMapper;
import com.dyc.xiaohashu.note.domain.mapper.NoteDOMapper;
import com.dyc.xiaohashu.note.domain.mapper.TopicDOMapper;
import com.dyc.xiaohashu.note.dto.NoteOperateMqDTO;
import com.dyc.xiaohashu.note.dto.req.DeleteNoteReq;
import com.dyc.xiaohashu.note.dto.req.FindNoteDetailReq;
import com.dyc.xiaohashu.note.dto.req.PublishNoteReq;
import com.dyc.xiaohashu.note.dto.req.TopNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteReq;
import com.dyc.xiaohashu.note.dto.req.UpdateNoteVisibleOnlyMeReq;
import com.dyc.xiaohashu.note.dto.rsp.FindNoteDetailRsp;
import com.dyc.xiaohashu.note.dto.rsp.FindTopicRsp;
import com.dyc.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import com.dyc.xiaohashu.note.enums.NoteOperateEnum;
import com.dyc.xiaohashu.note.enums.NoteStatusEnum;
import com.dyc.xiaohashu.note.enums.NoteTypeEnum;
import com.dyc.xiaohashu.note.enums.NoteVisibleEnum;
import com.dyc.xiaohashu.note.enums.ResponseCodeEnum;
import com.dyc.xiaohashu.note.rpc.KeyValueRpcService;
import com.dyc.xiaohashu.note.rpc.UserRpcService;
import com.dyc.xiaohashu.note.service.NoteService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
//    @Resource
//    private TransactionTemplate transactionTemplate;
    @Resource
    private ChannelDOMapper channelDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000)
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();


    /**
     * 笔记发布
     *
     * @param publishNoteReq
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Response<?> publishNote(PublishNoteReq publishNoteReq) {
        // 笔记类型
        Integer type = publishNoteReq.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = publishNoteReq.getImgUris();
                // 校验图片是否为空
                Assert.isTrue(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Assert.isTrue(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StrUtil.join( ",",imgUriList);

                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReq.getVideoUri();
                // 校验视频链接是否为空
                Assert.isTrue(StrUtil.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        // 判断所选频道是否存在
        Long channelId = publishNoteReq.getChannelId();
        ChannelDO channelDO = channelDOMapper.selectByPrimaryKey(channelId);

        if (Objects.isNull(channelDO)) {
            throw new BizException(ResponseCodeEnum.CHANNEL_NOT_FOUND);
        }

        // RPC: 调用分布式 ID 生成服务，生成笔记 ID
//        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
        String snowflakeIdId = RandomUtil.randomNumbers(8);
        // 笔记内容 UUID。
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReq.getContent();

        // 若用户填写了笔记内容
        if (StrUtil.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
            // RPC: 调用 KV 键值服务，存储短文本
            boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);

            // 若存储失败，抛出业务异常，提示用户发布笔记失败
            if (!isSavedSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 话题处理
        String topicIds = handleTopics(publishNoteReq.getTopics());

        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeIdId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReq.getTitle())
                .channelId(publishNoteReq.getChannelId())
                .topicIds(topicIds)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();

        try {
            // 笔记入库存储
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败", e);
            if (StrUtil.isNotBlank(contentUuid)) {
                // RPC: 笔记保存失败，则删除笔记内容
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
        }

//        // 发送 MQ
//        // 构建消息体 DTO
//        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
//                .creatorId(creatorId)
//                .noteId(Long.valueOf(snowflakeIdId))
//                .type(NoteOperateEnum.PUBLISH.getCode()) // 发布笔记
//                .build();
//
//        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
//        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
//                .build();
//
//        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
//        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;
//
//        // 异步发送 MQ 消息，提升接口响应速度
//        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//                log.info("==> 【笔记发布】MQ 发送成功，SendResult: {}", sendResult);
//            }
//
//            @Override
//            public void onException(Throwable throwable) {
//                log.error("==> 【笔记发布】MQ 发送异常: ", throwable);
//            }
//        });

        return Response.success();
    }

    private String handleTopics(List<Object> topicInputs) {
        if (CollUtil.isEmpty(topicInputs)) return null;

        // 1. 分离已存在话题（ID）和新话题（名称）
        List<Long> existingTopicIds = new ArrayList<>();
        List<String> newTopicNames = new ArrayList<>();

        topicInputs.forEach(input -> {
            if (input instanceof Number) {
                // 已存在话题 ID
                existingTopicIds.add(Long.valueOf(String.valueOf(input)));
            } else if (input instanceof String) {
                // 新话题名称
                newTopicNames.add((String) input);
            }
        });

        // 2. 查询现有话题信息 - 批量查询
        Set<Long> existingTopicIdsSet = Sets.newHashSet();
        if (CollUtil.isNotEmpty(existingTopicIds)) {
            List<TopicDO> existingTopicDOS = topicDOMapper.selectByTopicIdIn(existingTopicIds);
            existingTopicIdsSet = existingTopicDOS.stream()
                    .map(TopicDO::getId)
                    .collect(Collectors.toSet());
        }


        // 3. 处理新标签
        List<TopicDO> newTopics = new ArrayList<>();
        for (String topicName : newTopicNames) {
            TopicDO existingTopic = topicDOMapper.selectByTopicName(topicName);
            if (Objects.isNull(existingTopic)) {
                // 话题不存在，插入新话题
                newTopics.add(TopicDO.builder().name(topicName).build());
            } else {
                // 话题已经存在，加入现有话题 ID 列表
                existingTopicIdsSet.add(existingTopic.getId());
            }
        }

        // 4. 批量保存新话题（如果有）
        if (CollUtil.isNotEmpty(newTopics)) {
            topicDOMapper.batchInsert(newTopics);
        }

        // 5. 获取所有话题的 ID（已存在和新插入的）
        List<Long> allTopicIds = new ArrayList<>(existingTopicIdsSet);
        if (CollUtil.isNotEmpty(newTopics)) {
            newTopics.forEach(newTopic -> allTopicIds.add(newTopic.getId()));
        }

        // 6. 将所有的话题 ID 以逗号拼接
        return StrUtil.join( ",",allTopicIds);
    }


    @Override
    public Response<FindNoteDetailRsp> findNoteDetail(FindNoteDetailReq findNoteDetailReq) {
        // 查询的笔记 ID
        Long noteId = findNoteDetailReq.getId();

        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();

        // 先从本地缓存中查询
        String findNoteDetailRspStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StrUtil.isNotBlank(findNoteDetailRspStrLocalCache)) {
            FindNoteDetailRsp findNoteDetailRsp = JsonUtils.parseObject(findNoteDetailRspStrLocalCache, FindNoteDetailRsp.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspStrLocalCache);
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRsp);
            return Response.success(findNoteDetailRsp);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = stringRedisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StrUtil.isNotBlank(noteDetailJson)) {
            FindNoteDetailRsp findNoteDetailRsp = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRsp.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId,
                        Objects.isNull(findNoteDetailRsp) ? "null" : JsonUtils.toJsonString(findNoteDetailRsp));
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRsp);

            return Response.success(findNoteDetailRsp);
        }

        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                stringRedisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        // 并发查询优化
        // RPC: 调用用户服务获取发布者信息
        Long creatorId = noteDO.getCreatorId();
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findById(creatorId), threadPoolTaskExecutor);

        // RPC: 获取笔记内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE) && StrUtil.isNotBlank(noteDO.getContentUuid())) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRsp> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    // 获取 Future 返回的结果
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    // 图文笔记图片链接拆分
                    Integer noteType = noteDO.getType();
                    String imgUrisStr = noteDO.getImgUris();
                    List<String> imgUris = null;
                    if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode()) && StrUtil.isNotBlank(imgUrisStr)) {
                        imgUris = List.of(imgUrisStr.split(","));
                    }

                    // 批量查询话题
                    String topicIdsStr = noteDO.getTopicIds();
                    List<FindTopicRsp> findTopicRspList = null;
                    if (StrUtil.isNotBlank(topicIdsStr)) {
                        List<Long> topicIds = Arrays.stream(topicIdsStr.split(","))
                                .map(Long::valueOf)
                                .toList();
                        List<TopicDO> topicDOS = topicDOMapper.selectByTopicIdIn(topicIds);
                        findTopicRspList = topicDOS.stream()
                                .map(topicDO -> FindTopicRsp.builder()
                                        .id(topicDO.getId())
                                        .name(topicDO.getName())
                                        .build())
                                .toList();
                    }

                    // 计数（默认 0，待计数服务接入后替换）
                    String likeTotal = "0";
                    String collectTotal = "0";
                    String commentTotal = "0";

                    return FindNoteDetailRsp.builder()
                            .id(noteDO.getId())
                            .type(noteDO.getType())
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topics(findTopicRspList)
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(Objects.nonNull(findUserByIdRspDTO) ? findUserByIdRspDTO.getNickName() : null)
                            .avatar(Objects.nonNull(findUserByIdRspDTO) ? findUserByIdRspDTO.getAvatar() : null)
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime() != null ? noteDO.getUpdateTime().toString() : null)
                            .visible(noteDO.getVisible())
                            .likeTotal(likeTotal)
                            .collectTotal(collectTotal)
                            .commentTotal(commentTotal)
                            .build();
                });

        // 获取拼装后的 FindNoteDetailRsp
        FindNoteDetailRsp findNoteDetailRsp = resultFuture.join();

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            String noteDetailJson1 = JsonUtils.toJsonString(findNoteDetailRsp);
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            stringRedisTemplate.opsForValue().set(noteDetailRedisKey, noteDetailJson1, expireSeconds, TimeUnit.SECONDS);
        });

        return Response.success(findNoteDetailRsp);
    }


    /**
     * 校验笔记的可见性（针对 VO 实体类）
     * @param userId
     * @param findNoteDetailRsp
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRsp findNoteDetailRsp) {
        if (Objects.nonNull(findNoteDetailRsp)) {
            Integer visible = findNoteDetailRsp.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRsp.getCreatorId());
        }
    }

    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) {
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReq updateNoteReq) {
        // 笔记 ID
        Long noteId = updateNoteReq.getId();
        // 笔记类型
        Integer type = updateNoteReq.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:
                List<String> imgUriList = updateNoteReq.getImgUris();
                // 校验图片是否为空
                Assert.isTrue(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Assert.isTrue(imgUriList.size() <= 8, "笔记图片不能多于 8 张");

                imgUris = StrUtil.join(",", imgUriList);
                break;
            case VIDEO:
                videoUri = updateNoteReq.getVideoUri();
                // 校验视频链接是否为空
                Assert.isTrue(StrUtil.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 笔记不存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许更新笔记
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {

            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 话题
        Long topicId = updateNoteReq.getTopicId();
        String topicName = null;
        String topicIds = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);

            // 判断一下提交的话题, 是否是真实存在的
            if (StrUtil.isBlank(topicName)) throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            topicIds = String.valueOf(topicId);
        }

        // 更新笔记元数据表 t_note
        String content = updateNoteReq.getContent();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StrUtil.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReq.getTitle())
                .topicId(updateNoteReq.getTopicId())
                .topicName(topicName)
                .topicIds(topicIds)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .build();

        noteDOMapper.updateByPrimaryKey(noteDO);

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        stringRedisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        // 笔记内容更新
        // 查询此篇笔记内容对应的 UUID
        NoteDO noteDO1 = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = noteDO1.getContentUuid();

        // 笔记内容是否更新成功
        boolean isUpdateContentSuccess = false;
        if (StrUtil.isBlank(content)) {
            // 若笔记内容为空，则删除 K-V 存储
            isUpdateContentSuccess = keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 调用 K-V 更新短文本
            isUpdateContentSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
        }

        // 如果更新失败，抛出业务异常，回滚事务
        if (!isUpdateContentSuccess) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        return Response.success();
    }

    @Override
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    /**
     * 删除笔记
     *
     * @param deleteNoteReq
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteNote(DeleteNoteReq deleteNoteReq) {
        // 笔记 ID
        Long noteId = deleteNoteReq.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许删除笔记
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 逻辑删除
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        noteDOMapper.updateByPrimaryKeySelective(noteDO);

        // 删除缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        stringRedisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        // 发送 MQ
        // 构建消息体 DTO
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .type(NoteOperateEnum.DELETE.getCode())
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
                .build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_DELETE;

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记删除】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记删除】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 笔记仅对自己可见
     *
     * @param updateNoteVisibleOnlyMeReq
     * @return
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReq updateNoteVisibleOnlyMeReq) {
        // 笔记 ID
        Long noteId = updateNoteVisibleOnlyMeReq.getId();

        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许修改笔记权限
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);

        // 若影响的行数为 0，则表示该笔记无法修改为仅自己可见
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        stringRedisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    /**
     * 笔记置顶 / 取消置顶
     *
     * @param topNoteReq
     * @return
     */
    @Override
    public Response<?> topNote(TopNoteReq topNoteReq) {
        // 笔记 ID
        Long noteId = topNoteReq.getId();
        // 是否置顶
        Boolean isTop = topNoteReq.getIsTop();

        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();

        // 构建置顶/取消置顶 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .creatorId(currUserId)
                .build();

        int count = noteDOMapper.updateIsTop(noteDO);

        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        stringRedisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }





}
