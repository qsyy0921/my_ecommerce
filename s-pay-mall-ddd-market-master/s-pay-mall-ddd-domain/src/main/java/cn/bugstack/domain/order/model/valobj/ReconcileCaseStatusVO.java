package cn.bugstack.domain.order.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReconcileCaseStatusVO {

    OPEN(0, "待处理"),
    CONFIRMED(1, "已确认处理"),
    IGNORED(2, "已忽略"),
    CLOSED(3, "已关闭");

    private final Integer code;
    private final String info;

    public static ReconcileCaseStatusVO valueOfCode(Integer code) {
        if (null == code) {
            return null;
        }
        for (ReconcileCaseStatusVO value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

    public boolean isOpen() {
        return OPEN.equals(this);
    }

    public boolean isTerminal() {
        return !OPEN.equals(this);
    }

}
