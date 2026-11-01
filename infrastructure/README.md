# Infrastructure

该目录保存本地开发和部署所需的基础设施资源。

```text
infrastructure
├── mysql
│   └── README.md
└── rocketmq
    └── README.md
```

数据库表结构使用 `notification-admin` 中的 Flyway 迁移管理。本目录提供数据库首次创建说明和 RocketMQ 资源约定。Docker Compose、Broker 配置和自动建 Topic 脚本将在确定本地运行方式后补充。
