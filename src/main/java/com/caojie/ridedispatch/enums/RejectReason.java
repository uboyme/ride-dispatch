package com.caojie.ridedispatch.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RejectReason {
    DRIVER_REJECTED(1, "主动拒单"),
    TIMEOUT(2, "超时未响应");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}
