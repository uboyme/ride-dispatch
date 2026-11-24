package com.caojie.ridedispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.caojie.ridedispatch.enums.CancelBy;
import com.caojie.ridedispatch.enums.OrderStatus;
import com.caojie.ridedispatch.enums.ServiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ride_order")
public class RideOrder {
    private Long id;
    private Long passengerId;
    private Long driverId;
    private ServiceType serviceType;
    private OrderStatus status;
    private BigDecimal startLng;
    private BigDecimal startLat;
    private String startAddress;
    private BigDecimal endLng;
    private BigDecimal endLat;
    private String endAddress;
    private BigDecimal estimatedFee;
    private CancelBy cancelBy;
    private String cancelReason;
    private BigDecimal cancelFee;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime updatedAt;
}
