# 通知接入 Payload Schema 校验规格

## 1. 目标

在生产者创建 `notification` 和发送 RocketMQ 半消息前，使用 `event_definition.payload_schema` 校验请求 Payload。无效 Payload 必须在入站 API 同步拒绝，不能进入数据库或 MQ。

## 2. 规范与边界

- 使用 JSON Schema Draft 2020-12。
- 使用 `com.networknt:json-schema-validator` 2.x；该版本兼容当前项目的 Jackson 2.x，并支持 Draft 2020-12。
- 只支持内嵌 Schema；不支持远程 `$ref`、文件 `$ref` 或自定义网络协议，避免 SSRF 与不可控依赖。
- Schema 的创建、更新阶段验证其本身的合法性；通知接入阶段验证 Payload。

## 3. 处理流程

```text
管理端保存 event_definition
  → 校验 payload_schema 是合法的 Draft 2020-12 Schema

通知接入 API 收到 payload
  → 校验 payload 是 JSON Object
  → 加载对应 event_definition.payload_schema
  → 校验 payload
  → 通过后才计算幂等键、落 notification / delivery_task、发送半消息
```

在 Schema 校验失败时返回 HTTP 422，响应仅包含字段路径和可读错误消息，不返回完整 Payload。Schema 无效属于管理配置错误，返回 HTTP 422；不会发送 MQ 消息。

## 4. 代码变更

- `ConfiguredEvent` 与 JDBC 查询携带 `payload_schema`。
- 新增 `PayloadSchemaValidator`，输入 Schema 与 Payload，输出校验错误列表。
- `NotificationSubmissionService` 在幂等键计算前调用 Validator。
- Admin 的事件创建/更新服务调用 Schema 元校验；错误在 CRUD API 返回 400。
- 增加 JSON Schema 依赖和单元/集成测试。

## 5. 验收标准

- `required`、`type`、`minLength`、`pattern`、嵌套对象和数组等常见关键字能正确校验。
- 合法 Payload 保持现有幂等、落库和事务消息行为。
- 不合法 Payload 返回 422，`notification`、`delivery_task` 与 MQ 均没有新增记录。
- 带 `http://`、`https://` 或 `file:` 的外部 `$ref` 被拒绝。

