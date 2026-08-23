package com.dyc.xiaohashu.oss.biz.service.impl;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.oss.biz.service.FileService;
import com.dyc.xiaohashu.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RefreshScope
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;

    @Value("${storage.bucket-name}")
    private String BUCKET_NAME;

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 上传文件
        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Response.success(url);
    }
}
