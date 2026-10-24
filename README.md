# 毕业论文（设计）过程管理平台

面向毕业设计过程管理的最小可行系统，覆盖：

**需求文档 → 原型 → 设计评审 → 周报 → 代码仓库关联 → 测试报告**

> 课程：软件工程专业实践课程
> 周期：6 周，每周 5–6 学时
> 当前阶段：第 2 周（JWT 登录、角色隔离、真实工作台）已完成，累计进度 34.8%

---

## 1. 当前进度

| 项目 | 状态 |
|---|---|
| 需求规格说明书 | 已完成（`docs/01-需求规格说明书.md`） |
| 概要设计（ER / 表结构 / 接口 / 状态） | 已完成（`docs/02-概要设计.md`） |
| 低保真原型（8 页） | 已完成（`prototype/index.html`） |
| Spring Boot 后端脚手架 | 已完成 |
| H2 开发库 + 6 张表 + 种子数据 | 已完成 |
| `/api/health` 健康检查 | 已完成 |
| Vue 3 + Element Plus 前端脚手架 | 已完成 |
| JWT 登录、BCrypt 校验、角色拦截 | 已完成（第 2 周） |
| 学生工作台真实六步进度 | 已完成（第 2 周） |
| 导师学生列表与详情 | 已完成（第 2 周） |
| 后端登录/越权测试（11 个） | 已完成（第 2 周） |
| 材料上传、设计评审 | 第 3 周 |
| 周报、代码仓库、测试报告 | 第 4–5 周 |
| 联调测试、部署、答辩材料 | 第 5–6 周 |

详细进度见 [`开发进度.md`](开发进度.md)。

---

## 2. 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Axios + Vue Router |
| 后端 | Java 21 + Spring Boot 3.3.5 + MyBatis-Plus 3.5.9 |
| 开发数据库 | H2（MySQL 兼容模式，文件持久化） |
| 生产数据库 | MySQL 8.0（脚本见 `db/`） |
| 测试 | JUnit 5 + MockMvc + Postman/Apifox 手工用例 |
| 原型 | 静态 HTML/CSS 低保真原型 |

> 本机未安装 MySQL 和 Docker，因此第 1 周使用 H2 启动；MySQL 建表与种子脚本已单独提供。

---

## 3. 快速启动

### 3.1 启动后端

Windows 推荐直接双击根目录的 `start-backend.bat`，它会自动打包并启动。

命令行手动启动：

```bash
cd backend
mvn -q -DskipTests package
java -jar target/thesis-platform-0.1.0.jar
```

> 本项目路径包含中文。已验证 `java -jar` 可正常运行；`mvn spring-boot:run` 在该路径下可能因 Maven 插件类路径编码问题报 `ClassNotFoundException`，因此不作为推荐命令。使用 IDEA 时可直接运行 `ThesisApplication`。

访问健康检查：

```bash
curl http://localhost:8080/api/health
```

预期返回 `code = 0`、`database = UP`、`userCount = 3`。

H2 控制台（仅开发环境）：

- 地址：<http://localhost:8080/h2-console>
- JDBC URL：`jdbc:h2:file:./data/thesisdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`
- 用户名：`sa`
- 密码：留空

### 3.2 启动前端

Windows 推荐直接双击根目录的 `start-frontend.bat`。

命令行手动启动：

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。

登录页会自动请求 `/api/health`，显示后端与数据库连接状态。

也可以双击 `start-all.bat`，同时打开后端和前端两个命令行窗口。

### 3.3 查看低保真原型

直接双击打开：

```text
prototype/index.html
```

无需启动服务。原型包含 8 个静态页面，按钮不提交真实数据。

---

## 4. 演示账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| `stu001` | `123456` | 学生 | 张同学，课题：基于 Spring Boot 的图书管理系统 |
| `stu002` | `123456` | 学生 | 李同学，课题：校园二手交易平台 |
| `tea001` | `123456` | 导师 | 王老师，名下 2 名学生 |

密码使用 BCrypt 哈希存储。登录成功后返回 JWT（有效期 8 小时），前端保存到 `localStorage`，后续请求通过 `Authorization: Bearer <token>` 携带。学生与导师的角色、数据归属均由后端强制校验：学生访问导师接口、导师访问学生接口均返回 403。

---

## 5. 项目结构

