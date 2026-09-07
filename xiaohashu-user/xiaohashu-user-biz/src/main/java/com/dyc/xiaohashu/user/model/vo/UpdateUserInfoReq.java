package com.dyc.xiaohashu.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserInfoReq {

    private MultipartFile avatar;
    private String nickname;
    private String xiaohashuId;
    private Integer sex;
    private LocalDate birthday;
    private String introduction;
    private MultipartFile backgroundImg;
}
