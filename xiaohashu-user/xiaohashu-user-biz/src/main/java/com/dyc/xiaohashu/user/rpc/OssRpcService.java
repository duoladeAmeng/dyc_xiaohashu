package com.dyc.xiaohashu.user.rpc;

import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.oss.api.FileFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OssRpcService {

    @Resource
    private FileFeignApi fileFeignApi;

    public String uploadFile(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadFile(file);
        if (response == null || !response.isSuccess()) {
            return null;
        }
        return (String) response.getData();
    }
}
