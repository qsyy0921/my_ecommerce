package cn.bugstack.domain.goods.service;

import cn.bugstack.domain.goods.adapter.repository.IGoodsRepository;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 结算服务
 * @create 2025-02-15 09:11
 */
public class GoodsService implements IGoodsService {

    private final IGoodsRepository repository;

    public GoodsService(IGoodsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void changeOrderDealDone(String orderId) {
        repository.changeOrderDealDone(orderId);
    }

}
