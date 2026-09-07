package com.dyc.xiaohashu.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserProfileRsp {

    private Long userId;
    private String avatar;
    private String nickname;
    private String xiaohashuId;
    private Integer sex;
    private Integer age;
    private LocalDate birthday;
    private String introduction;
    @Builder.Default
    private String followingTotal = "0";
    @Builder.Default
    private String fansTotal = "0";
    @Builder.Default
    private String likeAndCollectTotal = "0";
}
