# RealTimeVideo 项目完整总结

> 生成日期: 2026-05-28
> 仓库: https://github.com/qiuyingmu/RealTimeVideo
> 服务器: 47.108.204.59 (root, /opt/realtime-video)

---

## 一、项目定位

贵州建工监理咨询有限公司的内部视频监控管理平台，基于 **萤石云开放平台（Ezviz OpenAPI）** + **EZUIKit 播放器**，提供实时视频监控查看、云台控制（PTZ）、用户权限管理等功能。

---

## 二、技术架构

```
┌─────────────────────────────────────────────────────────┐
│                     浏览器 (Chrome/Safari)                │
│   Vue 3 SPA ── EZUIKit Player ←── ezopen:// P2P 直连   │
└──────────┬──────────────────────────────────────────────┘
           │ 80 /api/*
           ▼
┌──────────────────┐     ┌──────────────────────┐
│  Nginx 前端容器   │────▶│  Spring Boot 后端容器  │
│  (nginx:alpine)   │     │  (Java 17 + JAR)     │
│  端口 80          │     │  端口 8080            │
└──────────────────┘     └──────────┬───────────┘
                                    │
                                    ▼
                           ┌──────────────────┐
                           │  MySQL 8.0 数据库  │
                           │  端口 3306         │
                           └──────────────────┘
                                    │
                           ┌──────────────────┐
                           │  萤石云 OpenAPI    │
                           │  open.ys7.com     │
                           └──────────────────┘
```

### 网络隔离 (Docker)
```
backend-net (internal-like, 可访问外网)
  ├── db (MySQL 8.0)
  └── backend (Spring Boot)
         │
frontend-net (可访问外网)
  ├── backend ←── 跨网络互通
  └── frontend (Nginx)
```

---

## 三、功能清单

### ✅ 已实现

| 功能 | 状态 | 说明 |
|------|------|------|
| 🔐 JWT 双 Token 认证 | ✅ | Access (15min) + Refresh (7d)，自动刷新 |
| 🧾 httpOnly Cookie 存 refreshToken | ✅ | 2026-05-28 迁移，防 XSS 窃取 |
| 📹 视频直播播放 | ✅ | EZUIKit pcLive 模板，全屏视频+右侧悬浮控件 |
| 🔀 多通道切换 | ✅ | 左右导航按钮 + 手势滑动（移动端） |
| 🎚️ 画质切换 | ✅ | 流畅/高清/自动三档 + 缩放模式（fill/fit/cover） |
| 🎮 PTZ 云台控制 | ✅ | 方向键 + 变焦控制，鼠标/触控均支持 |
| 📋 设备管理 | ✅ | 萤石云自动同步，通道树形展示 |
| 👥 用户管理 | ✅ | 管理员后台：创建/禁用/重置密码 |
| 🛡️ 设备权限隔离 | ✅ | 普通用户只能看到授权的设备通道 |
| 📝 操作审计日志 | ✅ | 登录/登出/PTZ/增删用户等全部记录 |
| 🔒 登录失败锁定 | ✅ | 5 次失败锁定 30 分钟 |
| 🚦 请求限流 | ✅ | Bucket4j 令牌桶，通用 120/min，登录 10/min+30/h |
| 📱 移动端适配 | ✅ | 独立 MobileLayout，抽屉式侧边栏，横屏全屏 |
| 🌙 夜间模式 | ✅ | CSS 变量切换，移动端支持 |
| 🎯 全屏播放 | ✅ | Fullscreen API + 自动隐藏导航栏 |
| 📶 网络质量检测 | ✅ | 前端每 30s 检测延迟，显示绿/黄/红指示器 |
| 🔄 30s 轮询更新状态 | ✅ | 通道在线状态自动刷新，不重建播放器 |

### ⏳ 待实现/优化

| 功能 | 优先级 | 说明 |
|------|--------|------|
| HTTPS 证书 | 中 | Nginx 配置已就绪，需域名 + Let's Encrypt |
| 单元/集成测试 | 中 | Spring Boot Test + Vue Test Utils |
| CI/CD | 低 | GitHub Actions 自动构建部署 |
| 视频回放 | 低 | EZUIKit 支持 playback，需后端对接 |
| 弹窗/对讲 | 低 | 设备已支持 talk，需前端对接 |
| 监控告警 (Actuator) | 低 | Prometheus + 指标监控 |

---

## 四、后端结构

