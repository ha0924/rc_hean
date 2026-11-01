# MySQL Bootstrap

数据库表结构由 `notification-admin` 中的 Flyway 管理：

```text
notification-admin/src/main/resources/db/migration
├── V1__create_configuration_tables.sql
└── V2__create_delivery_tables.sql
```

本目录只负责数据库和用户的首次创建，不再保存另一份表结构 SQL，避免与 Flyway 迁移发生漂移。

首次启动服务前，需要由数据库管理员完成类似初始化：

```sql
CREATE DATABASE notification_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'notification'@'%' IDENTIFIED BY '<replace-with-secure-password>';
GRANT ALL PRIVILEGES ON notification_platform.* TO 'notification'@'%';
```

启动 `notification-admin` 后，Flyway 会自动创建 `flyway_schema_history`，依次执行尚未应用的迁移，并校验已执行脚本的校验和。

生产环境建议将“执行迁移”和“应用运行”拆成独立发布步骤；初版由 `notification-admin` 启动时执行迁移。
