# 视频监控管理平台

基于 **萤石云开放平台** 的实时视频监控系统，支持多设备、多通道直播查看、云台控制（PTZ）、用户权限管理等功能。

## 📋 项目概览

```
实时视频监控平台
├── backend/                    # Spring Boot 后端 (Java 17)
│   └── src/main/java/com/realtimevideo/
│       ├── config/             # 安全配置、CORS、JWT过滤器、限流器
│       ├── controller/         # REST API 控制器
│       ├── dto/                # 数据传输对象
│       ├── model/              # 实体模型 (User/Device/Channel/OperationLog)
│       ├── repository/         # JPA 数据访问层
│       └── service/            # 业务逻辑层
├── frontend/                   # Vue 3 前端
│   └── src/
│       ├── api/                # Axios API 客户端
│       ├── components/         # 可复用组件 (VideoPlayer/Toast)
│       ├── composables/        # Vue 组合式函数 (useToast)
│       ├── router/             # Vue Router 路由配置
│       ├── store/              # Pinia 状态管理
│       └── views/              # 页面视图 (桌面端 + 移动端)
├── deploy/                     # Docker 部署配置
│   ├── Dockerfile.backend      # 后端容器镜像
│   ├── Dockerfile.frontend     # 前端容器镜像
│   ├── nginx.conf              # Nginx 配置（方式一用）
│   ├── bt-nginx.conf           # 宝塔面板 Nginx 模板（方式二用）
│   ├── init.sql                # 数据库初始化脚本
│   ├── build-package.sh        # 构建打包脚本
│   ├── deploy.sh               # 方式一部署脚本
│   └── deploy-bt.sh            # 方式二部署脚本（宝塔面板）
├── deploy-server.sh            # 服务器一键部署（方式一）
├── docker-compose.yml          # 方式一：三容器编排 (MySQL + Backend + Nginx)
├── docker-compose.backend-only.yml  # 方式二：仅后端 (MySQL + Backend)
└── .env.example                # 环境变量配置模板
```

## 🏗️ 系统架构图

```mermaid
flowchart TB
    subgraph 前端["🎨 前端 (Vue 3)"]
        Login["登录页面 Login.vue"]
        Dashboard["控制台 Dashboard.vue"]
        Admin["用户管理 AdminUsers.vue"]
        VideoPlayer["视频播放器<br/>VideoPlayer.vue"]
        Store["状态管理 (Pinia)"]
        Router["路由 (Vue Router)"]
        API["API 客户端 (Axios)"]

        Login --> Router
        Dashboard --> Router
        Admin --> Router
        Dashboard --> VideoPlayer
        VideoPlayer --> API
        Dashboard --> API
        Admin --> API
    end

    subgraph 后端["⚙️ 后端 (Spring Boot 3.2.5)"]
        direction TB
        subgraph 安全层["🔐 安全层"]
            SecurityConfig["SecurityConfig<br/>安全配置"]
            JwtAuthFilter["JwtAuthFilter<br/>JWT 认证"]
            RateLimitFilter["RateLimitFilter<br/>限流器"]
            CorsConfig["CorsConfig<br/>跨域配置"]
        end

        subgraph 控制层["📡 控制层"]
            AuthController["AuthController<br/>登录/登出"]
            DeviceController["DeviceController<br/>设备管理"]
            EzvizController["EzvizController<br/>萤石云API"]
            AdminController["AdminController<br/>用户管理"]
            HealthController["HealthController<br/>健康检查"]
        end

        subgraph 业务层["🧩 业务层"]
            UserService["UserService<br/>用户管理"]
            JwtService["JwtService<br/>JWT令牌"]
            DeviceService["DeviceService<br/>设备管理"]
            EzvizService["EzvizService<br/>萤石云集成"]
            AuditLogService["AuditLogService<br/>审计日志"]
        end

        subgraph 数据层["💾 数据层"]
            UserRepository["UserRepository"]
            DeviceRepository["DeviceRepository"]
            ChannelRepository["ChannelRepository"]
            DB[("MySQL/H2<br/>数据库")]
        end

        subgraph 外部集成["🌐 外部集成"]
            EzvizAPI["萤石云 OpenAPI<br/>open.ys7.com"]
            EZUIKit["EZUIKit Player<br/>ezuikit-js"]
        end

        安全层 --> 控制层
        控制层 --> 业务层
        业务层 --> 数据层
        数据层 --> DB
        业务层 --> EzvizAPI
    end

    前端 --> 后端
    EZUIKit -.->|"ezopen:// 协议"| EzvizAPI

    style 前端 fill:#e3f2fd,stroke:#1a73e8,color:#1a1a2e
    style 后端 fill:#f3e5f5,stroke:#7b1fa2,color:#1a1a2e
    style 安全层 fill:#fff3e0,stroke:#f57c00
    style 控制层 fill:#e8f5e9,stroke:#388e3c
    style 业务层 fill:#e0f7fa,stroke:#00838f
    style 数据层 fill:#fce4ec,stroke:#c62828
    style 外部集成 fill:#f5f5f5,stroke:#616161
```

