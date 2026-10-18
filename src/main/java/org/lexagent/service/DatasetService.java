package org.lexagent.service;

import org.lexagent.model.dto.DatasetCreateDTO;
import org.lexagent.model.dto.DatasetQueryDTO;
import org.lexagent.model.dto.DatasetUpdateDTO;
import org.lexagent.model.vo.DatasetVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;

/**
 * 法律知识库分类服务
 */
public interface DatasetService {

    Result<?> createDataset(DatasetCreateDTO dto);

    PageResult<DatasetVO> queryDatasets(Integer pageNum, Integer pageSize, DatasetQueryDTO queryDTO);

    DatasetVO getDataset(Long id);

    DatasetVO updateDataset(DatasetUpdateDTO dto);

    Long deleteDataset(Long id);
}
