package com.caojie.ridedispatch.controller;

import com.caojie.ridedispatch.common.Result;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public Result<RideOrder> create(@RequestBody OrderService.CreateOrderRequest req) {
        return Result.ok(orderService.create(req));
    }

    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.accept(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.reject(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/depart")
    public Result<Void> depart(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.depart(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/arrive")
    public Result<Void> arrive(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.arrive(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/pickup")
    public Result<Void> pickUp(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.pickUp(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.complete(id, driverId);
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    public Result<RideOrder> cancel(@PathVariable Long id, @RequestParam Long passengerId) {
        return Result.ok(orderService.cancel(id, passengerId));
    }
}