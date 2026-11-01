package com.dyc.xiaohashu.auth.dto.req;

import com.dyc.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendCodeReq {

    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;

}
