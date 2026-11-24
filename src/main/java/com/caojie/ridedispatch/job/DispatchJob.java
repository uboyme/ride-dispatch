package com.caojie.ridedispatch.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caojie.ridedispatch.config.RideDispatchProperties;
import com.caojie.ridedispatch.entity.RideOrder;
import com.caojie.ridedispatch.enums.OrderStatus;
import com.caojie.ridedispatch.mapper.RideOrderMapper;
import com.caojie.ridedispatch.service.DispatchService;
import com.caojie.ridedispatch.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 派单定时任务：处理接单超时，并重试派不出去的订单。
 * v1 用定时扫描实现，最多有一个扫描间隔的延迟；v3 计划换成延迟消息，到点精确触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchJob {
    private static final int BATCH_SIZE = 100;

    private final RideOrderMapper orderMapper;
    private final OrderService orderService;
    private final DispatchService dispatchService;
    private final RideDispatchProperties properties;

    // 上一轮执行完，隔一个扫描间隔再执行下一轮，不会两轮重叠
    @Scheduled(fixedDelayString = "${ride-dispatch.scan-interval-ms:5000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        handleAcceptTimeouts(now);
        retryPendingOrders(now);
    }

    // 1. 派出去超过超时时间还没接单的订单
    private void handleAcceptTimeouts(LocalDateTime now) {
        List<RideOrder> timeouts = orderMapper.selectList(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getStatus, OrderStatus.DISPATCHED)
                .lt(RideOrder::getDispatchedAt, now.minusSeconds(properties.acceptTimeoutSeconds()))
                .last("LIMIT " + BATCH_SIZE));
        for (RideOrder order : timeouts) {
            try {
                orderService.handleAcceptTimeout(order.getId(), order.getDriverId());
            } catch (Exception e) {
                // 单个订单出错不影响其他订单，下一轮会再试
                log.warn("处理接单超时失败 orderId={}", order.getId(), e);
            }
        }
    }

    // 2. 还在待派单的订单：太久就系统取消，否则重试派单
    // 查询条件 status + ORDER BY created_at 正好用上联合索引 idx_status_created
    private void retryPendingOrders(LocalDateTime now) {
        LocalDateTime giveUpBefore = now.minusMinutes(properties.dispatchGiveUpMinutes());
        List<RideOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getStatus, OrderStatus.PENDING_DISPATCH)
                .orderByAsc(RideOrder::getCreatedAt)
                .last("LIMIT " + BATCH_SIZE));
        for (RideOrder order : pending) {
            try {
                if (order.getCreatedAt().isBefore(giveUpBefore)) {
                    orderService.systemCancel(order.getId());
                } else {
                    dispatchService.dispatch(order.getId());
                }
            } catch (Exception e) {
                log.warn("重试派单失败 orderId={}", order.getId(), e);
            }
        }
    }
}
