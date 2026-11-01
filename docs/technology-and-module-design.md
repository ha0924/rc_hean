# 技术选型与模块设计

> 日期：2026-11-01
> 状态：初稿

## 1. 技术选型

### 1.1 Java 21

选择 Java 21 作为编译和运行基线：

- LTS 版本，适合作为长期维护基线。
- 团队熟悉 Java 技术栈，开发和排障成本较低。
- 可以在后续评估虚拟线程，但初版仍使用有界 Consumer 线程池和显式并发控制。

### 1.2 Spring Boot 3.5.16

Spring Boot 用于构建三个可独立启动的服务：

- `notification-admin`
- `notification-api`
- `notification-worker`

选择 3.5.x 而不是直接采用 Spring Boot 4，主要考虑 RocketMQ Spring 2.3.x 已明确支持 Spring Boot 3.x，兼容风险更低。

### 1.3 RocketMQ Spring Boot Starter 2.3.6

RocketMQ 用于：

- 事务消息
- 任务缓冲和可靠分发
- Consumer Group 消费重试
- 死信队列

初版不自行开发数据库轮询任务调度器，也不建立自定义重试表。

### 1.4 MySQL 8.4 LTS

MySQL 保存：

- 内部业务系统配置
- 事件定义
- 外部 API 配置
- 通知路由
- 通知请求
- 投递任务及最近一次调用结果

使用 InnoDB 事务保障 `notification` 和 `delivery_task` 在 RocketMQ 本地事务中的原子写入。

### 1.5 数据访问

初始骨架使用 Spring JDBC：

- SQL 和条件更新语义清晰。
- 适合实现任务抢占、幂等插入和状态机更新。
- 暂不引入额外 ORM，避免初版依赖过多。

如果后续 CRUD 代码明显增多，可以再评估 MyBatis。

### 1.6 数据库迁移

使用 Flyway 11.15.0 管理数据库结构：

- `notification-admin` 是初版迁移责任方。
- 迁移脚本位于 `notification-admin/src/main/resources/db/migration`。
- `V1` 创建配置表，`V2` 创建通知和投递任务表。
- 应用启动时校验已执行迁移的校验和并执行新增版本。
- 禁止修改已经在共享环境执行过的迁移；结构变更必须新增迁移版本。
- Flyway Clean 被禁用，避免误删数据库对象。

生产环境后续可以将迁移从 Admin 启动过程拆成独立发布 Job，迁移脚本本身不需要改变。

## 2. 架构组件与代码模块

架构上包含六个逻辑组件：

1. 配置后台
2. 通知处理服务
3. MySQL
4. RocketMQ
5. Delivery Consumer
6. DLQ Handler

工程上拆成四个 Maven 模块，其中三个为可部署服务：

```text
notification-platform
├── notification-common
├── notification-admin
├── notification-api
└── notification-worker
```

MySQL 和 RocketMQ 放在 `infrastructure` 目录管理；DLQ Handler 与正常 Consumer 一起放在 `notification-worker` 中。

## 3. 模块职责

### 3.1 `notification-common`

公共 Java Library，不独立启动。

包含：

- RocketMQ 消息 DTO
- Topic、Tag 和 Consumer Group 常量
- Notification 和 Delivery Task 状态枚举
- 多模块都需要使用的错误码和基础类型

不包含：

- Controller
- Repository
- 具体 Service 实现
- 数据库实体的持久化逻辑

### 3.2 `notification-admin`

配置管理与运维服务。

职责：

- 内部业务系统 CRUD
- 事件定义 CRUD
- 外部 API CRUD
- 通知路由 CRUD
- 配置启用和停用
- 通知和投递任务查询
- 失败任务人工重试

依赖：

```text
Spring Web
Spring Validation
Spring JDBC
Flyway
MySQL
notification-common
```

当前已实现的配置接口（统一前缀 `/api/admin`）：

| 资源 | CRUD 路径 |
|---|---|
| 业务系统 | `/business-applications` |
| 事件定义 | `/event-definitions` |
| 外部端点 | `/external-endpoints` |
| 通知路由 | `/notification-routes` |

所有资源均提供 `POST`、列表/详情 `GET`、`PUT` 和 `DELETE`。创建或更新路由时会先校验引用的事件和外部端点存在；数据库外键继续作为最终约束。业务系统 Secret 使用 BCrypt 哈希保存，接口响应不返回 Hash；外部端点响应不返回 `authConfig`。

### 3.3 `notification-api`

内部通知接入服务和 RocketMQ 事务消息 Producer。

职责：

- 内部系统鉴权
- 参数与 Payload Schema 校验
- 请求幂等
- 路由匹配
- 预生成 Notification ID 和 Task ID
- 发送 RocketMQ Half Message
- 本地事务写入 `notification` 和 `delivery_task`
- RocketMQ 事务状态回查
- 返回 `202 Accepted`

依赖：

```text
Spring Web
Spring Validation
Spring JDBC
MySQL
RocketMQ Producer
notification-common
```

### 3.4 `notification-worker`

消息消费和外部 HTTP 投递服务。

职责：

- 消费正常投递消息
- 原子抢占投递任务
- 按外部端点控制最大并发数
- 渲染 URL、Header 和 Body
- 调用外部 HTTP API
- 更新任务状态及最近一次调用结果
- 对可重试失败返回消费失败
- 消费 DLQ 并标记最终失败

依赖：

```text
Spring Boot
Spring Web HTTP Client
Spring JDBC
MySQL
RocketMQ Consumer
notification-common
```

## 4. 包分层

每个服务模块采用以下基础分层：

```text
controller       HTTP 协议、参数接收和响应转换
application      用例编排和事务边界
domain           领域对象、状态规则和接口抽象
infrastructure   MySQL、RocketMQ、HTTP Client 等技术实现
```

依赖方向：

```text
controller -> application -> domain
                         ^
                         |
                  infrastructure
```

约束：

- Controller 不直接访问数据库。
- Domain 不依赖 Spring MVC、RocketMQ 或 JDBC。
- 事务边界放在 Application 层。
- Infrastructure 实现 Domain 或 Application 定义的端口。
- 跨模块消息只能使用 `notification-common` 中定义的消息契约。

## 5. 部署单元

初版包含三个 Spring Boot 进程：

| 服务 | 默认端口 | 主要依赖 |
|---|---:|---|
| `notification-api` | 8080 | MySQL、RocketMQ |
| `notification-admin` | 8081 | MySQL |
| `notification-worker` | 8082 | MySQL、RocketMQ、外部 API |

基础设施：

| 组件 | 用途 |
|---|---|
| MySQL 8.4 LTS | 配置和任务状态存储 |
| RocketMQ | 事务消息、消费重试和 DLQ |

## 6. 配置约定

所有环境相关配置通过环境变量覆盖，代码仓库不保存真实密钥：

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
ROCKETMQ_NAME_SERVER
```

外部 API Token 和内部系统 Secret 不得写入普通配置文件或日志。

## 7. 后续开发顺序

1. 完成数据库迁移管理和配置实体。（已完成）
2. 实现配置 Repository 和 Admin CRUD。
3. 实现通知接入、幂等和事务消息 Producer。
4. 实现事务回查。
5. 实现正常 Consumer 和外部 HTTP Client。
6. 实现重试状态更新和 DLQ Handler。
7. 补充集成测试、监控指标和本地运行编排。

## 参考资料

- [Spring Boot 3.5 Reference](https://docs.spring.io/spring-boot/reference/index.html)
- [Apache RocketMQ Spring](https://github.com/apache/rocketmq-spring)
- [MySQL 8.4 LTS Release Model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)
