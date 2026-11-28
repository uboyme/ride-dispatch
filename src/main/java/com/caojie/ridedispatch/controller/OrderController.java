package com.caojie.ridedispatch.controller;

import com.caojie.ridedispatch.common.Result;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单", description = "下单、接单、行程推进、取消")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @Operation(summary = "乘客下单", description = "创建订单并立即尝试派单；附近没有空闲司机时，订单停在待派单，由定时任务重试")
    @PostMapping
    public Result<RideOrder> create(@Valid @RequestBody OrderService.CreateOrderRequest req) {
        return Result.ok(orderService.create(req));
    }

    @Operation(summary = "司机接单", description = "只有被派到这一单的司机才能接单；派单后 15 秒内不接单，订单会被改派")
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.accept(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "司机拒单", description = "订单退回待派单并重新派单，拒单的司机不会再收到这一单")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.reject(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "出发去接乘客", description = "已接单 → 接驾中")
    @PostMapping("/{id}/depart")
    public Result<Void> depart(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.depart(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "到达上车点", description = "接驾中 → 司机已到达")
    @PostMapping("/{id}/arrive")
    public Result<Void> arrive(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.arrive(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "乘客上车", description = "司机已到达 → 行程中")
    @PostMapping("/{id}/pickup")
    public Result<Void> pickUp(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.pickUp(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "完成行程", description = "行程中 → 已完成，司机恢复空闲")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.complete(id, driverId);
        return Result.ok();
    }

    @Operation(summary = "乘客取消订单", description = "待派单、已派单免费；接单 3 分钟后或司机到达后取消收 5 元；行程中不能取消")
    @PostMapping("/{id}/cancel")
    public Result<RideOrder> cancel(@PathVariable Long id, @RequestParam Long passengerId) {
        return Result.ok(orderService.cancel(id, passengerId));
    }
}