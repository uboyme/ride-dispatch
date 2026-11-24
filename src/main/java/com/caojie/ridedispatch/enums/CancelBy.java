package com.caojie.ridedispatch.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelBy {
    PASSENGER(1, "乘客"),
    DRIVER(2, "司机"),
    SYSTEM(3, "系统");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}