```
backend/
├── pom.xml                          # Maven, Spring Boot 3.2.5, Java 17
├── src/main/java/com/realtimevideo/
│   ├── RealTimeVideoApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java      # Spring Security 配置
│   │   ├── JwtAuthFilter.java       # JWT 过滤器（白名单跳过公开路径）
│   │   ├── CorsConfig.java          # CORS 跨域配置
│   │   ├── RateLimitFilter.java     # Bucket4j 令牌桶限流
│   │   ├── GlobalExceptionHandler.java # 全局异常处理（不暴露细节）
│   │   ├── DataInitializer.java     # 默认 admin/user 账户初始化
│   │   ├── DotenvConfig.java        # .env 文件自动加载
│   │   ├── RestTemplateConfig.java  # 萤石云 API 连接池
│   │   └── SchedulingConfig.java    # @EnableAsync + @EnableScheduling
│   ├── controller/
│   │   ├── AuthController.java      # 登录/刷新/登出/me
│   │   ├── EzvizController.java     # 萤石云: token/同步/通道/PTZ
│   │   ├── DeviceController.java    # 设备 CRUD
│   │   ├── AdminController.java     # 用户管理 + 权限管理 + 日志查询
│   │   └── HealthController.java    # 健康检查
│   ├── service/
│   │   ├── UserService.java         # 登录逻辑/失败锁定/创建用户
│   │   ├── JwtService.java          # Token 生成/验证/黑名单
│   │   ├── EzvizService.java        # 萤石云 API 集成/缓存/同步
│   │   ├── DeviceService.java       # 设备 CRUD 逻辑
│   │   └── AuditLogService.java     # 审计日志记录与查询
│   ├── model/
│   │   ├── User.java                # 用户实体（UserDetails）
│   │   ├── Role.java                # 枚举: ROLE_ADMIN, ROLE_USER
│   │   ├── Device.java              # 设备实体
│   │   ├── Channel.java             # 通道实体（摄像头）
│   │   ├── OperationLog.java        # 操作审计日志
│   │   └── UserDevicePermission.java # 用户设备权限映射
│   ├── repository/                  # JPA Repository 接口
│   └── dto/                         # ApiResponse/LoginRequest/LoginResponse 等
└── src/main/resources/application.yml  # 多 profile 配置
```

### 关键设计决策

- **@JsonIgnore on refreshToken**: 不在 JSON 响应体中返回，仅通过 httpOnly Cookie 下发
- **EzvizService 双重缓存**: accessToken 缓存至过期前、通道列表 5s TTL 缓存
- **@Async @PostConstruct**: 异步初始化萤石云 Token + 同步设备，不阻塞启动
- **syncDevices 无事务**: 一个设备失败不影响其他设备同步
- **RateLimitFilter + JwtAuthFilter 双过滤器**: 限流先于认证，防止未认证请求打满资源

---

## 五、前端结构

```
frontend/
├── package.json              # Vue 3 + Vite 5 + Pinia + Vue Router
├── vite.config.js            # 代理 /api→8080, /ezuikit_cdn→萤石云CDN
├── src/
│   ├── main.js               # 入口
│   ├── App.vue               # 根组件：导航栏 + 路由视图 + 主题切换
│   ├── assets/styles/main.css # 全局样式 + 夜间模式 CSS 变量
│   ├── api/index.js          # Axios 实例 + Token 刷新拦截器 + 统一错误处理
│   ├── store/auth.js         # Pinia 认证状态（accessToken 存 localStorage）
│   ├── router/index.js       # 桌面端/移动端路由分流 + 认证守卫
│   ├── composables/
│   │   └── useToast.js       # 全局 Toast + 确认弹窗 composable
│   ├── components/
│   │   ├── VideoPlayer.vue   # EZUIKit 播放器封装（pcLive 模板）
│   │   └── GlobalToast.vue   # Toast 通知 + 确认弹窗 UI
│   └── views/
│       ├── Login.vue         # 登录页
│       ├── Dashboard.vue     # 桌面端主页面（侧边栏设备树 + 视频区）
│       ├── AdminUsers.vue    # 用户管理（管理员）
│       ├── AdminPermissions.vue # 设备权限管理（管理员）
│       ├── AdminLogs.vue     # 操作日志查看（管理员）
│       ├── MobileLayout.vue  # 移动端布局外壳
│       ├── MobileDashboard.vue # 移动端设备列表
│       ├── MobilePlay.vue    # 移动端视频播放
│       └── MobileSettings.vue # 移动端设置
```

### 前端亮点

- **智能设备树刷新**: 30s 轮询时保留 Vue 对象引用，只更新 status 字段，不重建播放器
- **Token 刷新队列**: 并发 401 时排队等待刷新完成，避免多次刷新请求
- **通道切换防竞态**: 世代计数器（generation counter），快速切换只有最后一次生效
- **pcLive 模板移动端覆盖**: 用 `:deep()` CSS 将 EZUIKit 的 pcLive 模板改造为移动端可用的浮动工具栏
- **全屏/横屏事件广播**: `CustomEvent` 通知 App.vue 隐藏导航栏
- **iOS 安全适配**: `safe-area-inset-*` + `font-size: 16px` 防自动缩放

---

## 六、安全体系

