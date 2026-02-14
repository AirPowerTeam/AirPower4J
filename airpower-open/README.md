# AirPower Open 模块

## 概述
AirPower Open模块是AirPower4J框架中的开放平台模块，提供API开放和权限控制功能。

## 功能特性
- 统一API开放接口
- 权限认证机制
- API访问控制
- AOP切面编程支持

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-exception`: 异常处理模块
- `airpower-api`: API开发模块
- `airpower-http`: HTTP客户端模块
- `airpower-redis`: Redis缓存模块
- `spring-boot-starter-aop`: Spring Boot AOP启动器

## 使用场景
适用于需要对外提供API服务的业务场景，提供标准化的API开放和权限管理接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。