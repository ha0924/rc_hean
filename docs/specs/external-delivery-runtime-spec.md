# 外部 HTTP 投递运行时规格

## 1. 目标与范围

本规格使后台的 Endpoint 与 Route 配置真正参与 Worker 的外部 HTTP 调用，解决以下能力缺失：

- Endpoint 鉴权：`NONE`、`BEARER`、`API_KEY`、`BASIC`。
- Route 请求映射：动态路径、Query、Header，以及 JSON、表单、XML 请求体。
- Endpoint 运行时策略：`max_concurrency`、`success_rule`、`retryable_rule`。

本规格不包含外部 Payload 公钥加密/签名、分布式并发限流、复杂 XML Namespace 映射或通用脚本表达式；这些均为后续独立能力。

## 2. 投递链路

```text
RocketMQ 消息
  → 查询任务、Route、Endpoint 配置
  → 按 Endpoint 本进程并发上限取得许可
  → 原子抢占 delivery_task
  → 解析请求映射和鉴权凭据引用
  → 构造 HTTP 请求并调用外部系统
  → 按 Endpoint 成功/重试规则更新任务
  → ACK、RocketMQ 重试或 DLQ
```

HTTP 调用不包裹数据库事务。任务只在获得并发许可后抢占，避免等待并发许可时租约过期。

## 3. Endpoint 鉴权配置

`external_endpoint.auth_config` 统一保存 JSON 配置，不保存明文 Token、API Key 或密码。敏感值必须通过 `secretRef` 读取。

| `auth_type` | `auth_config` |
| --- | --- |
| `NONE` | `null` 或空值 |
| `BEARER` | `{"secretRef":"env:CRM_BEARER_TOKEN"}` |
| `API_KEY` | `{"secretRef":"env:CRM_API_KEY","in":"HEADER","name":"X-API-Key"}` |
| `BASIC` | `{"username":"crm-service","passwordSecretRef":"env:CRM_PASSWORD"}` |

`API_KEY.in` 仅支持 `HEADER`、`QUERY`，默认 `HEADER`；`name` 为必填。第一版 `SecretProvider` 支持 `env:` 引用，从 Worker 环境变量或 Spring 配置读取。后续可以替换为 Vault/KMS 实现，调用方不需要改 Route 或 Endpoint 数据。

凭据绝不写入日志、任务错误信息或 HTTP 响应摘要。Route 和 Endpoint 映射不得设置 `Authorization`。

## 4. `request_mapping` 协议

映射表达式从 `notification.payload` 取值，采用 RFC 6901 JSON Pointer。例如 `/customer/id` 指向 `payload.customer.id`。

值表达式只有两种形式：

```json
{"from":"/customer/id","required":true}
```

```json
{"value":"subscription"}
```

`from` 与 `value` 必须二选一。`required` 默认 `true`；必填值不存在、非标量 Header/Query 值、URL 占位符未赋值均为不可重试的配置/映射错误，Worker 不发送 HTTP 请求并将任务标记为 `FAILED`。

### 4.1 完整 CRM 示例

Endpoint：

```json
{
  "httpMethod": "PATCH",
  "urlTemplate": "https://crm.example.com/v1/contacts/{contactId}",
  "authType": "BEARER",
  "authConfig": {"secretRef": "env:CRM_BEARER_TOKEN"},
  "defaultHeaders": {"X-Source": "subscription"},
  "timeoutMs": 5000,
  "maxConcurrency": 8,
  "successRule": {"statuses": [200, 204]},
  "retryableRule": {"statuses": [408, 429], "ranges": ["5xx"]}
}
```

Route：

```json
{
  "pathVariables": {
    "contactId": {"from": "/contact/id"}
  },
  "query": {
    "source": {"value": "notification"}
  },
  "headers": {
    "X-Event-Type": {"value": "subscription.payment_succeeded"},
    "X-Contact-Level": {"from": "/contact/level", "required": false}
  },
  "body": {
    "type": "JSON",
    "template": {
      "contactId": {"from": "/contact/id"},
      "email": {"from": "/contact/email"},
      "payment": {
        "amount": {"from": "/payment/amount"},
        "currency": {"from": "/payment/currency"}
      }
    }
  }
}
```

结果请求为：