| 层次 | 措施 | 说明 |
|------|------|------|
| 认证 | JWT 双 Token | Access (15min, localStorage) + Refresh (7d, httpOnly Cookie) |
| 黑名单 | ConcurrentHashMap | 登出/刷新时加入黑名单，定时清理过期条目 |
| 密码 | BCrypt (strength 12) | Spring Security PasswordEncoder |
| 锁定 | 5 次/30 分钟 | 连续失败自动锁定，过期自动解锁 |
| 限流 | Bucket4j 令牌桶 | API 120/min, 登录 10/min+30/h |
| CORS | 白名单模式 | 配置跨域允许域名，不开放 `Access-Control-Allow-Origin: *` |
| 异常处理 | 不暴露细节 | 统一返回"服务器繁忙，请稍后重试" |
| XSS 防护 | httpOnly Cookie | refreshToken 通过 Cookie 下发，JS 不可读 |
| CSRF 防护 | SameSite=Strict | Cookie 仅同站请求携带 |
| 权限 | Spring Security hasRole | `/api/admin/**` 仅 ADMIN 角色可访问 |

---

## 七、部署架构

```
docker-compose.yml（三容器编排）
├── db: mysql:8.0
│   ├── 健康检查: mysqladmin ping
│   ├── 数据持久化: mysql_data volume
│   └── 初始化: deploy/init.sql
├── backend: Dockerfile.backend
│   ├── 构建: 本地 Maven 编译 JAR → COPY 到 alpine 镜像
│   ├── 启动: java -jar app.jar --spring.profiles.active=prod
│   └── 网络: backend-net + frontend-net
└── frontend: Dockerfile.frontend
    ├── 构建: 本地 Vite 构建 dist → COPY 到 nginx:alpine
    ├── Nginx 配置: deploy/nginx.conf
    ├── 网络: frontend-net
    └── 端口: 80:80
```

### Dockerfile 特点（2026-05-28 重构）

- **跳过云端编译**: 直接 COPY 本地预构建产物，`docker compose build` ≈10 秒
- **预构建 JAR**: `backend/target/realtime-video-backend-1.0.0.jar` (55MB)
- **预构建 dist**: `frontend/dist/` (3.7MB)

### Nginx 配置要点

- Gzip 压缩
- SPA 路由: `try_files $uri $uri/ /index.html`
- API 反代: `proxy_pass http://backend:8080`（`resolver 127.0.0.11` 运行时 DNS）
- EZUIKit CDN 反代: WASM 文件修正 Content-Type
- 静态资源缓存: `/assets/` 1 年 `immutable`
- HTTPS 模板: 已注释，取消注释 + 证书即可启用

---

## 八、关键修复记录

### 历史问题

| 问题 | 原因 | 解决 |
|------|------|------|
| WASM 编译失败 | CDN 返回 `application/octet-stream` | Vite 代理 + Nginx 反代修正 MIME |
| ezopen 10001 错误 | 错误使用 ipcSerial 而非 deviceSerial | 统一使用 deviceSerial + channelNo |
| SharedArrayBuffer 不可用 | 缺 COEP/COOP 头 | Vite 配置 `credentialless` + `same-origin` |
| 视频黑边 | scaleMode 默认 contain | 设置 scaleMode=1 (cover) |

### 2026-05-28 修复

| 问题 | 原因 | 解决 |
|------|------|------|
| refreshToken XSS 风险 | 存 localStorage | 迁到 httpOnly Cookie |
| logout 端点公开 | 在 PUBLIC_PATHS 中 | 移除白名单 |
| 原生 alert/confirm | 直接在 Dashboard 中使用 | 替换为 GlobalToast 组件 |
| 前端容器崩溃 | `.dockerignore` 排除 dist | 移除两行排除规则 |
| Nginx 502 | `backend:8080` 启动时无法解析 | resolver + 变量 proxy_pass |
| Nginx 502（真因） | backend 未加入 frontend-net | 加 `frontend-net` 网络 |

---

## 九、资源预估

| 配置 | 支持用户数 | 评价 |
|------|-----------|------|
| 2 vCPU / 2GB / 5Mbps | 1~2 | 勉强够，内存偏紧 |
| 2 vCPU / 4GB / 10Mbps | 3~8 | **推荐配置** |
| 4 vCPU / 8GB / 20Mbps | 10+ | 无压力 |

> 视频流 P2P 直连不占服务器带宽，带宽仅用于 API 请求和 WASM 文件加载。

---

## 十、待办事项

### 🔴 高优先级
- [ ] 配置 `.env`: EZS_APP_KEY / EZS_APP_SECRET / JWT_SECRET
- [ ] 确认 `curl http://localhost:80/api/health` 返回 200
- [ ] 测试前端正常访问并登录

### 🟡 中优先级
- [ ] 正式域名 + Let's Encrypt HTTPS
- [ ] 编写后端单元测试
- [ ] 添加 GitHub Actions CI/CD

### 🟢 低优先级
- [ ] 视频回放功能
- [ ] 弹窗/对讲功能
- [ ] Actuator + Prometheus 监控
- [ ] 前端 CSS 清理（`!important` 依赖 EZUIKit 版本）
