package org.lexagent.controller.casebox;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.lexagent.model.dto.CaseCreateDTO;
import org.lexagent.model.dto.CaseFactCreateDTO;
import org.lexagent.model.dto.CaseQueryDTO;
import org.lexagent.model.dto.CaseUpdateDTO;
import org.lexagent.model.vo.CaseConversationVO;
import org.lexagent.model.vo.CaseFactVO;
import org.lexagent.model.vo.CaseVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.CaseService;

import java.util.List;

/**
 * 案件管理接口（V2.0 第三章）
 * 案件创建/分页/详情/编辑/归档删除/案件事实/会话关联
 * 所有接口均需登录，数据权限：普通用户仅能访问自己的案件
 */
@Tag(name = "案件管理")
@RestController
@Slf4j
@RequestMapping("/case")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    // ===================== 案件 CRUD =====================

    @PostMapping("/create")
    @Operation(summary = "创建案件（CASE-001）", description = "填写案件基本信息，生成案件编号")
    public Result<CaseVO> createCase(@Validated @RequestBody CaseCreateDTO dto) {
        return caseService.createCase(dto);
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询案件列表（CASE-002）",
            description = "支持按名称查询/类型筛选/状态筛选/时间排序；普通用户仅返回自己的案件")
    public Result<PageResult<CaseVO>> queryCases(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") Integer pageNum,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") Integer pageSize,
            @RequestBody(required = false) CaseQueryDTO queryDTO) {
        return Result.success(caseService.queryCases(pageNum, pageSize, queryDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询案件详情（CASE-003）", description = "含证据数量与关联会话数量")
    public Result<CaseVO> getCase(@PathVariable Long id) {
        return Result.success(caseService.getCase(id));
    }

    @PutMapping("/update")
    @Operation(summary = "修改案件（CASE-004）", description = "修改名称/类型/描述/发生时间/地点/备注，留空字段不修改")
    public Result<CaseVO> updateCase(@Validated @RequestBody CaseUpdateDTO dto) {
        return Result.success(caseService.updateCase(dto));
    }

    @DeleteMapping("/{id}/archive")
    @Operation(summary = "归档案件（CASE-005）", description = "归档后不再显示在默认进行中列表，历史数据全部保留")
    public Result<Long> archiveCase(@PathVariable Long id) {
        return Result.success(caseService.archiveCase(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除案件", description = "级联删除案件事实/会话关联/证据记录及证据文件，请谨慎操作")
    public Result<Long> deleteCase(@PathVariable Long id) {
        return Result.success(caseService.deleteCase(id));
    }

    // ===================== 案件事实 =====================

    @GetMapping("/{id}/facts")
    @Operation(summary = "查询案件事实", description = "返回案件全部事实记录（含来源与确认状态）")
    public Result<List<CaseFactVO>> getFacts(@PathVariable Long id) {
        return Result.success(caseService.getFacts(id));
    }

    @PostMapping("/{id}/fact")
    @Operation(summary = "新增案件事实", description = "用户手动补充事实，来源为 USER_INPUT，视为已确认")
    public Result<CaseFactVO> addFact(@PathVariable Long id,
                                      @Validated @RequestBody CaseFactCreateDTO dto) {
        return caseService.addFact(id, dto);
    }

    @DeleteMapping("/fact/{factId}")
    @Operation(summary = "删除案件事实")
    public Result<Long> deleteFact(@PathVariable Long factId) {
        return Result.success(caseService.deleteFact(factId));
    }

    // ===================== 案件-问诊会话关联 =====================

    @GetMapping("/{id}/conversations")
    @Operation(summary = "查询案件问诊记录（CASE-006）", description = "返回案件关联的全部问诊会话")
    public Result<List<CaseConversationVO>> getConversations(@PathVariable Long id) {
        return Result.success(caseService.getConversations(id));
    }

    @PostMapping("/{id}/conversation/{conversationId}")
    @Operation(summary = "关联问诊会话", description = "将已有会话与当前案件关联（问诊页面案件上下文升级的前置接口）")
    public Result<Long> linkConversation(@PathVariable Long id, @PathVariable Long conversationId) {
        return Result.success(caseService.linkConversation(id, conversationId));
    }
}
