package org.lexagent.constants;

/**
 * V2.0 案件管理模块常量
 * 案件状态 / 案件事实来源 / 证据分类 / OCR 状态等
 */
public final class CaseConstant {

    private CaseConstant() {
    }

    // ===================== 案件状态 =====================
    public static final String CASE_STATUS_ACTIVE = "ACTIVE";         // 进行中
    public static final String CASE_STATUS_PENDING = "PENDING";       // 待补充信息
    public static final String CASE_STATUS_COMPLETED = "COMPLETED";   // 已完成
    public static final String CASE_STATUS_ARCHIVED = "ARCHIVED";     // 已归档

    // ===================== 案件事实来源 =====================
    public static final String FACT_SOURCE_USER_INPUT = "USER_INPUT";         // 用户输入
    public static final String FACT_SOURCE_OCR_EXTRACTION = "OCR_EXTRACTION"; // OCR 识别提取
    public static final String FACT_SOURCE_AI_ANALYSIS = "AI_ANALYSIS";       // AI 分析提取
    public static final String FACT_SOURCE_USER_CORRECTION = "USER_CORRECTION"; // 用户修正

    // ===================== 证据分类 =====================
    public static final String EVIDENCE_CATEGORY_IDENTITY = "身份证明";
    public static final String EVIDENCE_CATEGORY_CONTRACT = "合同协议";
    public static final String EVIDENCE_CATEGORY_FINANCE = "财务凭证";
    public static final String EVIDENCE_CATEGORY_COMMUNICATION = "沟通记录";
    public static final String EVIDENCE_CATEGORY_DOCUMENT = "书面材料";
    public static final String EVIDENCE_CATEGORY_MEDIA = "影像资料";
    public static final String EVIDENCE_CATEGORY_OTHER = "其他材料";

    public static final String DEFAULT_EVIDENCE_CATEGORY = EVIDENCE_CATEGORY_OTHER;

    // ===================== 错误信息 =====================
    public static final String CASE_NOT_EXIST = "案件不存在或无权访问";
    public static final String CASE_NAME_REQUIRED = "案件名称不能为空";
    public static final String CASE_TYPE_REQUIRED = "案件类型不能为空";
    public static final String CASE_DESC_REQUIRED = "案件描述不能为空";
    public static final String FACT_CONTENT_REQUIRED = "事实内容不能为空";
    public static final String CONVERSATION_NOT_EXIST = "问诊会话不存在或无权访问";
    public static final String CONVERSATION_ALREADY_LINKED = "该会话已关联当前案件";
    public static final String EVIDENCE_NOT_EXIST = "证据不存在或无权访问";
    public static final String EVIDENCE_FILE_EMPTY = "上传证据文件为空";
    public static final String EVIDENCE_FILE_TYPE_NOT_SUPPORT =
            "不支持的证据文件类型，仅支持 JPG/PNG/BMP/WEBP 图片、PDF、DOC、DOCX、TXT";
    public static final String EVIDENCE_FILE_TOO_LARGE = "证据文件过大，单个文件不超过 20MB";
    public static final String EVIDENCE_NAME_REQUIRED = "证据名称不能为空";
    public static final String OCR_TEXT_EMPTY = "暂无识别文本，请先等待 OCR 处理完成";
    public static final String CASE_NO_PERMISSION = "无权操作该案件";
    public static final String EVIDENCE_NO_PERMISSION = "无权操作该证据";

    // ===================== 编号前缀 =====================
    public static final String CASE_NO_PREFIX = "CASE";
    public static final String EVIDENCE_NO_PREFIX = "EV";

    // ===================== 证据分析状态（V2.0 阶段三） =====================
    public static final String ANALYSIS_STATUS_PROCESSING = "PROCESSING";
    public static final String ANALYSIS_STATUS_SUCCESS = "SUCCESS";
    public static final String ANALYSIS_STATUS_FAILED = "FAILED";

    /** Agent 类型：Evidence Analysis Agent */
    public static final String AGENT_TYPE_EVIDENCE_ANALYSIS = "EVIDENCE_ANALYSIS";

    public static final String EVIDENCE_ANALYSIS_NOT_EXIST = "该证据暂无分析结果";
    public static final String EVIDENCE_OCR_NOT_READY = "请先等待 OCR 识别完成，再进行智能分析";
    public static final String EVIDENCE_ANALYSIS_LLM_UNAVAILABLE = "智能分析服务未配置（LLM 不可用），无法进行证据分析";
    public static final String INTEGRITY_LLM_UNAVAILABLE = "智能分析服务未配置（LLM 不可用），无法进行证据完整性分析";

    // ===================== 证据清单状态（V2.0 阶段四） =====================
    public static final String CHECKLIST_STATUS_REQUIRED = "REQUIRED";     // 建议收集
    public static final String CHECKLIST_STATUS_UPLOADED = "UPLOADED";     // 已上传
    public static final String CHECKLIST_STATUS_CONFIRMED = "CONFIRMED";   // 用户已确认
    public static final String CHECKLIST_STATUS_OPTIONAL = "OPTIONAL";     // 可选材料

    public static final String CHECKLIST_NOT_EXIST = "清单项目不存在或无权访问";
    public static final String CHECKLIST_NAME_REQUIRED = "证据名称不能为空";
    public static final String CHECKLIST_STATUS_INVALID = "清单状态不合法，仅支持 REQUIRED/UPLOADED/CONFIRMED/OPTIONAL";
    public static final String CHECKLIST_RELATION_EXISTS = "该证据已关联此清单项目";
    public static final String CHECKLIST_RELATION_NOT_EXIST = "该证据未关联此清单项目";
    public static final String CHECKLIST_EVIDENCE_NOT_BELONG = "该证据不属于当前案件";
    public static final String CHECKLIST_LLM_UNAVAILABLE = "智能分析服务未配置（LLM 不可用），无法生成证据清单";
    public static final String CHECKLIST_FACTS_EMPTY = "请先在「案件事实」中补充案件事实，再生成证据清单";

    // ===================== 限制 =====================
    public static final long EVIDENCE_MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB
    public static final int FACT_CONTENT_MAX_LENGTH = 2000;
}
