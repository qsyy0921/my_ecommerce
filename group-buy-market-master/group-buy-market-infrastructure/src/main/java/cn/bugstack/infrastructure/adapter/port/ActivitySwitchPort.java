package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.activity.adapter.port.IActivitySwitchPort;
import cn.bugstack.infrastructure.dcc.DCCService;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class ActivitySwitchPort implements IActivitySwitchPort {

    @Resource
    private DCCService dccService;

    @Override
    public boolean downgradeSwitch() {
        return dccService.isDowngradeSwitch();
    }

    @Override
    public boolean cutRange(String userId) {
        return dccService.isCutRange(userId);
    }

}
