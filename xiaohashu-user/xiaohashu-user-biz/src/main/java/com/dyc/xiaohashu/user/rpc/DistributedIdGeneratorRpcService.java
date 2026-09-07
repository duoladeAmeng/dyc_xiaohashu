package com.dyc.xiaohashu.user.rpc;

import com.dyc.xiaohashu.user.id.UserIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private UserIdGenerator userIdGenerator;

    public String getXiaohashuId() {
        return userIdGenerator.nextXiaohashuId();
    }

    public String getUserId() {
        return String.valueOf(userIdGenerator.nextUserId());
    }
}
