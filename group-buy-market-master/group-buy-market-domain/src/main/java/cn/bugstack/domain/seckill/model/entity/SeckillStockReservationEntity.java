package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillStockReservationEntity {

    public static final String SUCCESS = "SUCCESS";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String STOCK_NOT_ENOUGH = "STOCK_NOT_ENOUGH";

    private String status;
    private Integer stockBucket;
    private Integer stockBefore;
    private Integer stockAfter;

    public static SeckillStockReservationEntity success(Integer stockBucket, Integer stockBefore, Integer stockAfter) {
        return SeckillStockReservationEntity.builder()
                .status(SUCCESS)
                .stockBucket(stockBucket)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .build();
    }

    public static SeckillStockReservationEntity duplicate() {
        return SeckillStockReservationEntity.builder().status(DUPLICATE).build();
    }

    public static SeckillStockReservationEntity stockNotEnough() {
        return SeckillStockReservationEntity.builder().status(STOCK_NOT_ENOUGH).build();
    }

    public boolean isSuccess() {
        return SUCCESS.equals(status);
    }

    public boolean isDuplicate() {
        return DUPLICATE.equals(status);
    }

}
