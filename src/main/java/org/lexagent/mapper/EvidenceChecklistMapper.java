package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.EvidenceChecklist;

/**
 * 证据清单 Mapper
 */
@Mapper
public interface EvidenceChecklistMapper extends BaseMapper<EvidenceChecklist> {
}
