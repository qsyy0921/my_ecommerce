package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.OrderStateFlow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IOrderStateFlowDao {

    void insertIgnore(OrderStateFlow orderStateFlow);

}
