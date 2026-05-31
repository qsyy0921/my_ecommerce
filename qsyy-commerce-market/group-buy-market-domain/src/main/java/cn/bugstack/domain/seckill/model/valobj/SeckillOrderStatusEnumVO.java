package cn.bugstack.domain.seckill.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum SeckillOrderStatusEnumVO {

    CREATE(0, "已抢到资格，待支付"),
    COMPLETE(1, "已支付完成"),
    CLOSE(2, "已关闭"),
    REFUND(3, "已退款"),
    ;

    private Integer code;
    private String info;

    public static SeckillOrderStatusEnumVO valueOf(Integer code) {
        if (null == code) return CREATE;
        switch (code) {
            case 0:
                return CREATE;
            case 1:
                return COMPLETE;
            case 2:
                return CLOSE;
            case 3:
                return REFUND;
            default:
                return CREATE;
        }
    }

    public boolean canPay() {
        return CREATE.equals(this);
    }

    public boolean canTimeoutClose() {
        return CREATE.equals(this);
    }

    public boolean canRefund() {
        return COMPLETE.equals(this);
    }

    public boolean releasesStock() {
        return CLOSE.equals(this) || REFUND.equals(this);
    }

}
