package com.caojie.ridedispatch.controller;

import com.caojie.ridedispatch.common.BizException;
import com.caojie.ridedispatch.common.ErrorCode;
import com.caojie.ridedispatch.common.Result;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.mapper.DriverMapper;
import com.caojie.ridedispatch.service.DriverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {
    private final DriverMapper driverMapper;
    private final DriverService driverService;

    public record LocationRequest(
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-180", message = "必须在 -180 到 180 之间")
            @DecimalMax(value = "180", message = "必须在 -180 到 180 之间") Double lng,
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-90", message = "必须在 -90 到 90 之间")
            @DecimalMax(value = "90", message = "必须在 -90 到 90 之间") Double lat) {}

    @PostMapping
    public Result<Driver> create(@Valid @RequestBody DriverService.CreateDriverRequest req) {
        return Result.ok(driverService.create(req));
    }

    @GetMapping("/{id}")
    public Result<Driver> get(@PathVariable Long id) {
        return Result.ok(driverService.get(id));
    }

    @PostMapping("/{id}/online")
    public Result<Void> online(@PathVariable Long id, @Valid @RequestBody LocationRequest req) {
        driverService.online(id, req.lng(), req.lat());
        return Result.ok();
    }

    @PostMapping("/{id}/location")
    public Result<Void> location(@PathVariable Long id, @Valid @RequestBody LocationRequest req) {
        driverService.reportLocation(id, req.lng(), req.lat());
        return Result.ok();
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        driverService.offline(id);
        return Result.ok();
    }
}