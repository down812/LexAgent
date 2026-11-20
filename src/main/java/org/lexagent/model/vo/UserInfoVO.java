package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoVO {

    @Schema(description = "用户ID", required = true)
    private Long userId;

    @Schema(description = "用户账号/临时标识", required = true)
    private String identifier;

    @Schema(description = "用户联系电话")
    private String phone;

    @Schema(description = "用户联系邮箱")
    private String email;

    @Schema(description = "用户类型（0-超级管理员，1-普通管理员，2-普通用户）", required = true)
    private Integer type;

    @Schema(description = "用户类型描述")
    private String typeName;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @Schema(description = "最近活跃时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastActive;
}
