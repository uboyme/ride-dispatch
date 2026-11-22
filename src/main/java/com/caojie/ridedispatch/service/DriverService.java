package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.caojie.ridedispatch.entity.Driver;
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
    private String geoKey(Integer serviceType) {
        return "driver:geo:" + serviceType;
    }
    // 上线：离线(0) → 空闲(1)，再写入 GEO
    public void online(Long driverId, double lng, double lat) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) throw new RuntimeException("司机不存在");

        int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, 1)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, 0));
        if (rows == 0) throw new RuntimeException("当前状态不能上线");

        redis.opsForGeo().add(geoKey(driver.getServiceType()),
                new Point(lng, lat), driverId.toString());
    }
    // 上报位置：只有空闲司机才写 GEO
    public void reportLocation(Long driverId, double lng, double lat) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null || driver.getStatus() != 1) return;

        redis.opsForGeo().add(geoKey(driver.getServiceType()),
                new Point(lng, lat), driverId.toString());
    }
    // 下线：空闲(1) → 离线(0)，再从 GEO 移除
    public void offline(Long driverId) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) throw new RuntimeException("司机不存在");

        int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, 0)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, 1));
        if (rows == 0) throw new RuntimeException("当前状态不能下线");

        redis.opsForGeo().remove(geoKey(driver.getServiceType()), driverId.toString());
    }

}
