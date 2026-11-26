package org.lexagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.lexagent.model.entity.AnswerFeedback;

/**
 * 回答评价 Mapper
 */
@Mapper
public interface AnswerFeedbackMapper extends BaseMapper<AnswerFeedback> {
}
