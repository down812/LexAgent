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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.lexagent.model.dto.ChecklistCreateDTO;
import org.lexagent.model.dto.ChecklistRelationDTO;
import org.lexagent.model.dto.ChecklistUpdateDTO;
import org.lexagent.model.vo.EvidenceChecklistVO;
import org.lexagent.result.Result;
import org.lexagent.service.EvidenceChecklistService;

import java.util.List;

/**
 * 智能证据清单接口（V2.0 阶段四 / LIST-001~004）
 * 清单生成 / 查询 / 新增 / 修改 / 删除 / 证据关联
 * 所有接口均需登录，数据权限：普通用户仅能访问自己案件的清单
 */
@Tag(name = "智能证据清单")
@RestController
@Slf4j
@RequestMapping("/evidence-checklist")
public class EvidenceChecklistController {

    private final EvidenceChecklistService evidenceChecklistService;

    public EvidenceChecklistController(EvidenceChecklistService evidenceChecklistService) {
        this.evidenceChecklistService = evidenceChecklistService;
    }

    @PostMapping("/generate")
    @Operation(summary = "生成证据清单（LIST-001）",
            description = "结合案件事实、已有证据、证据分析结果与法律检索生成个性化清单（LLM，同步返回）；同名项目跳过以保留已有成果")
    public Result<List<EvidenceChecklistVO>> generate(@RequestParam Long caseId) {
        return evidenceChecklistService.generate(caseId);
    }

    @PostMapping("/create")
    @Operation(summary = "手动新增清单项目（LIST-004）")
    public Result<EvidenceChecklistVO> createItem(@Validated @RequestBody ChecklistCreateDTO dto) {
        return Result.success(evidenceChecklistService.createItem(dto));
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "查询证据清单（含关联证据与推荐关联候选）",
            description = "推荐关联基于名称相似度生成，仅作建议，由用户确认（LIST-002）")
    public Result<List<EvidenceChecklistVO>> getByCase(@PathVariable Long caseId) {
        return Result.success(evidenceChecklistService.getByCase(caseId));
    }

    @PutMapping("/update")
    @Operation(summary = "修改清单项目（LIST-004）", description = "修改名称/用途/状态/关联事实/备注，留空不修改")
    public Result<EvidenceChecklistVO> updateItem(@Validated @RequestBody ChecklistUpdateDTO dto) {
        return Result.success(evidenceChecklistService.updateItem(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除清单项目（LIST-004）", description = "级联删除关联关系")
    public Result<Long> deleteItem(@PathVariable Long id) {
        return Result.success(evidenceChecklistService.deleteItem(id));
    }

    @PostMapping("/relation")
    @Operation(summary = "关联实际证据（LIST-002）",
            description = "证据必须属于同一案件；REQUIRED 状态自动置为 UPLOADED")
    public Result<EvidenceChecklistVO> relateEvidence(@Validated @RequestBody ChecklistRelationDTO dto) {
        return Result.success(evidenceChecklistService.relateEvidence(dto.getChecklistId(), dto.getEvidenceId()));
    }

    @DeleteMapping("/relation")
    @Operation(summary = "取消关联实际证据（LIST-002）",
            description = "取消后若无其他关联，UPLOADED 状态自动回退为 REQUIRED")
    public Result<EvidenceChecklistVO> unrelateEvidence(
            @RequestParam Long checklistId,
            @RequestParam Long evidenceId) {
        return Result.success(evidenceChecklistService.unrelateEvidence(checklistId, evidenceId));
    }
}
