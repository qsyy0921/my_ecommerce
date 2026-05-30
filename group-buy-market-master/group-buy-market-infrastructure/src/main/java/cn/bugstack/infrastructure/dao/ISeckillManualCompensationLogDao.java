package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SeckillManualCompensationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ISeckillManualCompensationLogDao {

    void insert(SeckillManualCompensationLog log);

    List<SeckillManualCompensationLog> queryRecentLogs(@Param("limit") Integer limit);

}
