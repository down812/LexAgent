package org.lexagent.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR 识别结果 VO
 * <p>
 * 用户上传图片/PDF后，返回识别出的文字内容，供用户审阅后再提交问诊。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrResultVO {

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件类型(IMAGE/PDF)")
    private String fileType;

    @Schema(description = "OCR 识别出的文字内容")
    private String text;

    @Schema(description = "识别文字字符数")
    private Integer charCount;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "失败原因（success=false时填充）")
    private String errorMsg;
}
