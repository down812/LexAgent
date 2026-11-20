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
import org.lexagent.model.entity.Dataset;
import org.lexagent.model.entity.DatasetFile;
import org.lexagent.model.vo.DatasetFileVO;
import org.lexagent.result.PageResult;
import org.lexagent.service.DatasetFileService;
import org.lexagent.service.FileProcessTaskService;
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
 *
 * 职责：文件 CRUD（上传存储 / 查询 / 删除）+ 触发解析向量化
 *
 * 异步改造：
 *   uploadFile 存储文件后调用 fileProcessTaskService.processAsync() 异步触发解析，
 *   立即返回 WAITING 状态，前端通过 GET /file/{id} 轮询状态变化。
 *   解析向量化实际逻辑在 FileProcessTaskServiceImpl。
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
    private FileProcessTaskService fileProcessTaskService;

    /**
     * 上传法律文件：存储到本地并生成文件记录，异步触发解析向量化链路
     *
     * 改造说明：解析向量化改为异步执行，上传接口立即返回 WAITING 状态。
     * 前端通过 GET /file/{id} 轮询状态：WAITING → PROCESSING → SUCCESS/FAILED。
     *
     * 注意：不使用 @Transactional。文件记录 insert 需独立提交，
     * 否则异步线程在事务未提交时查不到记录会跳过处理。
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

        // 5. 异步触发解析向量化链路（立即返回，不等待）
        //    注意：必须在 insert 提交后调用，否则异步线程查不到记录
        log.info("文件[{}]上传成功，状态=WAITING，异步触发解析向量化", record.getId());
        fileProcessTaskService.processAsync(record.getId());

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
     * 触发文件解析向量化链路（同步版，供手动重处理/测试用）
     *
     * 上传流程已改为异步（uploadFile 内调 processAsync），
     * 此方法保留为同步执行，用于：
     *   - 失败文件重新处理（管理员手动触发）
     *   - 单元测试同步验证
     *   - 外部任务调度调用
     */
    @Override
    public void triggerProcess(Long fileId) {
        log.info("手动触发文件处理（同步）：fileId={}", fileId);
        fileProcessTaskService.process(fileId);
    }

    private DatasetFileVO toVO(DatasetFile file) {
        DatasetFileVO vo = new DatasetFileVO();
        BeanUtils.copyProperties(file, vo);
        return vo;
    }
}
