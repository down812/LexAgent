package org.lexagent.service;

/**
 * 文档解析服务
 * 按文件类型路由解析：PDF(PDFBox) / DOCX(Apache POI) / TXT、MD(直接读取)
 * 统一输出纯文本，供法律文本结构化切片使用
 */
public interface DocumentParseService {

    /**
     * 解析文件为纯文本
     *
     * @param absolutePath 文件绝对路径
     * @param fileType     文件类型（PDF/DOCX/TXT/MD/IMAGE）
     * @return 解析出的纯文本（已清洗）
     */
    String parse(String absolutePath, String fileType) throws Exception;
}
