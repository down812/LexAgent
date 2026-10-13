package org.lexagent.controller.user;

import org.lexagent.Enum.UserEnum;
import org.lexagent.context.LoginContext;
import org.lexagent.exception.PermissionDeniedException;
import org.lexagent.model.dto.SubaccountCreateDTO;
import org.lexagent.model.dto.SubaccountQueryDTO;
import org.lexagent.model.dto.SubaccountUpdateDTO;
import org.lexagent.model.vo.SubaccountVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.SubaccountService;
import org.lexagent.service.UserService;
import org.lexagent.utils.MailUtils;
import org.lexagent.utils.VerCodeGenerateUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static org.lexagent.constants.MessageConstant.*;

/**
 * 用户相关接口控制器
 */
@Tag(name = "用户管理")
@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private SubaccountService subaccountService;

    @Autowired
    private MailUtils mailUtils;

    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/code")
    public Result<String> sendEmailCode(
            @NotBlank(message = "邮箱不能为空")
            @RequestParam String email) {
        boolean success = mailUtils.sendEmailCode(email,VerCodeGenerateUtil.getVerCode());
        if (success) {
            return Result.success("验证码已发送，请注意查收（5分钟内有效）");
        } else {
            return Result.error("验证码发送失败，请稍后重试");
        }
    }

    @PostMapping("/subaccount")
    @Operation(summary = "创建子账号", description = "仅超级管理员（type=0）可调用")
    public Result<?> createSubaccount(
            @Validated @RequestBody SubaccountCreateDTO createDTO
    ) {
        Integer currentUserType = LoginContext.getUserType();
        if (currentUserType == null || !currentUserType.equals(UserEnum.SUPER_ADMIN.getCode())) {
            throw new PermissionDeniedException(NO_PERMISSION_SUPER_ADMIN_REQUIRED);
        }
        return subaccountService.createSubaccount(createDTO);
    }


    @PostMapping("/subaccountList")
    @Operation(summary = "查询子账号列表", description = "分页查询，支持按账号、类型筛选；仅超级管理员（type=0）可调用")
    public Result<PageResult<SubaccountVO>> querySubaccounts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestBody(required = false) SubaccountQueryDTO queryDTO
    ) {
        Integer currentUserType = LoginContext.getUserType();
        if (currentUserType == null || !currentUserType.equals(UserEnum.SUPER_ADMIN.getCode())) {
            throw new PermissionDeniedException(NO_PERMISSION_SUPER_ADMIN_REQUIRED);
        }
        PageResult<SubaccountVO> pageResult = subaccountService.querySubaccounts(pageNum, pageSize, queryDTO);
        return Result.success(pageResult);
    }

    @PutMapping("/subaccount/{subaccountId}")
    @Operation(summary = "修改子账号", description = "支持修改密码、类型；仅超级管理员（type=0）可调用")
    public Result<SubaccountVO> updateSubaccount(
            @PathVariable Integer subaccountId,
            @Validated @RequestBody SubaccountUpdateDTO updateDTO
    ) {
        Integer currentUserType = LoginContext.getUserType();
        if (currentUserType == null || !currentUserType.equals(UserEnum.SUPER_ADMIN.getCode())) {
            throw new PermissionDeniedException(NO_PERMISSION_SUPER_ADMIN_REQUIRED);
        }
        SubaccountVO subaccountVO = subaccountService.updateSubaccount(subaccountId, updateDTO);
        return Result.success(subaccountVO);
    }


    @DeleteMapping("/subaccount/{subaccountId}")
    @Operation(summary = "删除子账号", description = "仅超级管理员（type=0）可调用")
    public Result<?> deleteSubaccount(
            @PathVariable Integer subaccountId
    ) {
        Integer currentUserType = LoginContext.getUserType();
        if (currentUserType == null || !currentUserType.equals(UserEnum.SUPER_ADMIN.getCode())) {
            throw new PermissionDeniedException(NO_PERMISSION_SUPER_ADMIN_REQUIRED);
        }
        Integer deletedId = subaccountService.deleteSubaccount(subaccountId);
        return Result.success(DELETE_SUBACCOUNT_ERROR, deletedId);
    }
}