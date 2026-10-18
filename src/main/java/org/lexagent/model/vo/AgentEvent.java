package org.lexagent.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件载体
 * <p>
 * 对应需求文档第11章事件类型：
 * agent_start / agent_result / citation / content / done / error
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEvent {

    @Schema(description = "事件类型：agent_start/agent_result/citation/content/done/error")
    private String event;

    @Schema(description = "Agent类型(CASE_ANALYSIS/LEGAL_SEARCH/LEGAL_ANALYSIS/REVIEWER)")
    private String agentType;

    @Schema(description = "Agent中文名")
    private String agentName;

    @Schema(description = "事件数据（各阶段不同结构）")
    private Object data;

    @Schema(description = "提示消息（如'案件分析完成'）")
    private String message;

    @Schema(description = "时间戳")
    private Long timestamp;

    public static AgentEvent of(String event, String agentType, String agentName, Object data, String message) {
        return AgentEvent.builder()
                .event(event)
                .agentType(agentType)
                .agentName(agentName)
                .data(data)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
