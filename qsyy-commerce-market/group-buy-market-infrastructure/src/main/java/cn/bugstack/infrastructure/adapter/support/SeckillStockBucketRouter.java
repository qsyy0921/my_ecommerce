package cn.bugstack.infrastructure.adapter.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

@Component
public class SeckillStockBucketRouter {

    @Value("${app.seckill.stock-bucket-count:64}")
    private Integer stockBucketCount;

    public int bucketOf(String userId, String outTradeNo) {
        return (int) (crc32(String.valueOf(userId) + ":" + String.valueOf(outTradeNo)) % bucketCount());
    }

    public int tryCount(Integer stockBucketTryCount) {
        return Math.min(bucketCount(), Math.max(1, null == stockBucketTryCount ? bucketCount() : stockBucketTryCount));
    }

    public int bucketCount() {
        return Math.max(1, null == stockBucketCount ? 64 : stockBucketCount);
    }

    private long crc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

}
