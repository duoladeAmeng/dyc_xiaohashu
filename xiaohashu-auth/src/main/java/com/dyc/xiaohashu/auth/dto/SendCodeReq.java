package com.dyc.xiaohashu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeReq {

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
