package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.EvidenceAnalysis;

/**
 * 证据分析结果 Mapper
 */
@Mapper
public interface EvidenceAnalysisMapper extends BaseMapper<EvidenceAnalysis> {
}
