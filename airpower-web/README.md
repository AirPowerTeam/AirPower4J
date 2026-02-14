# AirPower Web 模块

## 概述
AirPower Web模块是AirPower4J框架中的Web开发核心模块，提供完整的Web开发功能。

## 功能特性
- 统一Web开发接口
- 支持MVC架构模式
- Web安全控制
- 会话管理
- 静态资源处理

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-exception`: 异常处理模块
- `airpower-http`: HTTP客户端模块
- `airpower-curd`: 数据持久化模块
- `spring-data-redis`: Spring Data Redis
- `spring-web`: Spring Web框架
- `spring-webmvc`: Spring Web MVC框架
- `airpower-websocket`: WebSocket模块

## 使用场景
适用于构建基于Spring MVC的Web应用程序，提供标准化的Web开发接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。