package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.CaseFact;

/**
 * 案件事实 Mapper
 */
@Mapper
public interface CaseFactMapper extends BaseMapper<CaseFact> {
}
