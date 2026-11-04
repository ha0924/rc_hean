# 配置管理端安全规格

## 1. 目标

保护 `/api/admin/**` 配置 CRUD，避免未授权用户修改业务系统凭据、投递路由和外部 Endpoint；同时通过 Endpoint 主机白名单降低 SSRF 风险。

## 2. 访问控制

- Admin API 使用 `X-Admin-Api-Key` 认证。
- 配置项 `notification.admin.api-key-hash` 保存 BCrypt 哈希，明文 Key 仅通过环境变量或密钥管理系统注入。
- 认证成功后授予 `ROLE_ADMIN`；`/api/admin/**` 必须拥有此角色。
- 没有 Header、哈希未配置或比对失败统一返回 401，不泄漏具体原因。
- `/actuator/health` 与 `/actuator/info` 可匿名访问；其他端点默认拒绝。
- 服务采用无 Session、关闭 CSRF 的 API Key 模式。生产环境必须经 HTTPS 网关访问。

## 3. SSRF 防护

- `notification.admin.allowed-endpoint-hosts` 是必填的逗号分隔主机白名单。
- 创建/更新 Endpoint 时，URL 必须为 HTTPS，主机必须精确匹配白名单或其子域名。
- 禁止 `localhost`、IP 字面量、私有/回环主机名和非 HTTPS 协议。
- Worker 在实际调用前再次应用相同主机白名单，避免历史脏数据绕过后台校验。

本规格的白名单为应用层基础防护；生产网络仍应通过 DNS、网络策略和出站代理限制 Worker 访问范围。

## 4. 代码变更

- Admin 引入 Spring Security，新增 API Key 认证 Filter 和 `SecurityFilterChain`。
- Admin 增加 `AdminSecurityProperties` 与 `EndpointHostPolicy`；配置服务在 Endpoint 创建/更新时调用 Host Policy。
- Worker 引入相同的 Endpoint Host Policy，在构建 URI 时二次检查。
- Admin 集成测试全部显式传递有效 `X-Admin-Api-Key`，并增加未认证、错误 Key、非白名单 URL 的覆盖。

## 5. 验收标准

- 未携带或携带错误 Admin Key 的 CRUD 请求返回 401。
- 有效 Key 可完成现有 CRUD 流程，响应仍不返回业务系统 Secret 或 Endpoint 凭据。
- `https://crm.example.com/...` 在白名单中可保存；`http://`、`localhost`、`127.0.0.1`、非白名单域名均被拒绝。
- Worker 即使收到历史库中的非法 Endpoint URL，也不向其发起网络请求。

