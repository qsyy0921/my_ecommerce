package cn.bugstack.domain.goods.adapter.repository;

/**
 * @author qsyy
 * @description 结算仓储
 * @create 2025-02-15 09:12
 */
public interface IGoodsRepository {

    void changeOrderDealDone(String orderId);

}
