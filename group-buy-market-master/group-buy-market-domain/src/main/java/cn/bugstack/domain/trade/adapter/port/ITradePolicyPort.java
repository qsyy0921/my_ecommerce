package cn.bugstack.domain.trade.adapter.port;

public interface ITradePolicyPort {

    boolean isSCBlackIntercept(String source, String channel);

}
