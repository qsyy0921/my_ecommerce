package cn.bugstack.domain.shared.statemachine;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized order state transition guard for trade-like aggregates.
 */
public class OrderStateMachine {

    public static final String BIZ_SECKILL_ORDER = "SECKILL_ORDER";
    public static final String BIZ_GROUP_BUY_ORDER_LIST = "GROUP_BUY_ORDER_LIST";
    public static final String BIZ_GROUP_BUY_TEAM = "GROUP_BUY_TEAM";

    public static final String STATE_INIT = "INIT";
    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_CREATE = "CREATE";
    public static final String STATE_COMPLETE = "COMPLETE";
    public static final String STATE_CLOSE = "CLOSE";
    public static final String STATE_REFUND = "REFUND";
    public static final String STATE_REFUNDING = "REFUNDING";
    public static final String STATE_PARTIAL_REFUND = "PARTIAL_REFUND";
    public static final String STATE_REFUND_REJECTED = "REFUND_REJECTED";
    public static final String STATE_FULFILLED = "FULFILLED";
    public static final String STATE_PROGRESS = "PROGRESS";
    public static final String STATE_FAIL = "FAIL";
    public static final String STATE_COMPLETE_FAIL = "COMPLETE_FAIL";

    public static final String EVENT_LOCK = "LOCK";
    public static final String EVENT_ASYNC_ORDER_CREATED = "ASYNC_ORDER_CREATED";
    public static final String EVENT_PAY_SUCCESS = "PAY_SUCCESS";
    public static final String EVENT_TIMEOUT_RELEASE = "TIMEOUT_RELEASE";
    public static final String EVENT_FULFILL = "FULFILL";
    public static final String EVENT_REFUND_APPLY = "REFUND_APPLY";
    public static final String EVENT_REFUND_SUCCESS = "REFUND_SUCCESS";
    public static final String EVENT_REFUND_PARTIAL_SUCCESS = "REFUND_PARTIAL_SUCCESS";
    public static final String EVENT_REFUND_REJECT = "REFUND_REJECT";
    public static final String EVENT_OPEN_TEAM = "OPEN_TEAM";
    public static final String EVENT_TEAM_FORMED = "TEAM_FORMED";
    public static final String EVENT_TEAM_REFUND_PARTIAL = "TEAM_REFUND_PARTIAL";
    public static final String EVENT_TEAM_REFUND_ALL = "TEAM_REFUND_ALL";
    public static final String EVENT_TEAM_TIMEOUT = "TEAM_TIMEOUT";

    private static final Set<String> TRANSITIONS = buildTransitions();

    private OrderStateMachine() {
    }

    public static void check(String bizType, String fromStatus, String event, String toStatus) {
        String transition = transitionOf(bizType, fromStatus, event, toStatus);
        if (!TRANSITIONS.contains(transition)) {
            throw new IllegalStateException("Illegal order state transition: " + transition);
        }
    }

    public static boolean canTransit(String bizType, String fromStatus, String event, String toStatus) {
        return TRANSITIONS.contains(transitionOf(bizType, fromStatus, event, toStatus));
    }

    private static Set<String> buildTransitions() {
        Set<String> transitions = new HashSet<>();
        add(transitions, BIZ_SECKILL_ORDER, STATE_PROCESSING, EVENT_ASYNC_ORDER_CREATED, STATE_CREATE);
        add(transitions, BIZ_SECKILL_ORDER, STATE_CREATE, EVENT_TIMEOUT_RELEASE, STATE_CLOSE);
        add(transitions, BIZ_SECKILL_ORDER, STATE_CREATE, EVENT_PAY_SUCCESS, STATE_COMPLETE);
        add(transitions, BIZ_SECKILL_ORDER, STATE_COMPLETE, EVENT_REFUND_SUCCESS, STATE_REFUND);
        addAfterSaleTransitions(transitions, BIZ_SECKILL_ORDER, STATE_REFUND);

        add(transitions, BIZ_GROUP_BUY_ORDER_LIST, STATE_INIT, EVENT_LOCK, STATE_CREATE);
        add(transitions, BIZ_GROUP_BUY_ORDER_LIST, STATE_CREATE, EVENT_PAY_SUCCESS, STATE_COMPLETE);
        add(transitions, BIZ_GROUP_BUY_ORDER_LIST, STATE_CREATE, EVENT_TIMEOUT_RELEASE, STATE_CLOSE);
        add(transitions, BIZ_GROUP_BUY_ORDER_LIST, STATE_COMPLETE, EVENT_REFUND_SUCCESS, STATE_CLOSE);
        addAfterSaleTransitions(transitions, BIZ_GROUP_BUY_ORDER_LIST, STATE_REFUND);

        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_INIT, EVENT_OPEN_TEAM, STATE_PROGRESS);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_PROGRESS, EVENT_TEAM_FORMED, STATE_COMPLETE);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_PROGRESS, EVENT_TEAM_TIMEOUT, STATE_FAIL);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_PROGRESS, EVENT_REFUND_SUCCESS, STATE_PROGRESS);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_COMPLETE, EVENT_TEAM_REFUND_PARTIAL, STATE_COMPLETE_FAIL);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_COMPLETE, EVENT_TEAM_REFUND_ALL, STATE_FAIL);
        add(transitions, BIZ_GROUP_BUY_TEAM, STATE_COMPLETE_FAIL, EVENT_TEAM_REFUND_ALL, STATE_FAIL);
        return Collections.unmodifiableSet(transitions);
    }

    private static void addAfterSaleTransitions(Set<String> transitions, String bizType, String finalRefundState) {
        add(transitions, bizType, STATE_COMPLETE, EVENT_FULFILL, STATE_FULFILLED);
        add(transitions, bizType, STATE_COMPLETE, EVENT_REFUND_APPLY, STATE_REFUNDING);
        add(transitions, bizType, STATE_FULFILLED, EVENT_REFUND_APPLY, STATE_REFUNDING);
        add(transitions, bizType, STATE_REFUNDING, EVENT_REFUND_PARTIAL_SUCCESS, STATE_PARTIAL_REFUND);
        add(transitions, bizType, STATE_REFUNDING, EVENT_REFUND_SUCCESS, finalRefundState);
        add(transitions, bizType, STATE_REFUNDING, EVENT_REFUND_REJECT, STATE_REFUND_REJECTED);
        add(transitions, bizType, STATE_PARTIAL_REFUND, EVENT_REFUND_SUCCESS, finalRefundState);
    }

    private static void add(Set<String> transitions, String bizType, String fromStatus, String event, String toStatus) {
        transitions.add(transitionOf(bizType, fromStatus, event, toStatus));
    }

    private static String transitionOf(String bizType, String fromStatus, String event, String toStatus) {
        return String.valueOf(bizType) + "|" + String.valueOf(fromStatus) + "|" + String.valueOf(event) + "|" + String.valueOf(toStatus);
    }

}
