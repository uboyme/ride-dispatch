package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.entity.OrderReject;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.mapper.DriverMapper;
import com.caojie.ridedispatch.mapper.OrderRejectMapper;
import com.caojie.ridedispatch.mapper.RideOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final RideOrderMapper orderMapper;
    private final DriverMapper driverMapper;
    private final OrderRejectMapper rejectMapper;
    private final StringRedisTemplate redis;
    public record CreateOrderRequest(Long passengerId, Integer serviceType,
                                      BigDecimal startLng, BigDecimal startLat, String startAddress,
                                      BigDecimal endLng, BigDecimal endLat, String endAddress){}

    // 乘客下单：创建订单，然后立刻派单

    public RideOrder create(CreateOrderRequest req) {
        RideOrder order = new RideOrder();
        order.setPassengerId(req.passengerId());
        order.setServiceType(req.serviceType());
        order.setStartLng(req.startLng());
        order.setStartLat(req.startLat());
        order.setStartAddress(req.startAddress());
        order.setEndLng(req.endLng());
        order.setEndLat(req.endLat());
        order.setEndAddress(req.endAddress());
        order.setStatus(10);
        orderMapper.insert(order);

        dispatch(order.getId());
        return orderMapper.selectById(order.getId());
    }
    // 贪心派单：从近到远依次尝试，成功一个就停
    public boolean dispatch(Long orderId) {
        RideOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 10) return false;

        String key = "driver:geo:" + order.getServiceType();
        //GEOSEARCH driver:geo:1 FROMLONLAT 116.400 39.910 BYRADIUS 3 km ASC COUNT 10
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(order.getStartLng(). doubleValue(),
                        order.getStartLat().doubleValue()),
                new Distance(3, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .sortAscending().limit(10));
        if (results == null) return false;
        // 拒过这一单的司机
        Set<Long> rejected = rejectMapper.selectList(
                        new LambdaQueryWrapper<OrderReject>().eq(OrderReject::getOrderId, orderId))
                .stream().map(OrderReject::getDriverId).collect(Collectors.toSet());

        for (var result : results) {
            Long driverId = Long.valueOf(result.getContent().getName());
            if (rejected.contains(driverId)) continue;

            // 锁定司机：空闲(1) → 已派单(2)，改不成功就换下一个
            int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                    .set(Driver::getStatus, 2)
                    .eq(Driver::getId, driverId)
                    .eq(Driver::getStatus, 1));
            if (rows == 0) continue;

            redis.opsForGeo().remove(key, driverId.toString());

            // 订单：待派单(10) → 已派单(20)
            int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                    .set(RideOrder::getStatus, 20)
                    .set(RideOrder::getDriverId, driverId)
                    .eq(RideOrder::getId, orderId)
                    .eq(RideOrder::getStatus, 10));
            if (orderRows == 0) {
                // 订单在这期间被取消了，把司机放回空闲
                driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                        .set(Driver::getStatus, 1)
                        .eq(Driver::getId, driverId)
                        .eq(Driver::getStatus, 2));
                return false;
            }
            return true;
        }
        return false;
    }

    // 接单：已派单(20) → 已接单(30)，必须是派给他的那一单
    public void accept(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, 30)
                .set(RideOrder::getAcceptedAt, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, 20)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前订单不能接单");

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, 3)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, 2));
    }

    // 拒单：记录拒单，订单退回待派单，司机放回空闲，然后重新派单
    public void reject(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, 10)
                .set(RideOrder::getDriverId, null)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, 20)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前订单不能拒单");

        OrderReject reject = new OrderReject();
        reject.setOrderId(orderId);
        reject.setDriverId(driverId);
        reject.setReason(1);
        rejectMapper.insert(reject);

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, 1)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, 2));

        dispatch(orderId);
    }

    // 通用的状态推进：只有当前状态对、并且是这单的司机，才能推进
    private void advance(Long orderId, Long driverId, int from, int to,
                         SFunction<RideOrder, LocalDateTime> timeField) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, to)
                .set(timeField, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, from)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前状态不能执行该操作");
    }

    // 出发去接乘客：已接单(30) → 接驾中(40)
    public void depart(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, 40)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, 30)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前状态不能出发");
    }

    // 到达上车点：接驾中(40) → 司机已到达(50)
    public void arrive(Long orderId, Long driverId) {
        advance(orderId, driverId, 40, 50, RideOrder::getArrivedAt);
    }

    // 乘客上车：司机已到达(50) → 行程中(60)
    public void pickUp(Long orderId, Long driverId) {
        advance(orderId, driverId, 50, 60, RideOrder::getPickedUpAt);
    }

    // 完成行程：行程中(60) → 已完成(70)，司机恢复空闲
    public void complete(Long orderId, Long driverId) {
        advance(orderId, driverId, 60, 70, RideOrder::getCompletedAt);
        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, 1)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, 3));
    }
    
}
