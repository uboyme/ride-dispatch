package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.entity.OrderReject;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.enums.CancelBy;
import com.caojie.ridedispatch.enums.DriverStatus;
import com.caojie.ridedispatch.enums.OrderStatus;
import com.caojie.ridedispatch.enums.RejectReason;
import com.caojie.ridedispatch.enums.ServiceType;
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
    private final RideDispatchProperties properties;

    public record CreateOrderRequest(Long passengerId, ServiceType serviceType,
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
        order.setStatus(OrderStatus.PENDING_DISPATCH);
        orderMapper.insert(order);

        dispatch(order.getId());
        return orderMapper.selectById(order.getId());
    }
    // 贪心派单：从近到远依次尝试，成功一个就停
    public boolean dispatch(Long orderId) {
        RideOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_DISPATCH) return false;

        String key = properties.geoKey(order.getServiceType());
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(order.getStartLng(). doubleValue(),
                        order.getStartLat().doubleValue()),
                new Distance(properties.dispatchRadiusKm(), Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .sortAscending().limit(properties.candidateDriverLimit()));
        if (results == null) return false;
        // 拒过这一单的司机
        Set<Long> rejected = rejectMapper.selectList(
                        new LambdaQueryWrapper<OrderReject>().eq(OrderReject::getOrderId, orderId))
                .stream().map(OrderReject::getDriverId).collect(Collectors.toSet());

        for (var result : results) {
            Long driverId = Long.valueOf(result.getContent().getName());
            if (rejected.contains(driverId)) continue;

            // 锁定司机：空闲 → 已派单，改不成功就换下一个
            int rows = driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                    .set(Driver::getStatus, DriverStatus.DISPATCHED)
                    .eq(Driver::getId, driverId)
                    .eq(Driver::getStatus, DriverStatus.AVAILABLE));
            if (rows == 0) continue;

            redis.opsForGeo().remove(key, driverId.toString());

            // 订单：待派单 → 已派单
            int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                    .set(RideOrder::getStatus, OrderStatus.DISPATCHED)
                    .set(RideOrder::getDriverId, driverId)
                    .eq(RideOrder::getId, orderId)
                    .eq(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH));
            if (orderRows == 0) {
                // 订单在这期间被取消了，把司机放回空闲
                driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                        .set(Driver::getStatus, DriverStatus.AVAILABLE)
                        .eq(Driver::getId, driverId)
                        .eq(Driver::getStatus, DriverStatus.DISPATCHED));
                return false;
            }
            return true;
        }
        return false;
    }

    // 接单：已派单 → 已接单，必须是派给他的那一单
    public void accept(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.ACCEPTED)
                .set(RideOrder::getAcceptedAt, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前订单不能接单");

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.IN_SERVICE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.DISPATCHED));
    }

    // 拒单：记录拒单，订单退回待派单，司机放回空闲，然后重新派单
    public void reject(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH)
                .set(RideOrder::getDriverId, null)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前订单不能拒单");

        OrderReject reject = new OrderReject();
        reject.setOrderId(orderId);
        reject.setDriverId(driverId);
        reject.setReason(RejectReason.DRIVER_REJECTED);
        rejectMapper.insert(reject);

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.AVAILABLE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.DISPATCHED));

        dispatch(orderId);
    }

    // 通用的状态推进：只有当前状态对、并且是这单的司机，才能推进
    private void advance(Long orderId, Long driverId, OrderStatus from, OrderStatus to,
                         SFunction<RideOrder, LocalDateTime> timeField) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, to)
                .set(timeField, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, from)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前状态不能执行该操作");
    }

    // 出发去接乘客：已接单 → 接驾中
    public void depart(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.EN_ROUTE_TO_PICKUP)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.ACCEPTED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new RuntimeException("当前状态不能出发");
    }

    // 到达上车点：接驾中 → 司机已到达
    public void arrive(Long orderId, Long driverId) {
        advance(orderId, driverId, OrderStatus.EN_ROUTE_TO_PICKUP, OrderStatus.ARRIVED, RideOrder::getArrivedAt);
    }

    // 乘客上车：司机已到达 → 行程中
    public void pickUp(Long orderId, Long driverId) {
        advance(orderId, driverId, OrderStatus.ARRIVED, OrderStatus.IN_PROGRESS, RideOrder::getPickedUpAt);
    }

    // 完成行程：行程中 → 已完成，司机恢复空闲
    public void complete(Long orderId, Long driverId) {
        advance(orderId, driverId, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, RideOrder::getCompletedAt);
        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.AVAILABLE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.IN_SERVICE));
    }


    // 乘客取消订单
    public RideOrder cancel(Long orderId, Long passengerId) {
        RideOrder order = orderMapper.selectById(orderId);
        // 只能取消自己的订单
        if (order == null || !order.getPassengerId().equals(passengerId)) {
            throw new RuntimeException("订单不存在");
        }
        OrderStatus status = order.getStatus();
        if (!status.canCancel()) throw new RuntimeException("当前状态不能取消");

        BigDecimal fee = calcCancelFee(order);

        // 条件更新：状态必须还是刚才读到的那个
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.CANCELLED)
                .set(RideOrder::getCancelBy, CancelBy.PASSENGER)
                .set(RideOrder::getCancelReason, "乘客主动取消")
                .set(RideOrder::getCancelFee, fee)
                .set(RideOrder::getCancelledAt, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, status));
        if (rows == 0) throw new RuntimeException("订单状态已变化，请刷新后重试");

        // 有司机的话，把司机放回空闲
        if (order.getDriverId() != null) {
            driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                    .set(Driver::getStatus, DriverStatus.AVAILABLE)
                    .eq(Driver::getId, order.getDriverId())
                    .in(Driver::getStatus, DriverStatus.DISPATCHED, DriverStatus.IN_SERVICE));
        }
        return orderMapper.selectById(orderId);
    }

    // 计算取消费：状态决定能不能取消，时间决定收不收钱
    private BigDecimal calcCancelFee(RideOrder order) {
        LocalDateTime now = LocalDateTime.now();
        OrderStatus status = order.getStatus();
        if (status.hasFreeCancellationWindow()
                && order.getAcceptedAt().plusMinutes(properties.freeCancellationMinutes()).isBefore(now)) {
            return properties.cancellationFee();
        }
        // 改成：司机已到达，直接收费
        if (status == OrderStatus.ARRIVED) {
            return properties.cancellationFee();
        }
        return BigDecimal.ZERO;
    }
    
}
