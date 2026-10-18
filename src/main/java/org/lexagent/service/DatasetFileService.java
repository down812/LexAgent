package org.lexagent.service;

import org.lexagent.model.dto.DatasetFileQueryDTO;
import org.lexagent.model.vo.DatasetFileVO;
import org.lexagent.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文件服务：上传 / 查询 / 删除 / 触发解析向量化链路
 */
public interface DatasetFileService {

    /**
     * 上传法律文件：存储到本地并生成 WAITING 状态的文件记录
     */
    DatasetFileVO uploadFile(Long datasetId, MultipartFile file);

    PageResult<DatasetFileVO> queryFiles(Integer pageNum, Integer pageSize, DatasetFileQueryDTO queryDTO);

    DatasetFileVO getFile(Long id);

    Long deleteFile(Long id);

    /**
     * 触发文件解析向量化链路：文档解析 → 法律结构化切片 → 向量化存储
     */
    void triggerProcess(Long fileId);
}
