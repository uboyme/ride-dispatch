package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.caojie.ridedispatch.common.BizException;
import com.caojie.ridedispatch.common.ErrorCode;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.enums.DriverStatus;
import com.caojie.ridedispatch.enums.ServiceType;
import com.caojie.ridedispatch.mapper.DriverMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class DriverService {
    private final DriverMapper driverMapper;
    private final StringRedisTemplate redis;
    private final RideDispatchProperties properties;
    public record CreateDriverRequest(
            @Schema(description = "姓名", example = "王五")
            @NotBlank(message = "不能为空") @Size(max = 32, message = "最长 32 个字") String name,
            @Schema(description = "手机号", example = "13800000003")
            @NotBlank(message = "不能为空")
            @Pattern(regexp = "^1\\d{10}$", message = "格式不正确") String phone,
            @Schema(description = "车牌号", example = "京C11111")
            @NotBlank(message = "不能为空") @Size(max = 16, message = "最长 16 个字") String carPlate,
            @Schema(description = "车型", example = "比亚迪秦")
            @NotBlank(message = "不能为空") @Size(max = 32, message = "最长 32 个字") String carModel,
            @Schema(description = "车身颜色", example = "白色")
            @NotBlank(message = "不能为空") @Size(max = 16, message = "最长 16 个字") String carColor,
            @Schema(description = "服务类型：1 快车，2 专车", example = "1")
            @NotNull(message = "不能为空") ServiceType serviceType) {}

    // 新增司机：状态固定为离线，必须走上线流程才能接单
    public Driver create(CreateDriverRequest req) {
        Driver driver = new Driver();
        driver.setName(req.name());
        driver.setPhone(req.phone());
        driver.setCarPlate(req.carPlate());
        driver.setCarModel(req.carModel());
        driver.setCarColor(req.carColor());
        driver.setServiceType(req.serviceType());
        driver.setStatus(DriverStatus.OFFLINE);
        try {
            driverMapper.insert(driver);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        return driver;
    }

    // 查询司机
    public Driver get(Long id) {
        Driver driver = driverMapper.selectById(id);
        if (driver == null) throw new BizException(ErrorCode.DRIVER_NOT_FOUND);
        return driver;
    }
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
