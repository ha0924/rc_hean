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

负责维护平台配置，并通过 Flyway 管理表结构迁移。

配置链路：业务系统 → 事件类型 → 外部 Endpoint → 通知路由。Producer 根据业务系统和事件校验请求；Consumer 根据路由和 Endpoint 获取外部调用配置。

### 通知接入 API / 生产者：`notification-api`

供内部业务系统提交通知请求，并可靠地生产 RocketMQ 投递任务。

生产链路：内部请求 → 鉴权 → 幂等校验 → 本地落库 → RocketMQ 事务消息。

- 两层幂等：优先使用业务系统提供的 `Idempotency-Key`；缺失时使用 `requestId` 和规范化 payload 生成幂等键。
- `notification` 唯一键负责并发排斥：相同请求返回已有通知，不同内容复用同一键则拒绝。
- RocketMQ 半消息与本地 `notification`、`delivery_task` 创建通过事务消息保持一致；回查根据任务是否存在决定提交或回滚。

### 投递消费者：`notification-worker`

消费 `notification-delivery` Topic，负责真正调用外部 API。

消费链路：RocketMQ 消息 → 原子抢占任务 → 外部 HTTP 请求 → 更新投递状态 → ACK 或重试。

- 条件更新和租约锁保证重复消息不会并发调用同一个外部 API。
- 调用外部 API 时固定携带 `Idempotency-Key: {deliveryTaskId}`，由目标系统处理重复请求。
- 2xx 成功；网络异常、408、429、5xx 交给 RocketMQ 重试；其他 4xx 直接失败。重试耗尽后进入 DLQ，由 DLQ Consumer 最终标记失败。

初版直接透传通知 `payload` 作为外部 HTTP Body，仅启用 `auth_type=NONE`；字段映射、加密凭据或密钥引用解析是后续扩展能力。

## 核心表设计

| 表 | 作用 |
| --- | --- |
| `business_application` | 注册可接入平台的内部业务系统，保存调用凭证和启停状态。 |
| `event_definition` | 定义业务系统允许发送的事件类型及其 Payload 约束。 |
| `external_endpoint` | 描述要调用的外部 HTTP API，包括地址、方法、超时和调用规则。 |
| `notification_route` | 建立内部事件到外部 Endpoint 的投递路由。初版一个事件只有一条启用路由。 |
| `notification` | 记录一次内部通知请求，承担生产端请求幂等、原始 Payload 保存和通知总体状态。 |
| `delivery_task` | 记录一次具体外部投递，承担 Consumer 抢占锁、重试次数、HTTP 调用结果和最终投递状态。 |

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
