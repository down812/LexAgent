package org.lexagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lexagent.model.dto.UserAuthDTO;
import org.lexagent.model.dto.UserInfoUpdateDTO;
import org.lexagent.model.dto.UserLoginDTO;
import org.lexagent.model.dto.UserRegisterDTO;
import org.lexagent.model.entity.User;
import org.lexagent.model.vo.UserAuthVO;
import org.lexagent.model.vo.UserInfoVO;
import org.lexagent.model.vo.UserLoginVO;

/**
 * 用户信息授权服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户统一登录
     * @param userLoginDTO
     * @return
     */
    UserLoginVO login(UserLoginDTO userLoginDTO);



    /**
     * 用户信息授权
     * 将未授权用户（type=2）转换为已登录用户，完善用户信息
     * @param userAuthDTO 授权信息
     * @return 授权后的用户信息
     */
    UserAuthVO authUser(UserAuthDTO userAuthDTO);



    /**
     * 退出登录
     * @param token 用户登录时的令牌
     */
    void logout(String token);

    UserLoginVO register(UserRegisterDTO userRegisterDTO);

    /**
     * 查询用户信息（不含密码等敏感字段）
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfoVO getUserInfo(Long userId);

    /**
     * 更新用户信息（邮箱/电话）
     * @param userId 用户ID
     * @param updateDTO 待更新字段（留空不修改）
     * @return 更新后的用户信息
     */
    UserInfoVO updateUserUserInfo(Long userId, UserInfoUpdateDTO updateDTO);
}