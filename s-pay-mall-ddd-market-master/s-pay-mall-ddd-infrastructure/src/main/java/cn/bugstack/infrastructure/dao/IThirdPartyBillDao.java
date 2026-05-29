package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.ThirdPartyBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IThirdPartyBillDao {

    int insertIgnoreBatch(List<ThirdPartyBill> billList);

    int countUnmatchedBill();

    List<ThirdPartyBill> queryUnmatchedBillList(@Param("limit") Integer limit);

}
