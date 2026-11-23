package com.caojie.ridedispatch.controller;

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
    public RideOrder create(@RequestBody OrderService.CreateOrderRequest req) {
        return orderService.create(req);
    }
    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.accept(id, driverId);
        return "ok";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.reject(id, driverId);
        return "ok";
    }

    @PostMapping("/{id}/depart")
    public String depart(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.depart(id, driverId); return "ok";
    }

    @PostMapping("/{id}/arrive")
    public String arrive(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.arrive(id, driverId); return "ok";
    }

    @PostMapping("/{id}/pickup")
    public String pickUp(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.pickUp(id, driverId); return "ok";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam Long driverId) {
        orderService.complete(id, driverId); return "ok";
    }
}
