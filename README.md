# Notification Platform

企业内部 API 通知服务。内部业务系统通过统一 HTTP 接口提交业务通知，系统使用 RocketMQ 事务消息将投递任务可靠地交给 Worker，再由 Worker 调用外部 HTTP(S) API。

## 整体架构

```text
配置管理端
  └─ 业务系统、事件、外部 Endpoint、路由配置
                         │
内部业务系统 ── HTTPS ──> 通知接入 API（Producer）
                         │
                         ├─ 鉴权、请求幂等、notification / delivery_task 落库
                         └─ RocketMQ 事务消息
                                      │
                                      ▼
                           notification-delivery Topic
                                      │
                                      ▼
                          投递 Worker（Consumer）
                         ├─ 原子抢占投递任务
                         ├─ 外部 HTTP(S) 请求 + 幂等键
                         ├─ 成功、重试与最终失败状态更新
                         └─ 重试耗尽后进入 DLQ
                                      │
                                      ▼
                                外部业务系统 API
```

系统按“至少一次投递”设计。RocketMQ 消息可能重复投递，或者 Worker 可能在外部 HTTP 调用完成后、确认消息前故障。因此，本地 `delivery_task` 使用状态和租约锁保证同一任务不会被并发重复执行；向外部 API 固定传递 `Idempotency-Key: {deliveryTaskId}`，由外部系统将重复请求收敛为一次业务效果。

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

## 模块职责

### 配置管理端：`notification-admin`

为平台管理员提供配置 CRUD，并通过 Flyway 管理 MySQL 表结构。

- 管理内部业务系统及其 `accessKey`、Secret 哈希和启停状态；Secret 使用 BCrypt 哈希保存，不返回给管理端调用方。
- 管理事件定义、外部 HTTP Endpoint 和事件到 Endpoint 的路由关系。
- Endpoint 配置包含 HTTP 方法、URL、默认 Header、超时、并发上限、鉴权类型和成功/重试规则等基础信息。
- 初版约束为一个事件只配置一条启用的投递路由，因此一次通知对应一个 `delivery_task`。

### 通知接入 API / 生产者：`notification-api`

供内部业务系统提交通知请求，负责入站鉴权、请求幂等以及将任务可靠投递至 RocketMQ。

- 使用 `X-Access-Key` 和 `X-Access-Secret` 鉴权；Secret 通过 HTTPS 传输并用 BCrypt 比对。
- 支持两层请求幂等：优先采用调用方的 `Idempotency-Key`；未提供时，使用稳定的 `requestId + applicationId + eventDefinitionId + 规范化 payload` 生成幂等键。两者都缺失时拒绝请求。
- `notification(application_id, idempotency_key)` 唯一索引是最终并发保护；相同键且相同请求返回已有通知，不同内容复用同一键返回冲突。
- 先发送 RocketMQ 半消息，再在本地事务中创建 `notification` 与 `delivery_task`。本地事务成功则提交消息，失败则回滚；事务回查依据 `delivery_task` 是否存在决定提交或回滚。

### 投递消费者：`notification-worker`

消费 `notification-delivery` Topic，调用外部 HTTP(S) API，并将投递结果持久化。

- 通过条件更新原子抢占 `PENDING`、`RETRY_WAIT` 或锁已过期的 `PROCESSING` 任务；终态或仍被锁定的重复消息直接 ACK。
- HTTP 调用不包裹数据库长事务。请求携带 `X-Notification-Id`、`Idempotency-Key: {deliveryTaskId}` 和 `X-Trace-Id`，让外部系统可以处理重复调用。
- 2xx 标记为 `SUCCEEDED`；网络异常、408、429、5xx 标记为 `RETRY_WAIT` 并请求 RocketMQ 重投；其他 4xx 标记为 `FAILED`。
- 普通 Consumer 的重试次数固定为 5 次。超过次数进入 `%DLQ%notification-delivery-worker`，DLQ Consumer 将任务和通知最终标记为 `FAILED`。
- 初版直接透传通知 `payload` 作为外部 HTTP Body，且仅启用 `auth_type=NONE`。字段映射 DSL、加密凭据或密钥引用解析是后续扩展能力。

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
- 配置实体校验、JDBC Repository、事务性应用服务与 REST CRUD 接口
- 通知接入 API：鉴权、两层幂等、唯一键并发保护和异常响应
- RocketMQ 事务消息生产：半消息、本地事务和基于任务存在性的事务回查
- 投递 Worker：任务原子抢占、HTTP 投递、外部幂等键、结果分类与状态流转
- RocketMQ 重试与 DLQ Consumer 的最终失败收敛
- 覆盖生产、消费、HTTP 适配和 MySQL 状态流转的单元/集成测试
