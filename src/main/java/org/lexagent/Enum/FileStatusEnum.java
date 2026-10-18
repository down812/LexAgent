package org.lexagent.Enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件处理状态枚举
 */
@Getter
@AllArgsConstructor
public enum FileStatusEnum {
    WAITING("WAITING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "处理成功"),
    FAILED("FAILED", "处理失败");

    private final String code;
    private final String desc;
}
