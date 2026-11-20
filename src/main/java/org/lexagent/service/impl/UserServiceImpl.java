package org.lexagent.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lexagent.config.JwtConfig;
import org.lexagent.Enum.UserEnum;
import org.lexagent.exception.AuthFailException;
import org.lexagent.exception.BusinessException;
import org.lexagent.exception.BusinessNotFoundException;
import org.lexagent.exception.LoginFailException;
import org.lexagent.mapper.UserMapper;
import org.lexagent.model.dto.RedisUserMsgDTO;
import org.lexagent.model.dto.UserAuthDTO;
import org.lexagent.model.dto.UserInfoUpdateDTO;
import org.lexagent.model.dto.UserLoginDTO;
import org.lexagent.model.dto.UserRegisterDTO;
import org.lexagent.model.entity.User;
import org.lexagent.model.vo.UserAuthVO;
import org.lexagent.model.vo.UserInfoVO;
import org.lexagent.model.vo.UserLoginVO;
import org.lexagent.service.UserService;
import org.lexagent.utils.JwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.lexagent.constants.CommonConstant.*;
import static org.lexagent.constants.MessageConstant.*;
import static org.lexagent.constants.RedisConstant.EMAIL_CODE_KEY;
import static org.lexagent.constants.RedisConstant.LOGIN_USER_TOKEN;



