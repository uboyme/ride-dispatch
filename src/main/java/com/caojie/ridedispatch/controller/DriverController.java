package com.caojie.ridedispatch.controller;


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
    @PostMapping
    public Driver create(@RequestBody Driver driver) {
        driverMapper.insert(driver);
        return driver;
    }
    @GetMapping("/{id}")
    public Driver get(@PathVariable Long id) {
        return driverMapper.selectById(id);
    }
    private final DriverService driverService;

    public record LocationRequest(double lng, double lat) {}

    @PostMapping("/{id}/online")
    public String online(@PathVariable Long id, @RequestBody LocationRequest req) {
        driverService.online(id, req.lng(), req.lat());
        return "ok";
    }

    @PostMapping("/{id}/location")
    public String location(@PathVariable Long id, @RequestBody LocationRequest req) {
        driverService.reportLocation(id, req.lng(), req.lat());
        return "ok";
    }

    @PostMapping("/{id}/offline")
    public String offline(@PathVariable Long id) {
        driverService.offline(id);
        return "ok";
    }
}

