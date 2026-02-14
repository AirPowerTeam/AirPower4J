# AirPower CURD 模块

## 概述
AirPower CURD模块是AirPower4J框架中的数据持久化模块，提供通用的增删改查功能。

## 功能特性
- 通用CURD操作接口
- JPA实体映射支持
- 数据库事务管理
- 分页查询功能
- 统一异常处理

## 依赖关系
- `airpower-core`: 核心功能模块
- `airpower-api`: API开发模块
- `airpower-redis`: Redis缓存模块
- `lombok`: 代码简化工具
- `spring-data-commons`: Spring Data公共组件
- `jakarta.persistence-api`: Jakarta持久化API
- `spring-boot-starter-data-jpa`: Spring Boot JPA启动器
- `spring-boot-starter-validation`: Spring Boot验证启动器
- `mysql-connector-java`: MySQL连接器

## 使用场景
适用于需要数据库持久化的业务场景，提供标准化的数据访问接口。

## 配置说明
模块遵循AirPower框架的统一配置规范，支持自动装配和配置继承。