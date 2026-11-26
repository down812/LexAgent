package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.CaseEvidence;

/**
 * 案件证据 Mapper
 */
@Mapper
public interface CaseEvidenceMapper extends BaseMapper<CaseEvidence> {
}
