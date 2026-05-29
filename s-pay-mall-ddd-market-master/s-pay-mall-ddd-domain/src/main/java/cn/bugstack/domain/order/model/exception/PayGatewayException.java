package cn.bugstack.domain.order.model.exception;

public class PayGatewayException extends RuntimeException {

    public PayGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

}
