package com.dyc.xiaohashu.id.generator.dto.req;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateIdReqDTO {

    /**
     * ID 生成器类型。
     */
    @NotNull(message = "ID 生成器类型不能为空")
    private IdGeneratorType type;
}
