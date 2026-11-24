package com.caojie.ridedispatch.config;

import com.caojie.ridedispatch.enums.ServiceType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("ride-dispatch")
public record RideDispatchProperties(
        BigDecimal cancellationFee,
        long freeCancellationMinutes,
        double dispatchRadiusKm,
        int candidateDriverLimit,
        String geoKeyPrefix,
        long acceptTimeoutSeconds,
        long dispatchGiveUpMinutes) {

    public String geoKey(ServiceType serviceType) {
        return geoKeyPrefix + serviceType.getCode();
    }
}
