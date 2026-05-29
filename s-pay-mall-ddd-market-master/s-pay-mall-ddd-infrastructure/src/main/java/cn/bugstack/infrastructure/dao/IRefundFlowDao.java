package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.RefundFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRefundFlowDao {

    int insertIgnore(RefundFlow refundFlow);

    int countMissThirdPartyBill();

    List<RefundFlow> queryMissThirdPartyBillList(@Param("limit") Integer limit);

}
