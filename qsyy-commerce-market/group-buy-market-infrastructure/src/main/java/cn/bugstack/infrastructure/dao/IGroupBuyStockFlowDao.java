package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyStockFlow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IGroupBuyStockFlowDao {

    int insertIgnore(GroupBuyStockFlow groupBuyStockFlow);

}
