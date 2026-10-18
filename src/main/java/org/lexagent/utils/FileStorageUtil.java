package org.lexagent.utils;

import org.lexagent.constants.RagConstant;
import org.lexagent.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 法律文件本地存储工具
 * 按 日期目录/UUID.扩展名 存储，返回相对路径供数据库记录
 */
@Component
public class FileStorageUtil {

    @Value("${lexagent.rag.upload-dir:./data/files}")
    private String uploadDir;

    private String getAbsoluteUploadDir() {
        Path path = Paths.get(uploadDir).toAbsolutePath();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("无法创建上传目录: " + path, e);
            }
        }
        return path.toString();
    }

    private static final Set<String> SUPPORTED_EXT =
            Set.of("pdf", "docx", "txt", "md", "markdown", "jpg", "jpeg", "png");

    /**
     * 校验文件扩展名是否受支持
     */
    public void checkSupported(String originalName) {
        String ext = getExtension(originalName);
        if (!SUPPORTED_EXT.contains(ext)) {
            throw new BusinessException(RagConstant.FILE_TYPE_NOT_SUPPORT);
        }
    }

    /**
     * 解析文件类型（大写枚举值）
     */
    public String resolveFileType(String originalName) {
        String ext = getExtension(originalName);
        switch (ext) {
            case "pdf":
                return RagConstant.FILE_TYPE_PDF;
            case "docx":
                return RagConstant.FILE_TYPE_DOCX;
            case "txt":
                return RagConstant.FILE_TYPE_TXT;
            case "md":
            case "markdown":
                return RagConstant.FILE_TYPE_MD;
            case "jpg":
            case "jpeg":
            case "png":
                return RagConstant.FILE_TYPE_IMAGE;
            default:
                return null;
        }
    }

    /**
     * 保存文件到本地，返回相对存储路径（如 2026/10/13/uuid.pdf）
     */
    public String store(MultipartFile file) throws IOException {
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String baseDir = getAbsoluteUploadDir();
        File dir = new File(baseDir, dateDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建存储目录: " + dir.getAbsolutePath());
        }

        String original = (file.getOriginalFilename() == null) ? "file" : file.getOriginalFilename();
        String ext = getExtension(original);
        String fileName = java.util.UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        File dest = new File(dir, fileName).getAbsoluteFile();
        file.transferTo(dest);

        return dateDir + "/" + fileName;
    }

    /**
     * 根据相对路径获取绝对路径
     */
    public String absolutePath(String relativePath) {
        return new File(getAbsoluteUploadDir(), relativePath.replace("/", File.separator)).getAbsolutePath();
    }

    private String getExtension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase();
    }
}