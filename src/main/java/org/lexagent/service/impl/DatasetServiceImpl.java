package org.lexagent.service.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lexagent.constants.RagConstant;
import org.lexagent.exception.BusinessException;
import org.lexagent.exception.BusinessNotFoundException;
import org.lexagent.mapper.DatasetFileMapper;
import org.lexagent.mapper.DatasetMapper;
import org.lexagent.model.dto.DatasetCreateDTO;
import org.lexagent.model.dto.DatasetQueryDTO;
import org.lexagent.model.dto.DatasetUpdateDTO;
import org.lexagent.model.entity.Dataset;
import org.lexagent.model.entity.DatasetFile;
import org.lexagent.model.vo.DatasetVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;
import org.lexagent.service.DatasetService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 法律知识库分类服务实现
 */
@Slf4j
@Service
public class DatasetServiceImpl implements DatasetService {

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private DatasetFileMapper datasetFileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> createDataset(DatasetCreateDTO dto) {
        // 名称唯一校验
        Long count = datasetMapper.selectCount(
                new LambdaQueryWrapper<Dataset>().eq(Dataset::getName, dto.getName()));
        if (count != null && count > 0) {
            throw new BusinessException(RagConstant.DATASET_NAME_EXIST);
        }

        Dataset dataset = Dataset.builder()
                .name(dto.getName())
                .domain(dto.getDomain())
                .description(dto.getDescription())
                .createdAt(DateTime.now())
                .updatedAt(DateTime.now())
                .build();
        datasetMapper.insert(dataset);

        return Result.success("知识库创建成功", dataset.getId());
    }

    @Override
    public PageResult<DatasetVO> queryDatasets(Integer pageNum, Integer pageSize, DatasetQueryDTO queryDTO) {
        Page<Dataset> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Dataset> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO != null) {
            if (queryDTO.getName() != null && !queryDTO.getName().trim().isEmpty()) {
                wrapper.like(Dataset::getName, queryDTO.getName().trim());
            }
            if (queryDTO.getDomain() != null && !queryDTO.getDomain().trim().isEmpty()) {
                wrapper.eq(Dataset::getDomain, queryDTO.getDomain().trim());
            }
        }
        wrapper.orderByDesc(Dataset::getCreatedAt);
        Page<Dataset> result = datasetMapper.selectPage(page, wrapper);

        List<DatasetVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), result.getPages(), pageNum, pageSize, voList);
    }

    @Override
    public DatasetVO getDataset(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessNotFoundException(RagConstant.DATASET_NOT_EXIST);
        }
        return toVO(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO updateDataset(DatasetUpdateDTO dto) {
        Dataset dataset = datasetMapper.selectById(dto.getId());
        if (dataset == null) {
            throw new BusinessNotFoundException(RagConstant.DATASET_NOT_EXIST);
        }
        // 改名时校验唯一性
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(dataset.getName())) {
            Long count = datasetMapper.selectCount(
                    new LambdaQueryWrapper<Dataset>()
                            .eq(Dataset::getName, dto.getName())
                            .ne(Dataset::getId, dto.getId()));
            if (count != null && count > 0) {
                throw new BusinessException(RagConstant.DATASET_NAME_EXIST);
            }
            dataset.setName(dto.getName());
        }
        if (StringUtils.isNotBlank(dto.getDomain())) {
            dataset.setDomain(dto.getDomain());
        }
        if (StringUtils.isNotBlank(dto.getDescription())) {
            dataset.setDescription(dto.getDescription());
        }
        dataset.setUpdatedAt(DateTime.now());
        datasetMapper.updateById(dataset);
        return toVO(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long deleteDataset(Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessNotFoundException(RagConstant.DATASET_NOT_EXIST);
        }
        // 存在文件时禁止删除
        Long fileCount = datasetFileMapper.selectCount(
                new LambdaQueryWrapper<DatasetFile>().eq(DatasetFile::getDatasetId, id));
        if (fileCount != null && fileCount > 0) {
            throw new BusinessException(RagConstant.DATASET_HAS_FILES);
        }
        datasetMapper.deleteById(id);
        return id;
    }

    /**
     * 实体转 VO，并统计该知识库下的文件数量
     */
    private DatasetVO toVO(Dataset dataset) {
        DatasetVO vo = new DatasetVO();
        BeanUtils.copyProperties(dataset, vo);
        Long fileCount = datasetFileMapper.selectCount(
                new LambdaQueryWrapper<DatasetFile>().eq(DatasetFile::getDatasetId, dataset.getId()));
        vo.setFileCount(fileCount == null ? 0L : fileCount);
        return vo;
    }
}
