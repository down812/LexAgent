package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息更新 DTO
 * 用于 PUT /user/info 接口，普通用户更新自己的联系方式（邮箱/电话）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoUpdateDTO {

    @Schema(description = "用户账号（可选，留空不修改）")
    private String identifier;

    @Schema(description = "用户密码（可选，留空不修改）")
    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$",
            message = "密码需8-16位，包含字母和数字")
    private String password;

    @Pattern(regexp = "^$|^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$",
            message = "邮箱格式不正确")
    @Schema(description = "联系邮箱（可选，留空不修改）")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系电话（可选，留空不修改）")
    private String phone;
}