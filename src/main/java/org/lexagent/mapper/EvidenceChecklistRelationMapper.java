package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.EvidenceChecklistRelation;

/**
 * 证据清单关联 Mapper
 */
@Mapper
public interface EvidenceChecklistRelationMapper extends BaseMapper<EvidenceChecklistRelation> {
}
