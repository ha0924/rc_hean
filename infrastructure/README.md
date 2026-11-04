# Infrastructure

该目录保存本地开发和部署所需的基础设施资源与可复现的本地启动步骤。

```text
infrastructure
├── mysql
│   └── README.md
└── rocketmq
    ├── README.md
    └── broker.conf
```

根目录的 `docker-compose.yml` 启动 MySQL 8.4、RocketMQ NameServer 和 Broker。表结构不在 Compose 中维护：启动 `notification-admin` 时，Flyway 会执行唯一的版本化迁移来源。

## 本地启动

前置条件：Docker Compose、JDK 21、Maven 3.9+。以下命令在仓库根目录执行。

```bash
cp infrastructure/.env.example infrastructure/.env
set -a && . infrastructure/.env && set +a
docker compose up -d
./infrastructure/scripts/create-topics.sh
mvn -DskipTests package
```

在三个终端分别加载同一份环境变量后启动服务：

```bash
set -a && . infrastructure/.env && set +a
java -jar notification-admin/target/notification-admin-0.1.0-SNAPSHOT.jar
```

```bash
set -a && . infrastructure/.env && set +a
java -jar notification-api/target/notification-api-0.1.0-SNAPSHOT.jar
```

```bash
set -a && . infrastructure/.env && set +a
java -jar notification-worker/target/notification-worker-0.1.0-SNAPSHOT.jar
```

`broker.conf` 将 Broker 注册为 `127.0.0.1`，以便运行在宿主机上的 Java 客户端能从 NameServer 取得可连接的 Broker 地址。它只适用于本地 Docker 场景，部署环境必须使用实际网卡地址并关闭自动建 Topic。

## 验收与关闭

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8082/actuator/health
curl -H 'X-Admin-Api-Key: local-admin-key' http://localhost:8081/api/admin/business-applications
docker compose exec rocketmq-broker sh mqadmin topicList -n rocketmq-namesrv:9876
```

前三个请求应返回 `UP`；管理接口应返回 `[]` 或已创建的配置而不是 401；Topic 列表应包含 `notification-delivery`。运行完整自动化验证使用：

```bash
mvn clean verify
```

停止服务并保留本地数据库数据：

```bash
docker compose down
```

如需删除本地数据库卷（不可恢复）：

```bash
docker compose down -v
```
