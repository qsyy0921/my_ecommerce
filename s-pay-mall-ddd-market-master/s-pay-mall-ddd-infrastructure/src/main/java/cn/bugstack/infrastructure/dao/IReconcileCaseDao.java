package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.ReconcileCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IReconcileCaseDao {

    int upsert(ReconcileCase reconcileCase);

    List<ReconcileCase> queryCaseList(@Param("caseStatus") Integer caseStatus,
                                      @Param("caseType") String caseType,
                                      @Param("lastId") Long lastId,
                                      @Param("pageSize") Integer pageSize);

    int updateCaseHandled(@Param("caseNo") String caseNo,
                          @Param("caseStatus") Integer caseStatus,
                          @Param("handler") String handler,
                          @Param("handleNote") String handleNote);

    int countOpenCase(@Param("caseType") String caseType);

    int countSlaTimeoutOpenCase();

}
