package com.caojie.ridedispatch.controller;

import com.caojie.ridedispatch.common.BizException;
import com.caojie.ridedispatch.common.ErrorCode;
import com.caojie.ridedispatch.common.Result;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.mapper.DriverMapper;
import com.caojie.ridedispatch.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {
    private final DriverMapper driverMapper;
    private final DriverService driverService;

    public record LocationRequest(double lng, double lat) {}

    @PostMapping
    public Result<Driver> create(@RequestBody Driver driver) {
        driverMapper.insert(driver);
        return Result.ok(driver);
    }

    @GetMapping("/{id}")
    public Result<Driver> get(@PathVariable Long id) {
        Driver driver = driverMapper.selectById(id);
        if (driver == null) throw new BizException(ErrorCode.DRIVER_NOT_FOUND);
        return Result.ok(driver);
    }

    @PostMapping("/{id}/online")
    public Result<Void> online(@PathVariable Long id, @RequestBody LocationRequest req) {
        driverService.online(id, req.lng(), req.lat());
        return Result.ok();
    }

    @PostMapping("/{id}/location")
    public Result<Void> location(@PathVariable Long id, @RequestBody LocationRequest req) {
        driverService.reportLocation(id, req.lng(), req.lat());
        return Result.ok();
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        driverService.offline(id);
        return Result.ok();
    }
}