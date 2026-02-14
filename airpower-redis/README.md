# AirPower Redis 模块

## 概述
AirPower Redis模块是AirPower4J框架中的Redis缓存模块，提供统一的Redis缓存操作功能。

## 功能特性
- 统一Redis操作接口
- 支持多种数据结构
- 缓存策略管理
- 连接池管理

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-exception`: 异常处理模块
- `spring-boot-starter-data-redis`: Spring Boot Redis启动器
- `redis.clients:jedis`: Jedis客户端

## 使用场景
适用于需要使用Redis进行缓存的业务场景，提供标准化的Redis操作接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。