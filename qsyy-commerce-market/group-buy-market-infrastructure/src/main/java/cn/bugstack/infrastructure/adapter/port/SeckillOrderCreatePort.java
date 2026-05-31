package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderCreatePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillBatchOrderCreateSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillSingleOrderCreateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class SeckillOrderCreatePort implements ISeckillOrderCreatePort {

    @Resource
    private SeckillSingleOrderCreateSupport seckillSingleOrderCreateSupport;
    @Resource
    private SeckillBatchOrderCreateSupport seckillBatchOrderCreateSupport;

    @Transactional(timeout = 5)
    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        seckillSingleOrderCreateSupport.create(seckillOrderEntity);
    }

    @Transactional(timeout = 10)
    @Override
    public void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        seckillBatchOrderCreateSupport.create(seckillOrderEntities);
    }

}
