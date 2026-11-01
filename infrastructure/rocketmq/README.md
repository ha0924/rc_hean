# RocketMQ Resources

初版逻辑资源：

| 类型 | 名称 |
|---|---|
| Transaction Topic | `notification-delivery` |
| Producer Group | `notification-tx-producer` |
| Consumer Group | `notification-delivery-worker` |

消费重试与死信队列使用 `notification-delivery-worker` Consumer Group 对应的 RocketMQ 内建能力。

事务消息 Body 使用 `notification-common` 中的 `DeliveryTaskMessage`：

```json
{
  "notificationId": "N001",
  "taskId": "T001",
  "traceId": "TRACE001"
}
```
