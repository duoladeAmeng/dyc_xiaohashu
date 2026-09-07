package com.dyc.xiaohashu.id.generator.dto.resp;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchGenerateIdRspDTO {

    /**
     * ID 生成器类型。
     */
    private IdGeneratorType type;

    /**
     * 实际返回数量。
     */
    private Integer size;

    /**
     * ID 列表。
     */
    private List<Long> ids;
}
