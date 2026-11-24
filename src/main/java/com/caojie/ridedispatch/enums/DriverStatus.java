package com.caojie.ridedispatch.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DriverStatus {
    OFFLINE(0, "离线"),
    AVAILABLE(1, "空闲"),
    DISPATCHED(2, "已派单"),
    IN_SERVICE(3, "服务中");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}
