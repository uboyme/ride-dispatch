-- 网约车派单系统建表语句（与当前代码保持一致）
-- 新环境（如云服务器）直接执行本文件；已有数据库的变更见 docs/PROGRESS.md 的"数据库变更记录"

CREATE DATABASE IF NOT EXISTS ride_dispatch DEFAULT CHARACTER SET utf8mb4;
USE ride_dispatch;

CREATE TABLE IF NOT EXISTS driver (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '司机ID',
  name         VARCHAR(32)  NOT NULL COMMENT '姓名',
  phone        VARCHAR(20)  NOT NULL COMMENT '手机号',
  car_plate    VARCHAR(16)  NOT NULL COMMENT '车牌号',
  car_model    VARCHAR(32)  NOT NULL COMMENT '车型',
  car_color    VARCHAR(16)  NOT NULL COMMENT '车身颜色',
  service_type TINYINT      NOT NULL COMMENT '1快车 2专车',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0离线 1空闲 2已派单 3服务中',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_phone (phone)
) COMMENT '司机表';

CREATE TABLE IF NOT EXISTS ride_order (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  passenger_id  BIGINT        NOT NULL COMMENT '乘客ID',
  driver_id     BIGINT        NULL COMMENT '司机ID，派单后才有',
  service_type  TINYINT       NOT NULL COMMENT '1快车 2专车',
  status        TINYINT       NOT NULL DEFAULT 10 COMMENT '10待派单 20已派单 30已接单 40接驾中 50司机已到达 60行程中 70已完成 80已取消',
  start_lng     DECIMAL(10,6) NOT NULL,
  start_lat     DECIMAL(10,6) NOT NULL,
  start_address VARCHAR(128)  NOT NULL,
  end_lng       DECIMAL(10,6) NOT NULL,
  end_lat       DECIMAL(10,6) NOT NULL,
  end_address   VARCHAR(128)  NOT NULL,
  estimated_fee DECIMAL(10,2) NULL COMMENT '预估费用',
  cancel_by     TINYINT       NULL COMMENT '1乘客 2司机 3系统',
  cancel_reason VARCHAR(64)   NULL,
  cancel_fee    DECIMAL(10,2) NULL COMMENT '取消费',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  dispatched_at DATETIME      NULL COMMENT '派单时间',
  accepted_at   DATETIME      NULL,
  arrived_at    DATETIME      NULL,
  picked_up_at  DATETIME      NULL,
  completed_at  DATETIME      NULL,
  cancelled_at  DATETIME      NULL,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_passenger (passenger_id),
  KEY idx_driver (driver_id),
  KEY idx_status_created (status, created_at)
) COMMENT '订单表';

CREATE TABLE IF NOT EXISTS order_reject (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id   BIGINT   NOT NULL,
  driver_id  BIGINT   NOT NULL,
  reason     TINYINT  NOT NULL COMMENT '1主动拒单 2超时未响应',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_driver (order_id, driver_id)
) COMMENT '拒单记录表';