```text
毕业论文管理系统/
├─ backend/                    Spring Boot 后端
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/example/thesis/
│     │  ├─ auth/              JWT 服务、登录拦截器、当前用户上下文
│     │  ├─ common/            统一响应与异常处理
│     │  ├─ config/            CORS、拦截器注册、BCrypt Bean
│     │  ├─ controller/        Auth / Health / Student / Advisor
│     │  ├─ dto/               登录、六步状态、学生/导师视图
│     │  ├─ entity/            User / Project / Artifact / DesignReview
│     │  ├─ mapper/            MyBatis-Plus Mapper
│     │  └─ service/           登录、项目进度与数据隔离
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  ├─ schema.sql         H2 建表
│     │  └─ data.sql           种子数据
│     └─ test/                 健康检查、登录、越权与数据隔离测试
├─ frontend/                   Vue 3 前端
│  └─ src/
│     ├─ api/http.js           Axios 与 JWT 请求头
│     ├─ auth/session.js       Token / 用户本地存储
│     ├─ router/index.js       角色路由守卫
│     ├─ utils/status.js       六步状态展示映射
│     ├─ views/
│     │  ├─ LoginView.vue
│     │  ├─ StudentWorkbenchView.vue
│     │  └─ AdvisorHomeView.vue
│     └─ styles.css
├─ prototype/                  8 个低保真页面 + 首页
├─ docs/
│  ├─ 01-需求规格说明书.md
│  ├─ 02-概要设计.md
│  ├─ 实验报告1.md
│  ├─ 实验报告2.md
│  └─ screenshots/             第 2 周学生/导师页面截图
├─ db/
│  ├─ schema-mysql.sql         MySQL 建表
│  └─ data-mysql.sql           MySQL 种子数据
├─ uploads/                    文件上传目录（第 3 周使用）
├─ plan.md                     6 周开发计划
├─ 开发进度.md                 累计实验进度记录（当前至第 2 周）
└─ README.md
```

---

## 6. 已完成交付物（第 1–2 周）

**第 1 周**

- 需求规格说明书 v0.1（3–5 页，含流程、功能需求、验收与追踪矩阵）
- 概要设计 v0.1（架构、ER 图、6 张表、16 个接口、状态规则）
- 低保真原型 8 页（登录、学生工作台、材料、设计评审、周报、仓库、测试报告、导师列表）
- 可运行后端脚手架（H2 + 种子数据 + 健康检查 + 统一响应）
- 可构建前端脚手架（Vue 3 + Element Plus + 路由 + 登录页 + 占位页）
- MySQL 建表与种子脚本
- 课程实验报告 1
- 开发进度记录

**第 2 周**

- JWT 登录、BCrypt 校验、8 小时有效期
- 登录拦截器与 `/api/student/**`、`/api/advisor/**` 角色隔离
- `POST /api/auth/login`、`GET /api/auth/me`
- `GET /api/student/project`：学生本人课题 + 六步进度（前 3 步真实计算）
- `GET /api/advisor/students`、`GET /api/advisor/students/{projectId}`：仅限名下学生
- 前端 Token 存储、Axios 鉴权头、路由守卫、401 自动回登录
- 学生工作台与导师列表接入真实数据，导师端支持查看学生详情抽屉
- 11 个后端测试（登录、401/403、数据隔离、六步初始状态）
- 课程实验报告 2、页面截图

### 第 2 周验收结果

| 验收项 | 结果 |
|---|---|
| `stu001` / `tea001` 登录并返回正确角色 | 通过 |
| 错误密码返回 401 且不泄露账号是否存在 | 通过 |
| 未登录访问受保护接口返回 401 | 通过 |
| 学生访问导师接口、导师访问学生接口返回 403 | 通过 |
| 学生只能读取自己的项目 | 通过 |
| 导师只能读取名下 2 名学生，访问其他导师项目返回 403 | 通过 |
| 前端登录、路由守卫、真实学生/导师页面 | 通过（无头浏览器验证） |
| 后端测试与前端构建 | 11 个测试通过；构建成功 |

---

## 7. 常见问题

**Q：后端启动报数据库被占用？**
H2 文件库同一时间只能被一个进程打开。关闭其他后端进程后重试；测试使用内存库，不受影响。

**Q：前端健康检查显示“后端未连接”？**
确认后端已在 8080 端口启动；前端开发服务器通过 Vite 代理访问 `/api`。

**Q：为什么不用 MySQL？**
当前开发机未安装 MySQL，第 1 周用 H2 降低启动成本。`db/schema-mysql.sql` 和 `db/data-mysql.sql` 可直接用于 MySQL 8.0。

**Q：为什么学生访问导师接口返回 403？**
系统按角色和路径隔离权限：`/api/student/**` 仅学生可访问，`/api/advisor/**` 仅导师可访问。即使持有合法 Token，角色不匹配也会返回 403。

**Q：JWT 密钥如何配置？**
开发环境在 `application.yml` 中提供默认密钥；部署时请设置环境变量 `JWT_SECRET` 覆盖，密钥至少 32 字节。Token 有效期默认 8 小时。

**Q：登录过期或 Token 失效会怎样？**
后端返回 401，前端清除本地会话并跳转登录页；重新登录即可。