```text
PATCH https://crm.example.com/v1/contacts/C-100?source=notification
Authorization: Bearer <resolved-secret>
X-Source: subscription
X-Event-Type: subscription.payment_succeeded
Idempotency-Key: {deliveryTaskId}
X-Notification-Id: {notificationId}
X-Trace-Id: {traceId}
Content-Type: application/json
```

### 4.2 URL、Query 与 Header

- `urlTemplate` 的 `{name}` 占位符必须在 `pathVariables` 中提供，按 UTF-8 URL 编码后替换。
- `query` 键和值被 URL 编码后附加到 URL；重复 Query、数组和值拼接不在第一版范围。
- Header 值必须是字符串、数字或布尔值；可选值缺失时不写入 Header/Query。
- Header 合并优先级为：Endpoint 默认 Header < Route Header < 鉴权 Header < 平台保留 Header。
- 以下 Header 为平台保留字段，配置不得覆盖：`Authorization`、`Idempotency-Key`、`X-Notification-Id`、`X-Trace-Id`、`Content-Type`。

### 4.3 请求体

| `body.type` | 配置字段 | 发送结果 |
| --- | --- | --- |
| `JSON` | `template`，支持嵌套对象和数组，叶子为值表达式 | `application/json` |
| `FORM_URLENCODED` | `fields`，每项为值表达式 | `application/x-www-form-urlencoded;charset=UTF-8` |
| `XML` | `root`、`fields`，第一版仅支持扁平元素 | `application/xml;charset=UTF-8` |

表单示例：

```json
{
  "body": {
    "type": "FORM_URLENCODED",
    "fields": {
      "contact_id": {"from": "/contact/id"},
      "amount": {"from": "/payment/amount"}
    }
  }
}
```

XML 示例：

```json
{
  "body": {
    "type": "XML",
    "root": "UpdateContact",
    "fields": {
      "ContactId": {"from": "/contact/id"},
      "Email": {"from": "/contact/email"}
    }
  }
}
```

XML 元素和值必须被正确转义。`request_mapping` 为 `{}` 或未设置 `body` 时，为兼容已有任务，默认将原始 Payload 作为 JSON Body 透传。

## 5. Endpoint 响应与并发规则

`success_rule` 和 `retryable_rule` 使用同一协议：

```json
{
  "statuses": [200, 201, 204],
  "ranges": ["2xx"]
}
```

- `statuses` 是精确 HTTP 状态码数组。
- `ranges` 仅支持 `2xx`、`3xx`、`4xx`、`5xx`。
- 先匹配成功规则，再匹配重试规则；均不匹配时标记为最终失败。
- 空规则兼容默认策略：成功为 `2xx`，重试为网络异常、408、429、`5xx`。
- 网络、连接、读取超时没有 HTTP 状态码时始终可重试。

`max_concurrency` 在单个 Worker 进程内通过 Endpoint 维度的 Semaphore 生效。它限制的是该进程对同一 Endpoint 的并发 HTTP 调用；多实例全局并发限制需要 Redis 或数据库协调，不属于本规格。

## 6. 代码与数据变更

不新增数据库列，复用已有 `external_endpoint` 和 `notification_route` 配置：

- Worker 的任务查询补充读取 `request_mapping`、`max_concurrency`、`success_rule`、`retryable_rule`。
- 新增 `OutboundRequestFactory`，负责映射、URL 构造、Header 合并和 Body 序列化。
- 新增 `SecretProvider` 与 `EndpointAuthenticationResolver`，隔离凭据解析。
- `RestClientExternalApiClient` 只执行已构造的 `OutboundHttpRequest`，不再固定 JSON Body。
- `DeliveryResultClassifier` 按 Endpoint 规则判断结果。
- Admin 在创建/更新 Endpoint、Route 时校验上述 JSON 协议。

## 7. 验收标准

- Bearer Token CRM 请求能正确替换 Path、Query、动态 Header 和 JSON Body，且 Token 不出现在日志中。
- API Key 支持 Header 与 Query 两种传递方式；Basic 能写入标准 Authorization Header。
- JSON、表单、XML 三种 Body 的 Content-Type、编码和请求体均符合配置。
- 规则配置可将 202 视为失败、将 409 视为成功或将 404 设为可重试；空规则保持默认行为。
- 同一 Endpoint 在一个 Worker 实例内同时运行的 HTTP 调用不超过 `max_concurrency`。
- 失败的映射或鉴权配置不会向外发起请求，任务被记录为不可重试失败。

