# AirPower MQTT 模块

## 概述
AirPower MQTT模块是AirPower4J框架中的MQTT消息中间件模块，提供统一的MQTT消息处理功能。

## 功能特性
- 统一MQTT消息接口
- 支持发布订阅模式
- 消息队列管理
- 连接状态监控

## 依赖关系
- `airpower-core`: 核心功能模块
- `lombok`: 代码简化工具
- `spring-integration-mqtt`: Spring集成MQTT
- `spring-boot`: Spring Boot核心
- `spring-boot-autoconfigure`: Spring Boot自动配置

## 使用场景
适用于需要使用MQTT协议进行消息通信的业务场景，提供标准化的MQTT消息处理接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。