package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 案件证据 VO（EVIDENCE-003）
 * 列表场景不返回 ocr_text 大字段，详情场景包含
 */
@Data
@Schema(description = "案件证据信息")
public class EvidenceVO {

    @Schema(description = "证据ID")
    private Long id;

    @Schema(description = "所属案件ID")
    private Long caseId;

    @Schema(description = "证据编号")
    private String evidenceNo;

    @Schema(description = "证据名称")
    private String evidenceName;

    @Schema(description = "原始文件名称")
    private String originalName;

    @Schema(description = "文件类型(PDF/DOC/DOCX/TXT/IMAGE)")
    private String fileType;

    @Schema(description = "证据分类")
    private String category;

    @Schema(description = "OCR处理状态(WAITING/PROCESSING/SUCCESS/FAILED)")
    private String ocrStatus;

    @Schema(description = "OCR失败原因")
    private String ocrErrorMsg;

    @Schema(description = "证据分析状态")
    private String analysisStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @Schema(description = "OCR/解析识别文本（详情接口返回）")
    private String ocrText;

    @Schema(description = "用户修正后文本（详情接口返回）")
    private String correctedText;
}
