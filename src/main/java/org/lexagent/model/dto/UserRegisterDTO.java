package org.lexagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", required = true)
    private String identifier;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$", message = "密码需8-16位，包含字母和数字")
    @Schema(description = "密码（8-16位，包含字母和数字）", required = true)
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$", message = "邮箱格式不正确")
    @Schema(description = "联系邮箱", required = true)
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "邮箱验证码", required = true)
    private String code;
}