## 🔄 数据流说明

```
用户请求流程：
1. 用户访问登录页面 → 输入用户名密码
2. 前端调用 /api/auth/login → 后端验证返回 JWT Token
3. 前端携带 Token 获取设备列表 /api/ezviz/channels
4. 前端获取萤石云 accessToken → 传递给 EZUIKit 播放器
5. EZUIKit 通过 ezopen:// 协议解析直播地址并播放视频
6. 用户可使用云台控制 (PTZ)、截图、录制、画质切换等功能
```

## ✨ 功能特性

- **🔐 用户认证** — JWT 双 Token 机制（Access + Refresh Token），refreshToken 通过 httpOnly Cookie 传输
- **🔄 强制改密** — 首次登录强制修改密码，支持 6~18 位任意字符
- **🔑 自助改密** — 桌面端、移动端均支持用户自助修改密码
- **📹 视频直播** — 基于 EZUIKit 播放器，支持萤石云设备实时查看
- **🎚️ 画质切换** — 流畅/高清/自动三档画质，自动检测网络质量
- **🎮 PTZ 云台控制** — 上下左右 + 变焦控制，支持鼠标/触控，受设备权限控制
- **📋 设备管理** — 萤石云设备自动同步，通道列表树形展示
- **👥 用户管理** — 管理员后台，支持创建/禁用/重置密码
- **🛡️ 设备权限隔离** — 普通用户只能查看已授权的设备通道，播放与 PTZ 操作均需权限校验
- **📝 操作审计日志** — 记录用户关键操作，支持分页查询与筛选
- **🔒 登录安全** — 登录失败锁定（5 次/30 分钟）、请求限流（Bucket4j 令牌桶）
- **⚡ 性能优化** — 通道列表 5 秒缓存、智能在线状态轮询刷新、骨架屏加载
- **🔄 通道状态刷新** — 所有用户均可按需刷新通道在线状态（仅更新 status，不修改配置）
- **📱 移动端适配** — 独立移动端布局，抽屉式侧边栏，横屏全屏播放
- **🌙 夜间模式** — 深色主题支持，保护夜间观看体验
- **🧪 单元测试** — 8 个后端测试用例覆盖服务逻辑与接口权限

## 🚀 快速启动

### 环境要求

- **JDK 17+**
- **Node.js 18+**
- **Maven 3.8+**
- **Docker + Docker Compose**（生产部署）

### 开发环境启动

```bash
# 1. 启动后端
cd backend
mvn clean install -DskipTests   # 跳过测试快速构建
mvn spring-boot:run
# 服务启动在 http://localhost:8080

# 2. 运行后端单元测试
mvn test

# 3. 启动前端（新开终端）
cd frontend
npm install
npm run dev
# 开发服务器启动在 http://localhost:5173
```

### 🐳 方式一：Docker 全容器部署（推荐，无需 Nginx 配置）

```bash
# 使用预构建产物快速部署
docker compose down
docker compose up -d --build

# 或使用服务器一键部署脚本
bash deploy-server.sh
```

### 🧩 方式二：宝塔面板部署（适合已有 BT 管理的服务器）

**架构**：BT Nginx 负责前端 + 反向代理，Docker 仅跑后端 + 数据库。

