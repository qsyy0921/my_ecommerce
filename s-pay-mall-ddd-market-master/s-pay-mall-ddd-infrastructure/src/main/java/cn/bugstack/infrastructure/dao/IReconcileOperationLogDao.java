package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.ReconcileOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IReconcileOperationLogDao {

    int insert(ReconcileOperationLog operationLog);

    List<ReconcileOperationLog> queryLogList(@Param("bizId") String bizId,
                                             @Param("lastId") Long lastId,
                                             @Param("pageSize") Integer pageSize);

}
