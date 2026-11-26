package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.LegalCase;

/**
 * 案件 Mapper
 */
@Mapper
public interface LegalCaseMapper extends BaseMapper<LegalCase> {
}
