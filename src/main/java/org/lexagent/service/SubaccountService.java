package org.lexagent.service;

import org.lexagent.model.dto.SubaccountCreateDTO;
import org.lexagent.model.dto.SubaccountQueryDTO;
import org.lexagent.model.dto.SubaccountUpdateDTO;
import org.lexagent.model.vo.SubaccountVO;
import org.lexagent.result.PageResult;
import org.lexagent.result.Result;

public interface SubaccountService {

    // 创建子账号
    Result<?> createSubaccount(SubaccountCreateDTO createDTO);

    // 查询子账号列表
    PageResult<SubaccountVO> querySubaccounts(Integer pageNum, Integer pageSize, SubaccountQueryDTO queryDTO);

    // 修改子账号
    SubaccountVO updateSubaccount(Integer subaccountId, SubaccountUpdateDTO updateDTO);

    // 删除子账号
    Integer deleteSubaccount(Integer subaccountId);

}
