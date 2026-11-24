package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.entity.OrderReject;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.enums.DriverStatus;
import com.caojie.ridedispatch.enums.OrderStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 派单服务。
 * 从 OrderService 中拆出来，是为了让其他 Bean 调用 dispatch() 时经过 Spring 代理，
 * 使 @Transactional 生效（同一个类内部调用会绕过代理，事务不生效）。
 */
@Service
@RequiredArgsConstructor
public class DispatchService {
    private final RideOrderMapper orderMapper;
    private final DriverMapper driverMapper;
    private final OrderRejectMapper rejectMapper;
    private final StringRedisTemplate redis;
    private final RideDispatchProperties properties;

    // 贪心派单：从近到远依次尝试，成功一个就停。
    // 事务保证"锁定司机"和"更新订单"同时成功或同时回滚，避免司机卡在已派单。
    // 注意：Redis 的 GEO 操作不会随事务回滚，司机被误移出 GEO 时，靠下次位置上报自动补回。
    @Transactional
    public boolean dispatch(Long orderId) {
        RideOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_DISPATCH) return false;

        String key = properties.geoKey(order.getServiceType());
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(order.getStartLng().doubleValue(),
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
}
