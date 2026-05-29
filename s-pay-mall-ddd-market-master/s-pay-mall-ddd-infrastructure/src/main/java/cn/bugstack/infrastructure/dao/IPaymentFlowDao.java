package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.PaymentFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IPaymentFlowDao {

    int insertIgnore(PaymentFlow paymentFlow);

    int countMissThirdPartyBill();

    List<PaymentFlow> queryMissThirdPartyBillList(@Param("limit") Integer limit);

}
