package com.dyc.xiaohashu.id.generator.dto.req;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchGenerateIdReqDTO {

    /**
     * ID 生成器类型。
     */
    @NotNull(message = "ID 生成器类型不能为空")
    private IdGeneratorType type;

    /**
     * 批量获取数量。
     */
    @NotNull(message = "批量获取数量不能为空")
    @Min(value = 1, message = "批量获取数量必须大于等于 1")
    @Max(value = 1000, message = "批量获取数量必须小于等于 1000")
    private Integer size;
}
