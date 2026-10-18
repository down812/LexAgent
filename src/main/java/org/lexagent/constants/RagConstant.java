package org.lexagent.constants;

/**
 * RAG 法律知识库相关常量
 */
public class RagConstant {

    // ============ 文件处理状态 ============
    public static final String FILE_STATUS_WAITING = "WAITING";
    public static final String FILE_STATUS_PROCESSING = "PROCESSING";
    public static final String FILE_STATUS_SUCCESS = "SUCCESS";
    public static final String FILE_STATUS_FAILED = "FAILED";

    // ============ 支持的文件类型 ============
    public static final String FILE_TYPE_PDF = "PDF";
    public static final String FILE_TYPE_DOCX = "DOCX";
    public static final String FILE_TYPE_TXT = "TXT";
    public static final String FILE_TYPE_MD = "MD";
    public static final String FILE_TYPE_IMAGE = "IMAGE";

    // ============ 元数据 key（写入 ES 向量） ============
    public static final String META_LAW = "law";
    public static final String META_ARTICLE = "article";
    public static final String META_DOMAIN = "domain";
    public static final String META_CHAPTER = "chapter";
    public static final String META_DATASET_ID = "datasetId";
    public static final String META_FILE_ID = "fileId";

    // ============ 消息 ============
    public static final String DATASET_NOT_EXIST = "知识库不存在";
    public static final String DATASET_NAME_EXIST = "知识库名称已存在";
    public static final String FILE_NOT_EXIST = "文件记录不存在";
    public static final String FILE_TYPE_NOT_SUPPORT = "不支持的文件类型，仅支持 PDF/DOCX/TXT/MD/图片";
    public static final String FILE_UPLOAD_EMPTY = "上传文件为空";
    public static final String DATASET_HAS_FILES = "知识库下仍存在文件，无法删除";
    public static final String NO_PERMISSION_ADMIN_REQUIRED = "需要管理员权限";

    // ============ OCR 相关 ============
    /**
     * 扫描件判定阈值：PDF 每页平均字符数低于此值视为扫描件，转 OCR
     */
    public static final int SCANNED_PDF_MIN_CHARS_PER_PAGE = 10;
    public static final String OCR_SERVICE_UNAVAILABLE = "OCR 服务不可用，无法处理扫描件/图片，请先启动 ocr-service（见 ocr-service/README.md）";

    private RagConstant() {
    }
}
