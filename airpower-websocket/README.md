# AirPower WebSocket 模块

## 概述
AirPower WebSocket模块是AirPower4J框架中的WebSocket通信模块，提供统一的实时通信功能。

## 功能特性
- 统一WebSocket接口
- 支持双向实时通信
- 连接状态管理
- 消息广播功能

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-api`: API开发模块
- `airpower-mqtt`: MQTT消息模块
- `airpower-redis`: Redis缓存模块
- `airpower-exception`: 异常处理模块
- `spring-websocket`: Spring WebSocket框架

## 使用场景
适用于需要实时双向通信的业务场景，提供标准化的WebSocket通信接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。