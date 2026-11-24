package com.caojie.ridedispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.caojie.ridedispatch.enums.RejectReason;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_reject")
public class OrderReject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long driverId;
    private RejectReason reason;
    private LocalDateTime createdAt;
}
