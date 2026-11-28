package com.caojie.ridedispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.caojie.ridedispatch.common.BizException;
import com.caojie.ridedispatch.common.ErrorCode;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.Driver;
import com.caojie.ridedispatch.entity.OrderReject;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.enums.*;
import com.caojie.ridedispatch.mapper.DriverMapper;
import com.caojie.ridedispatch.mapper.OrderRejectMapper;
import com.caojie.ridedispatch.mapper.RideOrderMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class OrderService {
    private final RideOrderMapper orderMapper;
    private final DriverMapper driverMapper;
    private final OrderRejectMapper rejectMapper;
    private final DispatchService dispatchService;
    private final RideDispatchProperties properties;

    public record CreateOrderRequest(
            @Schema(description = "乘客 ID", example = "1001")
            @NotNull(message = "不能为空") Long passengerId,
            @Schema(description = "服务类型：1 快车，2 专车", example = "1")
            @NotNull(message = "不能为空") ServiceType serviceType,
            @Schema(description = "起点经度", example = "116.400")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-180", message = "必须在 -180 到 180 之间")
            @DecimalMax(value = "180", message = "必须在 -180 到 180 之间") BigDecimal startLng,
            @Schema(description = "起点纬度", example = "39.910")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-90", message = "必须在 -90 到 90 之间")
            @DecimalMax(value = "90", message = "必须在 -90 到 90 之间") BigDecimal startLat,
            @Schema(description = "起点地址", example = "天安门")
            @NotBlank(message = "不能为空")
            @Size(max = 128, message = "最长 128 个字") String startAddress,
            @Schema(description = "终点经度", example = "116.480")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-180", message = "必须在 -180 到 180 之间")
            @DecimalMax(value = "180", message = "必须在 -180 到 180 之间") BigDecimal endLng,
            @Schema(description = "终点纬度", example = "39.990")
            @NotNull(message = "不能为空")
            @DecimalMin(value = "-90", message = "必须在 -90 到 90 之间")
            @DecimalMax(value = "90", message = "必须在 -90 到 90 之间") BigDecimal endLat,
            @Schema(description = "终点地址", example = "望京")
            @NotBlank(message = "不能为空")
            @Size(max = 128, message = "最长 128 个字") String endAddress) {}

    // 乘客下单：创建订单，然后立刻派单
    // 不加事务：订单先落库，派单失败时订单停在待派单，由定时任务重试
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

        // 调用另一个 Bean 的方法，经过 Spring 代理，dispatch 上的事务才会生效
        dispatchService.dispatch(order.getId());
        return orderMapper.selectById(order.getId());
    }

    // 接单：已派单 → 已接单，必须是派给他的那一单
    @Transactional
    public void accept(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.ACCEPTED)
                .set(RideOrder::getAcceptedAt, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "当前订单不能接单");

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.IN_SERVICE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.DISPATCHED));
    }

    // 拒单：记录拒单，订单退回待派单，司机放回空闲，然后重新派单
    @Transactional
    public void reject(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH)
                .set(RideOrder::getDriverId, null)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "当前订单不能拒单");

        OrderReject reject = new OrderReject();
        reject.setOrderId(orderId);
        reject.setDriverId(driverId);
        reject.setReason(RejectReason.DRIVER_REJECTED);
        rejectMapper.insert(reject);

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.AVAILABLE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.DISPATCHED));

        // 加入当前拒单事务一起提交
        dispatchService.dispatch(orderId);
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
        if (rows == 0) throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "当前状态不能执行该操作");
    }

    // 出发去接乘客：已接单 → 接驾中
    public void depart(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.EN_ROUTE_TO_PICKUP)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.ACCEPTED)
                .eq(RideOrder::getDriverId, driverId));
        if (rows == 0) throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "当前状态不能出发");
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
    @Transactional
    public void complete(Long orderId, Long driverId) {
        advance(orderId, driverId, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, RideOrder::getCompletedAt);
        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.AVAILABLE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.IN_SERVICE));
    }


    // 乘客取消订单
    @Transactional
    public RideOrder cancel(Long orderId, Long passengerId) {
        RideOrder order = orderMapper.selectById(orderId);
        // 只能取消自己的订单
        if (order == null || !order.getPassengerId().equals(passengerId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        OrderStatus status = order.getStatus();
        if (!status.canCancel()) throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "当前状态不能取消");

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
        if (rows == 0) throw new BizException(ErrorCode.ORDER_CHANGED);

        // 有司机的话，把司机放回空闲
        if (order.getDriverId() != null) {
            driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                    .set(Driver::getStatus, DriverStatus.AVAILABLE)
                    .eq(Driver::getId, order.getDriverId())
                    .in(Driver::getStatus, DriverStatus.DISPATCHED, DriverStatus.IN_SERVICE));
        }
        return orderMapper.selectById(orderId);
    }

    // 接单超时：订单退回待派单，记一条超时拒单，司机设为离线
    // 司机不响应多半是没信号或人走开了，设为离线避免继续给他派单；他回来后需要手动重新上线
    @Transactional
    public void handleAcceptTimeout(Long orderId, Long driverId) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH)
                .set(RideOrder::getDriverId, null)
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .eq(RideOrder::getDriverId, driverId));
        // 影响 0 行：司机刚好接单了，或者订单被取消了，都不用处理
        if (rows == 0) return;

        OrderReject reject = new OrderReject();
        reject.setOrderId(orderId);
        reject.setDriverId(driverId);
        reject.setReason(RejectReason.TIMEOUT);
        rejectMapper.insert(reject);

        driverMapper.update(null, new LambdaUpdateWrapper<Driver>()
                .set(Driver::getStatus, DriverStatus.OFFLINE)
                .eq(Driver::getId, driverId)
                .eq(Driver::getStatus, DriverStatus.DISPATCHED));
    }

    // 系统取消：待派单太久仍没有司机
    public void systemCancel(Long orderId) {
        orderMapper.update(null, new LambdaUpdateWrapper<RideOrder>()
                .set(RideOrder::getStatus, OrderStatus.CANCELLED)
                .set(RideOrder::getCancelBy, CancelBy.SYSTEM)
                .set(RideOrder::getCancelReason, "长时间无司机接单")
                .set(RideOrder::getCancelFee, BigDecimal.ZERO)
                .set(RideOrder::getCancelledAt, LocalDateTime.now())
                .eq(RideOrder::getId, orderId)
                .eq(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH));
    }

    // 计算取消费：状态决定能不能取消，时间决定收不收钱
    private BigDecimal calcCancelFee(RideOrder order) {
        LocalDateTime now = LocalDateTime.now();
        OrderStatus status = order.getStatus();
        if (status.hasFreeCancellationWindow()
                && order.getAcceptedAt().plusMinutes(properties.freeCancellationMinutes()).isBefore(now)) {
            return properties.cancellationFee();
        }
        // 司机已到达，取消直接收费
        if (status == OrderStatus.ARRIVED) {
            return properties.cancellationFee();
        }
        return BigDecimal.ZERO;
    }

}
