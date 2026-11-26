package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.CaseConversation;

/**
 * 案件会话关联 Mapper
 */
@Mapper
public interface CaseConversationMapper extends BaseMapper<CaseConversation> {
}
