package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

@Component
public class SeckillOrderShardRouter {

    @Value("${app.seckill.order-shard-count:1}")
    private Integer orderShardCount;

    @Value("${app.seckill.order-table-prefix:seckill_order}")
    private String orderTablePrefix;

    public boolean useSharding() {
        return shardCount() > 1;
    }

    public int shardCount() {
        return Math.max(1, null == orderShardCount ? 1 : orderShardCount);
    }

    public String tableName(String userId, String outTradeNo) {
        if (!useSharding()) {
            return tablePrefix();
        }
        return tableName(shardIndex(userId, outTradeNo));
    }

    public String tableName(int shardIndex) {
        return tablePrefix() + "_" + String.format("%02d", shardIndex);
    }

    public Map<String, List<SeckillOrder>> groupByTable(List<SeckillOrder> seckillOrders) {
        Map<String, List<SeckillOrder>> orderMap = new HashMap<>();
        for (SeckillOrder seckillOrder : seckillOrders) {
            String tableName = tableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo());
            orderMap.computeIfAbsent(tableName, key -> new ArrayList<>()).add(seckillOrder);
        }
        return orderMap;
    }

    private int shardIndex(String userId, String outTradeNo) {
        return (int) (crc32(String.valueOf(userId) + ":" + String.valueOf(outTradeNo)) % shardCount());
    }

    private long crc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private String tablePrefix() {
        String tablePrefix = null == orderTablePrefix || orderTablePrefix.trim().isEmpty() ? "seckill_order" : orderTablePrefix.trim();
        if (!tablePrefix.matches("[a-zA-Z0-9_]+")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "illegal seckill order table prefix");
        }
        return tablePrefix;
    }

}
