# Notification Platform

企业内部 API 通知服务。内部业务系统通过统一 HTTP 接口提交业务通知，系统使用 RocketMQ 事务消息将投递任务可靠地交给 Worker，再由 Worker 调用外部 HTTP(S) API。

## 工程模块

```text
notification-platform
├── notification-common   # 消息契约、状态枚举和公共常量
├── notification-admin    # 配置管理和任务运维接口
├── notification-api      # 内部通知接入和事务消息生产者
├── notification-worker   # 正常消费、外部投递和 DLQ 处理
├── docs                  # 方案和技术设计文档
└── infrastructure        # MySQL 与 RocketMQ 引导说明
```

MySQL、RocketMQ 和 DLQ 是架构组件，不是独立 Maven 模块。DLQ Handler 位于 `notification-worker` 中。

## 技术基线

- Java 21
- Spring Boot 3.5.16
- RocketMQ Spring Boot Starter 2.3.6
- MySQL 8.4 LTS
- Flyway 11.15.0（由 notification-admin 执行版本化迁移）
- Maven 3.9+

## 构建

```bash
mvn clean verify
```

## 文档

- [API 通知服务开发方案](docs/api-notification-service-development-plan.md)
- [技术选型与模块设计](docs/technology-and-module-design.md)

## 当前状态

已完成：

- Maven 多模块工程骨架
- Flyway 版本化数据库迁移
- 4 个配置实体及其基础校验
- 配置表 JDBC Repository、事务性应用服务与 REST CRUD 接口

通知接入 API 与 RocketMQ 投递处理逻辑将在后续迭代中实现。
