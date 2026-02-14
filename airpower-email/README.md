# AirPower Email 模块

## 概述
AirPower Email模块是AirPower4J框架中的邮件服务模块，提供统一的邮件发送功能。

## 功能特性
- 统一邮件发送接口
- 支持多种邮件模板
- 邮件队列处理
- 邮件发送状态跟踪

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-exception`: 异常处理模块
- `jakarta.mail`: Jakarta邮件API
- `spring-context-support`: Spring上下文支持

## 使用场景
适用于需要发送邮件通知的业务场景，提供标准化的邮件服务接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。