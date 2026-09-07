package com.dyc.xiaohashu.id.generator.dto.req;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class BatchGenerateIdReqDTO {

    @NotNull(message = "ID 生成器类型不能为空")
    private IdGeneratorType type;

    @Min(value = 1, message = "批量生成数量不能小于 1")
    @Max(value = 1000, message = "批量生成数量不能大于 1000")
    private int size;

    public IdGeneratorType getType() {
        return type;
    }

    public void setType(IdGeneratorType type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
