package com.caojie.ridedispatch.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING_DISPATCH(10, "待派单"),
    DISPATCHED(20, "已派单"),
    ACCEPTED(30, "已接单"),
    EN_ROUTE_TO_PICKUP(40, "接驾中"),
    ARRIVED(50, "司机已到达"),
    IN_PROGRESS(60, "行程中"),
    COMPLETED(70, "已完成"),
    CANCELLED(80, "已取消");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;

    public boolean canCancel() {
        return code < IN_PROGRESS.code;
    }

    public boolean hasFreeCancellationWindow() {
        return this == ACCEPTED || this == EN_ROUTE_TO_PICKUP;
    }
}
