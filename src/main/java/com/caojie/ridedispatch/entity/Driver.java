package com.caojie.ridedispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.caojie.ridedispatch.enums.DriverStatus;
import com.caojie.ridedispatch.enums.ServiceType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("driver")
public class Driver {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String phone;
    private String carPlate;
    private String carModel;
    private String carColor;
    private ServiceType serviceType;
    private DriverStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