```
BT Nginx (80/443)
  ├── / → 前端静态文件（站点根目录）
  ├── /api/* → 反向代理 → backend (127.0.0.1:8081)
  └── /ezuikit_cdn/* → 反向代理 → 萤石云 CDN
```

**① 部署后端服务：**

```bash
# 上传构建包到服务器，解压到 /opt/realtime-video
cd /opt/realtime-video
cp deploy/env.production .env
# 编辑 .env 填入真实配置
vi .env

# 启动后端（仅运行 db + backend，无端口冲突）
docker compose -p realtime-video -f docker-compose.backend-only.yml up -d --build
```

**② 配置宝塔站点：**

1. 宝塔面板 → 网站 → **添加站点**（输入你的域名）
2. 将 `frontend/dist/` 下的所有文件上传到站点根目录
3. 站点设置 → 配置文件中，**替换为 `deploy/bt-nginx.conf` 的内容**
   - 修改 `server_name` 和 `root` 路径
4. （可选）开启 SSL

```bash
# 上传前端文件示例
scp -r /path/to/realtime-video-build/frontend/dist/* root@服务器IP:/www/wwwroot/你的域名/
```

**③ 后续加新业务**：宝塔新建站点 → 新 Docker 项目（端口错开）→ BT Nginx 配 proxy_pass，互不干扰。

### 默认账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | Admin@123456 | 管理员 |
| user | User@123456 | 普通用户 |

> ⚠️ **安全提醒**：首次登录后请立即修改密码！

### 运行测试

```bash
cd backend
mvn test      # 运行后端单元测试
```

## 🔧 快速配置

复制环境变量模板并填写：

```bash
cp .env.example .env
# 编辑 .env 填入萤石云凭证和 JWT 密钥
```

## 📦 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端框架** | Vue 3 | ^3.4.27 |
| **构建工具** | Vite | ^5.2.12 |
| **状态管理** | Pinia | ^2.1.7 |
| **路由** | Vue Router | ^4.3.2 |
| **HTTP 客户端** | Axios | ^1.7.2 |
| **视频播放** | EZUIKit (ezuikit-js) | ^9.0.5 |
| **后端框架** | Spring Boot | 3.2.5 |
| **安全框架** | Spring Security | 3.2.5 |
| **ORM** | Spring Data JPA | 3.2.5 |
| **数据库** | MySQL 8.0 / H2 | - |
| **JWT** | JJWT | 0.12.5 |
| **限流** | Bucket4j | 8.7.0 |
| **部署** | Docker Compose | - |

## 🔧 配置说明

### 环境变量

项目使用 `.env` 文件管理敏感配置（已在 `.gitignore` 中排除）：

```bash
# 萤石云 OpenAPI 凭证（在 console.ys7.com 获取）
EZS_APP_KEY=你的萤石云appKey
EZS_APP_SECRET=你的萤石云appSecret

# JWT 签名密钥（生产环境务必修改）
# 生成命令: openssl rand -base64 32
JWT_SECRET=你的BASE64编码的随机密钥
```

### Docker 部署配置

详见 `docker-compose.yml`，三容器架构：

```
db (MySQL 8.0)  ←── backend (Spring Boot)  ←── frontend (Nginx)
  backend-net          backend-net + frontend-net     frontend-net
```

- 数据库不对外暴露端口，仅 backend 可通过 Docker 内部网络访问
- backend 同时位于 backend-net 和 frontend-net，实现跨网络通信
- Nginx 配置了运行时 DNS 解析，启动不依赖后端容器就绪状态

## 🔐 安全架构

- **JWT 双 Token**：Access Token（15分钟，localStorage）+ Refresh Token（7天，httpOnly Cookie）
- **Cookie 安全属性**：httpOnly、SameSite=Strict、Path=/api/auth
- **BCrypt 密码加密**：强度 12
- **登录失败锁定**：5 次连续失败锁定 30 分钟
- **请求限流**：通用 API 120 次/分钟，登录接口 10 次/分钟 + 30 次/小时
- **CORS 白名单**：仅允许配置的域名跨域访问
- **统一异常处理**：不暴露内部错误细节
- **设备权限隔离**：通道列表、Token 获取、PTZ 控制等所有设备相关 API 均受 `UserDevicePermission` 权限校验

## 📜 许可证

MIT License
