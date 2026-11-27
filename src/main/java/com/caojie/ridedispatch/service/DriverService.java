package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.caojie.ridedispatch.common.BizException;
import com.caojie.ridedispatch.common.ErrorCode;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.enums.DriverStatus;
import com.caojie.ridedispatch.mapper.DriverMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class DriverService {
    private final DriverMapper driverMapper;
    private final StringRedisTemplate redis;
    private final RideDispatchProperties properties;
    // 上线：离线 → 空闲，再写入 GEO
    public void online(Long driverId, double lng, double lat) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) throw new BizException(ErrorCode.DRIVER_NOT_FOUND);

        int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.AVAILABLE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.OFFLINE));
        if (rows == 0) throw new BizException(ErrorCode.DRIVER_STATUS_CONFLICT, "当前状态不能上线");

        redis.opsForGeo().add(properties.geoKey(driver.getServiceType()),
                new Point(lng, lat), driverId.toString());
    }
    // 上报位置：只有空闲司机才写 GEO
    public void reportLocation(Long driverId, double lng, double lat) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null || driver.getStatus() != DriverStatus.AVAILABLE) return;

        redis.opsForGeo().add(properties.geoKey(driver.getServiceType()),
                new Point(lng, lat), driverId.toString());
    }
    // 下线：空闲 → 离线，再从 GEO 移除
    public void offline(Long driverId) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) throw new BizException(ErrorCode.DRIVER_NOT_FOUND);

        int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.OFFLINE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.AVAILABLE));
        if (rows == 0) throw new BizException(ErrorCode.DRIVER_STATUS_CONFLICT, "当前状态不能下线");

        redis.opsForGeo().remove(properties.geoKey(driver.getServiceType()), driverId.toString());
    }

}
