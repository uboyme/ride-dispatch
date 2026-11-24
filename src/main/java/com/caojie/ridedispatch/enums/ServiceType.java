package com.caojie.ridedispatch.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ServiceType {
    EXPRESS(1, "快车"),
    PREMIUM(2, "专车");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}
