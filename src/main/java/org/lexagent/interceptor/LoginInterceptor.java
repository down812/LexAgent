package org.lexagent.interceptor;

import org.lexagent.context.LoginContext;
import org.lexagent.context.LoginContext.LoginUserDTO;
import org.lexagent.exception.PermissionDeniedException;
import org.lexagent.mapper.UserMapper;
import org.lexagent.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.lexagent.constants.CommonConstant.LOGIN_USER_ID;
import static org.lexagent.constants.CommonConstant.LOGIN_USER_TYPE;
import static org.lexagent.constants.RedisConstant.LAST_ACTIVE_REFRESH_KEY;

/**
 * 登录拦截器：基于JWT令牌解析用户信息，存入上下文并做权限校验
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private UserMapper userMapper;

    // 同一用户两次写库的最小间隔（秒）
    private static final long LAST_ACTIVE_REFRESH_SECONDS = 300;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            // 1. 获取 JwtInterceptor 传递的令牌和 claims
            String token = (String) request.getAttribute("validToken");
            Claims claims = (Claims) request.getAttribute("jwtClaims");

            // 如果 claims 为 null，说明是无需拦截的接口，直接放行
            if (claims == null) {
                return true;
            }

            // 2. 从 claims 提取用户信息
            Long userId = claims.get(LOGIN_USER_ID, Long.class);
            Integer userType = claims.get(LOGIN_USER_TYPE, Integer.class);
            if (userId == null || userType == null) {
                sendError(response, 401, "令牌中缺少用户信息");
                return false;
            }

            // 3. 存入线程上下文（供后续业务使用）
            LoginUserDTO userDTO = new LoginUserDTO();
            userDTO.setUserId(userId);
            userDTO.setUserType(userType);
            LoginContext.setUser(userDTO);

            // 3.1 刷新最近活跃时间（Redis 节流，每个用户最多 5 分钟写一次库）
            refreshLastActive(userId);

            // 4. 放行
            return true;
        } catch (PermissionDeniedException e) {
            sendError(response, 403, e.getMessage());
            return false;
        } catch (Exception e) {
            sendError(response, 500, "登录态处理失败：" + e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后，清理线程上下文（避免 ThreadLocal 内存泄漏）
        LoginContext.clear();
    }

    /**
     * 节流刷新最近活跃时间
     * 仅更新 last_active 单列，避免整行覆盖；任何异常均不影响主请求
     * @param userId 当前登录用户ID
     */
    private void refreshLastActive(Long userId) {
        String key = LAST_ACTIVE_REFRESH_KEY + userId;
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", LAST_ACTIVE_REFRESH_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(first)) {
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getLastActive, new Date()));
            }
        } catch (Exception e) {
            log.warn("刷新最近活跃时间失败：userId={}", userId, e);
        }
    }

    // 发送 JSON 格式的错误响应
    private void sendError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.write("{\"code\":" + code + ",\"msg\":\"" + msg + "\"}");
        out.flush();
        out.close();
    }
}