@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private JwtConfig jwtConfig;



    /**
     * 统一登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {

        // 1. 查询用户（原有逻辑）
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("identifier", userLoginDTO.getIdentifier())
        );
        if (user == null) {
            throw new LoginFailException(USER_NOT_EXIST);
        }

        // 2. 密码校验（原有逻辑）
        String encryptedPwd = DigestUtils.md5DigestAsHex((SALT + userLoginDTO.getPassword()).getBytes());
        if (!encryptedPwd.equals(user.getPassword())) {
            throw new LoginFailException(PASSWORD_ERROR);
        }

        // 2.1 登录即视为一次活跃（只更新 last_active 单列，避免整行覆盖）
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLastActive, new Date()));

        // 3. 生成JWT令牌（使用统一方法，与游客令牌格式一致）
        Map<String, Object> claims = new HashMap<>();
        claims.put(LOGIN_USER_ID, user.getId());
        claims.put(LOGIN_USER_TYPE, user.getType());
        Map<String, Object> header = new HashMap<>();
        header.put("type", "jwt");
        String token = jwtUtil.createTokenWithHeader(claims, header); // 带自定义头部

        // 4. 存储到Redis（与游客逻辑一致）
        RedisUserMsgDTO redisUserMsgDTO = new RedisUserMsgDTO();
        BeanUtils.copyProperties(user, redisUserMsgDTO);
        redisUserMsgDTO.setType(user.getType());

        String msgKey = LOGIN_USER_TOKEN + token;
        redisTemplate.opsForValue().set(
                msgKey,
                JSONUtil.toJsonStr(redisUserMsgDTO),
                jwtConfig.getUserTtlMillis() / 1000,
                TimeUnit.SECONDS
        );

        String tokenKey = LOGIN_USER_TOKEN + user.getId();
        redisTemplate.opsForValue().set(
                tokenKey,
                token,
                jwtConfig.getUserTtlMillis() / 1000,
                TimeUnit.SECONDS
        );

        // 5. 返回结果
        return UserLoginVO.builder()
                .token(token)
                .userId(user.getId())
                .type(user.getType())
                .identifier(user.getIdentifier())
                .build();
    }


    /**
     * 查询用户信息
     * @param userId 用户ID
     * @return 用户信息（不含密码等敏感字段）
     */
    @Override
    public UserInfoVO getUserInfo(Long userId) {
        // 1. 校验用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessNotFoundException(USER_NOT_EXIST);
        }

        // 2. 类型码转描述，便于前端直接展示
        UserEnum userEnum = Arrays.stream(UserEnum.values())
                .filter(e -> e.getCode().equals(user.getType()))
                .findFirst()
                .orElse(null);

        // 3. 显式挑字段组装 VO（不复制 password）
        return UserInfoVO.builder()
                .userId(user.getId())
                .identifier(user.getIdentifier())
                .phone(user.getPhone())
                .email(user.getEmail())
                .type(user.getType())
                .typeName(userEnum == null ? null : userEnum.getDesc())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastActive(user.getLastActive())
                .build();
    }

    /**
     * 更新用户信息（账号/密码/邮箱/电话）
     * 仅更新非空字段，留空字段保持不变
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateUserUserInfo(Long userId, UserInfoUpdateDTO updateDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessNotFoundException(USER_NOT_EXIST);
        }

        boolean changed = false;

        // 1. 账号：变更时校验唯一性（排除自身）
        String newIdentifier = updateDTO.getIdentifier();
        if (StringUtils.isNotBlank(newIdentifier) && !newIdentifier.equals(user.getIdentifier())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getIdentifier, newIdentifier)
                    .ne(User::getId, userId));
            if (count > 0) {
                throw new BusinessException(NEW_IDENTIFIER_ALREADY_TAKEN);
            }
            user.setIdentifier(newIdentifier);
            changed = true;
        }

        // 2. 密码：加密后更新
        String newPassword = updateDTO.getPassword();
        if (StringUtils.isNotBlank(newPassword)) {
            user.setPassword(DigestUtils.md5DigestAsHex((SALT + newPassword).getBytes()));
            changed = true;
        }

        // 3. 邮箱：变更时校验唯一性（排除自身）
        String newEmail = updateDTO.getEmail();
        if (StringUtils.isNotBlank(newEmail) && !newEmail.equals(user.getEmail())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, newEmail)
                    .ne(User::getId, userId));
            if (count > 0) {
                throw new BusinessException(EMAIL_ALREADY_LINKED);
            }
            user.setEmail(newEmail);
            changed = true;
        }

        // 4. 电话：变更时校验唯一性（排除自身）
        String newPhone = updateDTO.getPhone();
        if (StringUtils.isNotBlank(newPhone) && !newPhone.equals(user.getPhone())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, newPhone)
                    .ne(User::getId, userId));
            if (count > 0) {
                throw new BusinessException(PHONE_ALREADY_LINKED);
            }
            user.setPhone(newPhone);
            changed = true;
        }

        // 5. 无字段变更时直接返回，避免无意义写库
        if (!changed) {
            return getUserInfo(userId);
        }

        // 6. 维护更新时间并落库
        user.setUpdatedAt(new Date());
        if (!this.updateById(user)) {
            throw new BusinessException(USER_MESSAGE_UPDATE_ERROR);
        }
        return getUserInfo(userId);
    }

    /**
     * User info auth: registered users modify their info
     * @param userAuthDTO auth info
     * @return updated user info
     */
    @Override
    @Transactional
    public UserAuthVO authUser(UserAuthDTO userAuthDTO) {
        if (ObjectUtil.isEmpty(userAuthDTO) ||
                StringUtils.isBlank(userAuthDTO.getOldIdentifier()) ||
                StringUtils.isBlank(userAuthDTO.getNewIdentifier()) ||
                StringUtils.isBlank(userAuthDTO.getEmail()) ||
                StringUtils.isBlank(userAuthDTO.getPassword()) ||
                StringUtils.isBlank(userAuthDTO.getCode())) {
            throw new AuthFailException(PARAM_NOT_COMPLETE);
        }

        String email = userAuthDTO.getEmail();
        String inputCode = userAuthDTO.getCode();
        String redisKey = EMAIL_CODE_KEY + email;
        String cachedCode = redisTemplate.opsForValue().get(redisKey);

        if (StringUtils.isBlank(cachedCode) || !cachedCode.equals(inputCode)) {
            throw new AuthFailException("Code error or expired");
        }

        String oldIdentifier = userAuthDTO.getOldIdentifier();
        String newIdentifier = userAuthDTO.getNewIdentifier();
        Date currentTime = new Date();

        User dbUser = this.lambdaQuery()
                .eq(User::getIdentifier, oldIdentifier)
                .one();

        if (ObjectUtil.isEmpty(dbUser)) {
            throw new AuthFailException(USER_NOT_EXIST);
        }

        if (dbUser.getType() != 0 && dbUser.getType() != 1 && dbUser.getType() != 2) {
            throw new AuthFailException("无效的用户类型");
        }

        User existUser = this.lambdaQuery()
                .eq(User::getIdentifier, newIdentifier)
                .ne(User::getId, dbUser.getId())
                .one();
        if (ObjectUtil.isNotEmpty(existUser)) {
            throw new AuthFailException(NEW_IDENTIFIER_ALREADY_TAKEN);
        }

        User emailUser = this.lambdaQuery()
                .eq(User::getEmail, email)
                .ne(User::getId, dbUser.getId())
                .one();
        if (ObjectUtil.isNotEmpty(emailUser)) {
            throw new AuthFailException(EMAIL_ALREADY_LINKED);
        }

        String encryptedPwd = DigestUtils.md5DigestAsHex((SALT + userAuthDTO.getPassword()).getBytes());
        dbUser.setIdentifier(newIdentifier);
        dbUser.setEmail(email);
        dbUser.setPassword(encryptedPwd);
        dbUser.setUpdatedAt(currentTime);
        dbUser.setLastActive(currentTime);
        this.updateById(dbUser);

        return UserAuthVO.builder()
                .userId(dbUser.getId())
                .identifier(dbUser.getIdentifier())
                .type(dbUser.getType())
                .lastActive(dbUser.getLastActive())
                .needLogin(false)
                .build();
    }





    /**
     * 退出登录核心逻辑：清理 Redis 中的登录态缓存
     * @param token 用户登录时的令牌
     */
    @Override
    public void logout(String token) {
        // 1. 定义缓存键（与登录时一致）
        String userInfoKey = LOGIN_USER_TOKEN + token;

        // 2. 获取并解析用户信息，清理 userId 与 token 的映射
        String userInfoJson = redisTemplate.opsForValue().get(userInfoKey);
        if (userInfoJson != null) {
            // 直接解析并删除映射关系
            Long userId = JSONUtil.toBean(userInfoJson, RedisUserMsgDTO.class).getId();
            redisTemplate.delete(LOGIN_USER_TOKEN + userId);
        }

        // 3. 清除 token 对应的用户信息缓存
        redisTemplate.delete(userInfoKey);
    }

    @Override
    @Transactional
    public UserLoginVO register(UserRegisterDTO userRegisterDTO) {
        String email = userRegisterDTO.getEmail();
        String inputCode = userRegisterDTO.getCode();
        String redisKey = EMAIL_CODE_KEY + email;
        String cachedCode = redisTemplate.opsForValue().get(redisKey);

        if (StringUtils.isBlank(cachedCode) || !cachedCode.equals(inputCode)) {
            throw new AuthFailException(CODE_ERROR_OR_EXPIRED);
        }

        String identifier = userRegisterDTO.getIdentifier();
        User existUser = this.lambdaQuery()
                .eq(User::getIdentifier, identifier)
                .one();
        if (ObjectUtil.isNotEmpty(existUser)) {
            throw new AuthFailException(IDENTIFIER_ALREADY_EXIST);
        }

        User emailUser = this.lambdaQuery()
                .eq(User::getEmail, email)
                .one();
        if (ObjectUtil.isNotEmpty(emailUser)) {
            throw new AuthFailException(EMAIL_ALREADY_REGISTERED);
        }

        String encryptedPwd = DigestUtils.md5DigestAsHex((SALT + userRegisterDTO.getPassword()).getBytes());
        Date currentTime = new Date();

        User newUser = User.builder()
                .identifier(identifier)
                .password(encryptedPwd)
                .email(email)
                .type(UserEnum.USER.getCode())
                .createdAt(currentTime)
                .updatedAt(currentTime)
                .lastActive(currentTime)
                .build();
        userMapper.insert(newUser);

        redisTemplate.delete(redisKey);

        Map<String, Object> claims = new HashMap<>();
        claims.put(LOGIN_USER_ID, newUser.getId());
        claims.put(LOGIN_USER_TYPE, newUser.getType());
        Map<String, Object> header = new HashMap<>();
        header.put("type", "jwt");
        String token = jwtUtil.createTokenWithHeader(claims, header);

        RedisUserMsgDTO redisUserMsgDTO = new RedisUserMsgDTO();
        BeanUtils.copyProperties(newUser, redisUserMsgDTO);
        redisUserMsgDTO.setType(newUser.getType());

        String msgKey = LOGIN_USER_TOKEN + token;
        redisTemplate.opsForValue().set(
                msgKey,
                JSONUtil.toJsonStr(redisUserMsgDTO),
                jwtConfig.getUserTtlMillis() / 1000,
                TimeUnit.SECONDS
        );

        String tokenKey = LOGIN_USER_TOKEN + newUser.getId();
        redisTemplate.opsForValue().set(
                tokenKey,
                token,
                jwtConfig.getUserTtlMillis() / 1000,
                TimeUnit.SECONDS
        );

        return UserLoginVO.builder()
                .token(token)
                .userId(newUser.getId())
                .type(newUser.getType())
                .identifier(newUser.getIdentifier())
                .build();
    }

}