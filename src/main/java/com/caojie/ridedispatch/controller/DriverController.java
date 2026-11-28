package com.caojie.ridedispatch.controller;

import com.caojie.ridedispatch.common.Result;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.service.DriverService;
import com.caojie.ridedispatch.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "司机", description = "司机注册、上下线、位置上报")
@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {
    private final DriverService driverService;
    private final OrderService orderService;

    public record LocationRequest(
            @Schema(description = "经度", example = "116.397")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-180", message = "必须在 -180 到 180 之间")
            @DecimalMax(value = "180", message = "必须在 -180 到 180 之间") Double lng,
            @Schema(description = "纬度", example = "39.908")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-90", message = "必须在 -90 到 90 之间")
            @DecimalMax(value = "90", message = "必须在 -90 到 90 之间") Double lat) {}

    @Operation(summary = "新增司机", description = "新司机默认离线，需要调用上线接口才能接单")
    @PostMapping
    public Result<Driver> create(@Valid @RequestBody DriverService.CreateDriverRequest req) {
        return Result.ok(driverService.create(req));
    }

    @Operation(summary = "查询司机")
    @GetMapping("/{id}")
    public Result<Driver> get(@PathVariable Long id) {
        return Result.ok(driverService.get(id));
    }

    @Operation(summary = "司机上线", description = "离线 → 空闲，并加入附近司机的搜索范围")
    @PostMapping("/{id}/online")
    public Result<Void> online(@PathVariable Long id, @Valid @RequestBody LocationRequest req) {
        driverService.online(id, req.lng(), req.lat());
        return Result.ok();
    }

    @Operation(summary = "上报位置", description = "建议每 3 秒调用一次；只有空闲司机的位置会被写入")
    @PostMapping("/{id}/location")
    public Result<Void> location(@PathVariable Long id, @Valid @RequestBody LocationRequest req) {
        driverService.reportLocation(id, req.lng(), req.lat());
        return Result.ok();
    }

    @Operation(summary = "司机下线", description = "只有空闲状态才能下线")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        driverService.offline(id);
        return Result.ok();
    }

    @Operation(summary = "查询司机当前订单", description = "司机端轮询调用；没有进行中的订单时 data 为 null")
    @GetMapping("/{id}/current-order")
    public Result<RideOrder> currentOrder(@Parameter(description = "司机 ID", example = "1") @PathVariable Long id) {
        return Result.ok(orderService.currentOrderOfDriver(id));
    }
}