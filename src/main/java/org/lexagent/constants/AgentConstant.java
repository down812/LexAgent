package org.lexagent.constants;

/**
 * Multi-Agent 多智能体协作相关常量
 */
public class AgentConstant {

    // ============ Agent 类型 ============
    public static final String AGENT_CASE_ANALYSIS = "CASE_ANALYSIS";
    public static final String AGENT_LEGAL_SEARCH = "LEGAL_SEARCH";
    public static final String AGENT_LEGAL_ANALYSIS = "LEGAL_ANALYSIS";
    public static final String AGENT_REVIEWER = "REVIEWER";

    // ============ Agent 中文名 ============
    public static final String AGENT_NAME_CASE_ANALYSIS = "案件事实分析 Agent";
    public static final String AGENT_NAME_LEGAL_SEARCH = "法律检索 Agent";
    public static final String AGENT_NAME_LEGAL_ANALYSIS = "法律分析 Agent";
    public static final String AGENT_NAME_REVIEWER = "法律核验 Agent";

    // ============ Agent 任务状态 ============
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_RUNNING = "RUNNING";
    public static final String TASK_SUCCESS = "SUCCESS";
    public static final String TASK_FAILED = "FAILED";

    // ============ 消息角色 ============
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    // ============ 会话状态 ============
    public static final String CONVERSATION_ACTIVE = "ACTIVE";
    public static final String CONVERSATION_ARCHIVED = "ARCHIVED";

    // ============ SSE 事件类型 ============
    public static final String SSE_AGENT_START = "agent_start";
    public static final String SSE_AGENT_RESULT = "agent_result";
    public static final String SSE_CITATION = "citation";
    public static final String SSE_CONTENT = "content";
    public static final String SSE_DONE = "done";
    public static final String SSE_ERROR = "error";

    // ============ 错误消息 ============
    public static final String LLM_CALL_FAILED = "LLM 调用失败，请检查 DeepSeek API Key 配置";
    public static final String AGENT_EXECUTION_FAILED = "Agent 执行失败";
    public static final String CONVERSATION_NOT_EXIST = "会话不存在";
    public static final String MESSAGE_CONTENT_EMPTY = "消息内容不能为空";

    // ============ 最终回答模板 ============
    public static final String RISK_DISCLAIMER = "以上内容用于法律知识科普，不能替代专业法律意见。";

    private AgentConstant() {
    }
}
