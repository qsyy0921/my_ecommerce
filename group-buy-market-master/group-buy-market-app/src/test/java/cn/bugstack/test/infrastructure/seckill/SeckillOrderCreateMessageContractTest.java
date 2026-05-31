package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

public class SeckillOrderCreateMessageContractTest {

    @Test
    public void messageEnvelopeShouldExposeStableSchemaAndRouteKey() {
        SeckillOrderEntity order = order();

        SeckillOrderCreateMessageEntity message = SeckillOrderCreateMessageEntity.fromOrder(order);

        Assert.assertEquals(SeckillOrderCreateMessageEntity.SCHEMA_VERSION, message.getSchemaVersion());
        Assert.assertEquals(SeckillOrderCreateMessageEntity.EVENT_TYPE, message.getEventType());
        Assert.assertEquals("200001:user_001:trade_001", message.getMessageId());
        Assert.assertEquals("200001:user_001:trade_001", message.getRouteKey());
        Assert.assertEquals(message.getRouteKey(), message.stableRouteKey());
        Assert.assertNotNull(message.getOccurredAt());
        Assert.assertTrue(message.getOccurredAt().contains("T"));
    }

    @Test
    public void jsonRoundTripShouldRestoreOrderEntityWithoutChangingBusinessKey() {
        SeckillOrderEntity order = order();
        SeckillOrderCreateMessageEntity message = SeckillOrderCreateMessageEntity.fromOrder(order);

        String json = JSON.toJSONString(message);
        SeckillOrderCreateMessageEntity parsed = JSON.parseObject(json, SeckillOrderCreateMessageEntity.class);
        SeckillOrderEntity restored = parsed.toOrderEntity();

        Assert.assertEquals(order.getActivityId(), restored.getActivityId());
        Assert.assertEquals(order.getUserId(), restored.getUserId());
        Assert.assertEquals(order.getOutTradeNo(), restored.getOutTradeNo());
        Assert.assertEquals(order.getOrderId(), restored.getOrderId());
        Assert.assertEquals(order.getGoodsId(), restored.getGoodsId());
        Assert.assertEquals(order.getSource(), restored.getSource());
        Assert.assertEquals(order.getChannel(), restored.getChannel());
        Assert.assertEquals(order.getSeckillPrice(), restored.getSeckillPrice());
        Assert.assertEquals("200001:user_001:trade_001", restored.getSourceMessageId());
    }

    @Test
    public void legacyOrderJsonShouldStillBeConsumableAsEnvelope() {
        String legacyJson = JSON.toJSONString(order());

        SeckillOrderCreateMessageEntity parsed = JSON.parseObject(legacyJson, SeckillOrderCreateMessageEntity.class);
        SeckillOrderEntity restored = parsed.toOrderEntity();

        Assert.assertEquals(Long.valueOf(200001L), restored.getActivityId());
        Assert.assertEquals("user_001", restored.getUserId());
        Assert.assertEquals("trade_001", restored.getOutTradeNo());
        Assert.assertEquals("200001:user_001:trade_001", parsed.stableRouteKey());
        Assert.assertEquals("200001:user_001:trade_001", restored.getSourceMessageId());
    }

    private static SeckillOrderEntity order() {
        return SeckillOrderEntity.builder()
                .activityId(200001L)
                .userId("user_001")
                .outTradeNo("trade_001")
                .orderId("order_001")
                .source("s01")
                .channel("c01")
                .goodsId("sku_001")
                .goodsName("秒杀商品")
                .activityName("秒杀活动")
                .originalPrice(new BigDecimal("100.00"))
                .seckillPrice(new BigDecimal("79.00"))
                .status(SeckillOrderStatusEnumVO.CREATE.getCode())
                .stockBucket(3)
                .stockBefore(10)
                .stockAfter(9)
                .traceId("trace-001")
                .build();
    }

}
