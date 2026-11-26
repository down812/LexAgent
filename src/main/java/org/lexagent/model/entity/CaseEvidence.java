package org.lexagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 案件证据实体（V2.0）
 * 对应 case_evidence 表：案件证据材料（合同/聊天截图/转账记录等）
 * OCR 识别文本与用户修正文本分开保存，不允许修正文本覆盖原始文件
 */
@TableName(value = "case_evidence")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseEvidence implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Schema(description = "证据ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属案件ID")
    @TableField(value = "case_id")
    private Long caseId;

    @Schema(description = "上传用户ID")
    @TableField(value = "user_id")
    private Long userId;

    @Schema(description = "证据编号（EV+日期+ID）")
    @TableField(value = "evidence_no")
    private String evidenceNo;

    @Schema(description = "证据名称")
    @TableField(value = "evidence_name")
    private String evidenceName;

    @Schema(description = "原始文件名称")
    @TableField(value = "original_name")
    private String originalName;

    @Schema(description = "文件类型(PDF/DOC/DOCX/TXT/IMAGE)")
    @TableField(value = "file_type")
    private String fileType;

    @Schema(description = "文件存储路径")
    @TableField(value = "file_path")
    private String filePath;

    @Schema(description = "证据分类（身份证明/合同协议/财务凭证/沟通记录/书面材料/影像资料/其他材料）")
    @TableField(value = "category")
    private String category;

    @Schema(description = "OCR/解析识别文本")
    @TableField(value = "ocr_text")
    private String ocrText;

    @Schema(description = "用户修正后文本")
    @TableField(value = "corrected_text")
    private String correctedText;

    @Schema(description = "OCR处理状态(WAITING/PROCESSING/SUCCESS/FAILED)")
    @TableField(value = "ocr_status")
    private String ocrStatus;

    @Schema(description = "OCR识别失败原因")
    @TableField(value = "ocr_error_msg")
    private String ocrErrorMsg;

    @Schema(description = "证据分析状态（阶段三 Evidence Analysis Agent）")
    @TableField(value = "analysis_status")
    private String analysisStatus;

    @Schema(description = "备注")
    @TableField(value = "remark")
    private String remark;

    @Schema(description = "上传时间")
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
