package org.lexagent.controller.user;

import org.lexagent.model.dto.UserLoginDTO;
import org.lexagent.model.dto.UserRegisterDTO;
import org.lexagent.model.vo.UserLoginVO;
import org.lexagent.result.Result;
import org.lexagent.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import static org.lexagent.constants.MessageConstant.*;

/**
 * 统一登录认证控制器
 */
@Tag(name = "认证管理")
@RestController
@Slf4j
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 统一登录接口（无需修改，保持原有逻辑）
     */
    @PostMapping("/login")
    @Operation(summary = "统一登录接口")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        UserLoginVO loginVO = userService.login(userLoginDTO);
        return loginVO != null ? Result.success("登录成功",loginVO) : Result.error(LOGIN_FAILED);
    }

    /**
     * 退出登录接口
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录接口")
    public Result<String> logout(
            @RequestHeader(value = "token", required = false) String token
    ) {
        // 1. 校验token是否为空
        if (!StringUtils.hasText(token)) {
            return Result.error(TOKEN_NOT_NULL); // 提示“token不能为空”
        }

        // 2.调用Service清理登录态（删除Redis中的token关联）
        userService.logout(token);

        // 3. 返回成功响应
        return Result.success(LOGOUT_SUCCESS);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<UserLoginVO> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        UserLoginVO loginVO = userService.register(registerDTO);
        return loginVO != null ? Result.success(REGISTER_SUCCESS, loginVO) : Result.error(SYSTEM_ERROR);
    }
}