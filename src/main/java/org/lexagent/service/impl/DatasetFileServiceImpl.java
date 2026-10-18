package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.constants.RagConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.exception.BusinessNotFoundException;
import org.lexagent.mapper.DatasetFileMapper;
import org.lexagent.mapper.DatasetMapper;
import org.lexagent.model.dto.DatasetFileQueryDTO;
import org.lexagent.model.dto.LegalChunk;
import org.lexagent.model.entity.Dataset;
import org.lexagent.model.entity.DatasetFile;
import org.lexagent.model.vo.DatasetFileVO;
import org.lexagent.result.PageResult;
import org.lexagent.service.DatasetFileService;
import org.lexagent.service.DocumentParseService;
import org.lexagent.service.LegalStructureService;
import org.lexagent.service.VectorStoreService;
import org.lexagent.utils.FileStorageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文件服务实现
 * 串联：上传 → 文档解析 → 法律结构化切片 → 向量化存储 全链路
 */
@Slf4j
@Service
public class DatasetFileServiceImpl implements DatasetFileService {

    @Autowired
    private DatasetFileMapper datasetFileMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private DocumentParseService documentParseService;

    @Autowired
    private LegalStructureService legalStructureService;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 上传法律文件：存储到本地并生成文件记录，随后触发解析向量化链路
     *
     * 注意：不使用 @Transactional。因 triggerProcess 内含 ES 外部调用与 store 独立事务，
     * 处理失败时需保留文件记录（状态 FAILED）以便排查，不应回滚 insert。
     */
    @Override
    public DatasetFileVO uploadFile(Long datasetId, MultipartFile file) {
        // 1. 校验知识库存在
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessNotFoundException(RagConstant.DATASET_NOT_EXIST);
        }
        // 2. 校验文件非空与类型
        if (file == null || file.isEmpty()) {
            throw new BusinessException(RagConstant.FILE_UPLOAD_EMPTY);
        }
        String originalName = file.getOriginalFilename();
        fileStorageUtil.checkSupported(originalName);
        String fileType = fileStorageUtil.resolveFileType(originalName);

        // 3. 存储文件到本地
        String relativePath;
        try {
            relativePath = fileStorageUtil.store(file);
        } catch (Exception e) {
            log.error("文件存储失败", e);
            throw new BusinessException("文件存储失败: " + e.getMessage());
        }

        // 4. 生成文件记录（WAITING 状态）
        DatasetFile record = DatasetFile.builder()
                .datasetId(datasetId)
                .fileName(originalName)
                .fileType(fileType)
                .filePath(relativePath)
                .status(RagConstant.FILE_STATUS_WAITING)
                .uploadTime(DateTime.now())
                .build();
        datasetFileMapper.insert(record);

        // 5. 触发解析向量化链路：解析 → 结构化切片 → 向量化存储
        triggerProcess(record.getId());

        return toVO(record);
    }

    @Override
    public PageResult<DatasetFileVO> queryFiles(Integer pageNum, Integer pageSize, DatasetFileQueryDTO queryDTO) {
        Page<DatasetFile> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO != null) {
            if (queryDTO.getDatasetId() != null) {
                wrapper.eq(DatasetFile::getDatasetId, queryDTO.getDatasetId());
            }
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().trim().isEmpty()) {
                wrapper.eq(DatasetFile::getStatus, queryDTO.getStatus().trim());
            }
            if (queryDTO.getFileName() != null && !queryDTO.getFileName().trim().isEmpty()) {
                wrapper.like(DatasetFile::getFileName, queryDTO.getFileName().trim());
            }
        }
        wrapper.orderByDesc(DatasetFile::getUploadTime);
        Page<DatasetFile> result = datasetFileMapper.selectPage(page, wrapper);

        List<DatasetFileVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), result.getPages(), pageNum, pageSize, voList);
    }

    @Override
    public DatasetFileVO getFile(Long id) {
        DatasetFile file = datasetFileMapper.selectById(id);
        if (file == null) {
            throw new BusinessNotFoundException(RagConstant.FILE_NOT_EXIST);
        }
        return toVO(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long deleteFile(Long id) {
        DatasetFile file = datasetFileMapper.selectById(id);
        if (file == null) {
            throw new BusinessNotFoundException(RagConstant.FILE_NOT_EXIST);
        }
        // 删除本地文件
        try {
            String abs = fileStorageUtil.absolutePath(file.getFilePath());
            File f = new File(abs);
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
            log.warn("删除本地文件失败: {}", e.getMessage());
        }
        // TODO: 同时删除 Elasticsearch 中该文件对应的向量数据（阶段一后续实现）
        datasetFileMapper.deleteById(id);
        return id;
    }

    /**
     * 触发文件解析向量化链路：文档解析 → 法律结构化切片 → 向量化存储
     * 成功置 SUCCESS 并记录 chunkCount，失败置 FAILED 并记录 errorMsg
     */
    @Override
    public void triggerProcess(Long fileId) {
        DatasetFile record = datasetFileMapper.selectById(fileId);
        if (record == null) {
            return;
        }
        // 置为处理中
        record.setStatus(RagConstant.FILE_STATUS_PROCESSING);
        datasetFileMapper.updateById(record);

        try {
            // 1. 文档解析为纯文本
            String absPath = fileStorageUtil.absolutePath(record.getFilePath());
            String text = documentParseService.parse(absPath, record.getFileType());

            // 2. 获取法律领域（来自所属知识库）
            Dataset dataset = datasetMapper.selectById(record.getDatasetId());
            String domain = (dataset != null) ? dataset.getDomain() : "";

            // 3. 法律文本结构化切片
            List<LegalChunk> chunks = legalStructureService.structure(text, domain);

            // 4. 向量化存储（写入 Elasticsearch + MySQL）
            int count = vectorStoreService.store(chunks, record.getDatasetId(), fileId, domain);

            // 5. 成功
            record.setStatus(RagConstant.FILE_STATUS_SUCCESS);
            record.setChunkCount(count);
            record.setProcessTime(DateTime.now());
            datasetFileMapper.updateById(record);
            log.info("文件[{}]处理完成，切片数={}", fileId, count);
        } catch (Exception e) {
            log.error("文件[{}]处理失败", fileId, e);
            record.setStatus(RagConstant.FILE_STATUS_FAILED);
            record.setErrorMsg(e.getMessage());
            record.setProcessTime(DateTime.now());
            datasetFileMapper.updateById(record);
        }
    }

    private DatasetFileVO toVO(DatasetFile file) {
        DatasetFileVO vo = new DatasetFileVO();
        BeanUtils.copyProperties(file, vo);
        return vo;
    }
}
