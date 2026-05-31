package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradePolicyPort;
import cn.bugstack.infrastructure.dcc.DCCService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class TradePolicyPort implements ITradePolicyPort {

    @Resource
    private DCCService dccService;

    @Override
    public boolean isSCBlackIntercept(String source, String channel) {
        return dccService.isSCBlackIntercept(source, channel);
    }

}
