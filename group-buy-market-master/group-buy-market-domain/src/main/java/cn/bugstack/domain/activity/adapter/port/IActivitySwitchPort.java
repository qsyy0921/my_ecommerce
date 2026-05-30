package cn.bugstack.domain.activity.adapter.port;

public interface IActivitySwitchPort {

    boolean downgradeSwitch();

    boolean cutRange(String userId);

}
