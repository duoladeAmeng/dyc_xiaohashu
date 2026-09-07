package com.dyc.xiaohashu.id.generator.dto.req;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.validation.constraints.NotNull;

public class GenerateIdReqDTO {

    @NotNull(message = "ID 生成器类型不能为空")
    private IdGeneratorType type;

    public IdGeneratorType getType() {
        return type;
    }

    public void setType(IdGeneratorType type) {
        this.type = type;
    }
}
