package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SeckillStockFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ISeckillStockFlowDao {

    int insertIgnore(SeckillStockFlow seckillStockFlow);

    int insertIgnoreBatch(@Param("list") List<SeckillStockFlow> seckillStockFlows);

}
