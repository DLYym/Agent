# DB-GPT Lite (智能 SQL 助手)
在线演示：

👉 **[https://exitzero.tech/text2sql/](http://localhost:8080/index.html)**

> 一个基于 Spring Boot + LangChain4j + RAG的 AIGC 工具，让非技术人员也能通过自然语言查询数据库。


## ✨ 项目简介

**Text2Sql** 是一个轻量级的 AI 辅助工具。它利用大模型（LLM）的能力，将用户的自然语言问题（如“查询消费最高的3个用户”）自动转换为可执行的 SQL 语句，并直接在页面展示查询结果。

不再需要手写复杂的 SQL，让数据查询变得像聊天一样简单。

## ✨ 运行效果
![img_2.png](img_2.png)
上图展示了 DB-GPT Lite 的界面，专注于开发者的沉浸式体验。

**界面核心区域说明：**

* **左侧：控制面板 (Control Panel)**
    * **数据源配置**：支持动态输入任意 JDBC URL、账号密码，并提供 **“一键测试连接”** 功能，连接成功后顶部会出现绿色状态指示条。
    * **智能提问区**：用户在此输入自然语言业务问题（如：“查询上个月消费最高的前 3 位用户”）。
    * **操作流**：清晰的“生成 SQL” -> “执行 SQL” 两步走操作，防止 AI 误操作，给予用户二次确认的机会。

* **右侧：结果反馈 (Result Dashboard)**
    * **SQL 编辑器**：高亮显示 AI 生成的 SQL 语句。支持用户手动微调 SQL，满足复杂场景需求。
    * **数据结果表**：执行成功后，自动渲染查询结果集，支持动态列头解析，直观展示业务数据。

## 🌟 核心特性

- **🚀 Text-to-SQL**: 自动将自然语言转换为标准 SQL。
- **🧠 Schema RAG 增强**: 动态抽取目标数据库的 Schema、表注释、字段注释并构建检索上下文，为 SQL 生成提供更稳定的结构化知识。
- **🤖 ReAct 工作流**: 将 SQL 生成升级为“任务规划 -> 检索 -> 草拟 -> 校验 -> 修复 -> 执行”的多步 Agent 流程，失败时可依据错误反馈自动修复 SQL。
- **🔎 混合召回**: 支持 ElasticSearch 的向量 + 关键字双路召回，能够对表名做精确匹配，降低纯向量检索导致的语义漂移。
- **🛡️ 安全防御**: 内置正则拦截与只读锁，防止 `DELETE`/`DROP` 等危险操作。
- **🔌 动态数据源**: 支持单库、多数据源请求以及动态库级路由。
- **⚡ 连接池缓存**: 使用 Caffeine 缓存动态创建的 `DataSource`，通过 LRU 淘汰低频数据源，避免频繁切库时重复初始化连接池。
- **🧪 本地可测**: 提供基于 H2 的本地单元测试，无需真实数据库、向量库或大模型即可验证核心链路。
- **📊 可视化结果**: 自动渲染查询结果表格。

## 🛠️ 技术栈

- **后端**: Java 21, Spring Boot 3, LangChain4j, RAG
- **AI 模型**: 阿里云通义千问 (Qwen-Plus) + DashScope Embedding
- **前端**: Vue 3 (CDN 模式) + Tailwind CSS
- **数据库**: MySQL 8.0+, Oracle 11.2+, PostgreSQL 10+

## 📊 多数据库支持

### 当前支持的数据库
- ✅ **MySQL 5.7/8.0** - 完整支持
- ✅ **Oracle 11.2+** - 完整支持  
- ✅ **PostgreSQL 10+** - 完整支持

### 数据库连接方式差异

#### MySQL连接
```
连接流程：连接服务器实例 → 选择具体数据库 → 执行SQL
JDBC URL：jdbc:mysql://host:port/database_name
```

#### Oracle连接  
```
连接流程：直接连接到指定服务 → 执行SQL
JDBC URL：
  SID方式：jdbc:oracle:thin:@host:port:sid
  服务名方式：jdbc:oracle:thin:@//host:port/service_name
```

### SQL语法适配

系统自动识别数据库类型并适配相应SQL语法：

| 功能 | MySQL | Oracle | PostgreSQL |
|------|-------|--------|------------|
| 限制行数 | `LIMIT 1000` | `WHERE ROWNUM <= 1000` | `LIMIT 1000` |
| 表结构查询 | `DESCRIBE table` | `SELECT * FROM user_tab_columns` | `SELECT * FROM information_schema.columns` |
| 表列表查询 | `SHOW TABLES` | `SELECT table_name FROM user_tables` | `SELECT tablename FROM pg_tables` |

## 🚀 快速开始

### 1. 环境准备
- JDK 21
- Maven 3.6+
- 一个可用的 MySQL 数据库

### 2. 克隆项目
```bash
git clone [https://github.com/kang-yang77/Text2Sql.git](https://github.com/kang-yang77/Text2Sql.git)
cd Text2Sql
```
### 3. 配置
#### 1.大模型配置
![img_1.png](img_1.png)
如上图，配置自己的api-key以及chat-model

#### 2.ElasticSearch 混合检索（可选）
默认配置下，项目会使用内存版索引完成本地开发和测试；如果你希望启用 ElasticSearch 混合检索，请在 `application.yml` 中打开：

```yaml
app:
  elasticsearch:
    enabled: true
    base-url: http://localhost:9200
    index-name: text2sql_schema
```

如果本地没有启动 ElasticSearch，系统会自动回退到内存检索，不影响开发和测试。

服务器 profile 已支持通过环境变量直接开启 ES：

```bash
APP_ELASTICSEARCH_ENABLED=true
APP_ELASTICSEARCH_BASE_URL=http://elasticsearch:9200
APP_ELASTICSEARCH_INDEX_NAME=text2sql_schema
```

#### 3.Agent Workflow 配置（可选）
默认会在生成 SQL 后自动执行，并允许最多 2 轮失败修复；你也可以通过配置调整：

```yaml
app:
  workflow:
    max-repair-attempts: 2
    auto-execute-generated-sql: true
```

### 5. 本地运行
项目默认使用 `local` profile，本地启动时不带任何 URL 前缀：

```bash
export DASHSCOPE_API_KEY=你的Key
./mvnw spring-boot:run
```

访问地址：

👉 **[http://localhost:8080/index.html](http://localhost:8080/index.html)**

### 6. 服务器运行
服务器可以切换到 `server` profile，自动挂载在 `/text2sql` 前缀下，适合和个人博客共用同一个域名：

```bash
export DASHSCOPE_API_KEY=你的Key
./mvnw spring-boot:run -Dspring-boot.run.profiles=server
```

如果你使用打包后的 Jar：

```bash
java -jar target/Database-ai-0.0.1-SNAPSHOT.jar --spring.profiles.active=server
```

访问地址：

👉 **[http://localhost:8080/text2sql/index.html](http://localhost:8080/text2sql/index.html)**

### 6.1 Docker 部署（推荐用于 2 核 2G 服务器）
仓库已提供 [Dockerfile](/Users/yangkang/Desktop/Java/Projects/AI/demo-ai/Dockerfile) 和 [docker-compose.yml](/Users/yangkang/Desktop/Java/Projects/AI/demo-ai/docker-compose.yml)。  
这套配置默认按“小内存服务器”做了限制：

- App 容器内存上限约 `768M`
- JVM 默认 `-Xms256m -Xmx512m`
- MySQL 容器内存上限约 `384M`
- ElasticSearch 容器内存上限约 `640M`
- 默认使用 `server` profile
- 默认按服务器环境启用 ElasticSearch 单节点检索，并保留内存检索兜底

**推荐部署方式：先在本地打包，再上传到服务器运行**

1. 本地打包 Jar：
```bash
./mvnw clean package -DskipTests
```

2. 把以下文件上传到服务器同一目录，例如 `/home/docker/text2sql/`：
- `target/Database-ai-0.0.1-SNAPSHOT.jar`
- `Dockerfile`
- `docker-compose.yml`
- `.env`（可由 `.env.example` 复制得到）

3. 在服务器目录中准备环境变量文件：
```bash
cp .env.example .env
```

编辑 `.env`：
```bash
DASHSCOPE_API_KEY=你的DashScopeKey
MYSQL_ROOT_PASSWORD=你的MySQL密码
MYSQL_DATABASE=text2sql_db
APP_ELASTICSEARCH_ENABLED=true
APP_ELASTICSEARCH_BASE_URL=http://elasticsearch:9200
APP_ELASTICSEARCH_INDEX_NAME=text2sql_schema
APP_ELASTICSEARCH_ALLOW_IN_MEMORY_FALLBACK=true
ELASTICSEARCH_IMAGE=docker.elastic.co/elasticsearch/elasticsearch:8.19.10
ES_JAVA_OPTS=-Xms384m -Xmx384m
```

4. 启动容器：
```bash
docker compose up -d --build
```

5. 查看日志：
```bash
docker compose logs -f text2sql-app
```

如果你想确认 ES 已经起来：

```bash
curl http://127.0.0.1:9200
```

如果返回集群信息，说明当前服务器已经切到 ElasticSearch 的向量 + 关键词多路召回。

访问地址：

👉 `http://你的服务器IP:8080/text2sql/index.html`

### 6.2 2 核 2G 机器的部署建议
- 当前仓库提供的是低内存单节点 `ElasticSearch + Spring Boot + MySQL` 组合，但这只是面向演示和轻量数据集的折中方案。
- 如果你的 Schema 很大、测试数据很多，`2G` 机器仍然可能吃紧；这是基于部署经验的推断，不是官方容量保证。
- 如果内存紧张，优先保留 ES，并适当减少 MySQL 测试数据量。
- 如果流式输出要经过 Nginx，请关闭代理缓冲，否则前端看起来会像“非流式”。

### 6.3 Docker 场景下 JDBC 该怎么填
这点很关键：**前端填写的数据库地址，是应用容器视角下的地址，不是你宿主机浏览器视角下的地址。**

#### 场景 1：MySQL 也在 Docker Compose 网络里
如果你的 MySQL 服务名叫 `mysql`，那前端应填写：

- `Host`: `mysql`
- `Port`: `3306`

JDBC 实际会拼成：
```text
jdbc:mysql://mysql:3306/数据库名?serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false
```

#### 场景 2：MySQL 跑在宿主机，端口映射为 3307
如果应用在 Docker 容器里，而 MySQL 通过宿主机 `3307` 暴露，那么前端应填写：

- `Host`: `host.docker.internal`
- `Port`: `3307`

JDBC 实际会拼成：
```text
jdbc:mysql://host.docker.internal:3307/数据库名?serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false
```

> 注意：这时不要再填 `127.0.0.1`，因为容器里的 `127.0.0.1` 指向的是应用容器自己，不是宿主机。

### 6.4 Nginx 反向代理示例
如果你和个人博客共用同一个域名，建议按 `/text2sql` 转发，并关闭缓冲：

```nginx
location /text2sql/ {
    proxy_pass http://127.0.0.1:8080/text2sql/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_cache off;
    chunked_transfer_encoding on;
}
```

### 7. 测试运行
项目启动成功后（控制台显示 `Started ...`），请打开浏览器访问：

**使用步骤：**
1. 先填写实例连接信息（Host、Port、账号、密码）。
2. 点击 **“连接并加载数据库”**，从实例中拉取可选数据库列表。
3. 手动选择目标数据库。
4. 点击样例问题或输入自然语言问题，生成 SQL。
5. 检查并执行 SQL，结果会以弹窗形式展示。

当你调用 `/api/sql/generate` 时，响应中还会额外附带：

- `steps`: ReAct 工作流的步骤轨迹
- `attempts`: 最终成功前经历的 SQL 生成/修复轮次
- `workflowStatus`: 当前工作流状态

前端现在会根据当前页面地址自动推导接口前缀：

- 本地 `local` profile 下会请求 `/api/sql/*`
- 服务器 `server` profile 下会请求 `/text2sql/api/sql/*`

## 🧪 本地测试

不依赖真实 MySQL、ElasticSearch 和大模型的核心测试可以直接运行：

```bash
mvn test
```

如果你想显式指定本地 profile：

```bash
./mvnw test -Dspring.profiles.active=local
```

当前单测覆盖了以下关键能力：

- DataSource 缓存复用与 LRU 淘汰行为
- Schema / 表注释 / 字段注释抽取
- 多数据源下的混合召回与目标库选择
- Text-to-SQL 服务链路的本地执行

## 🔌 多数据源请求示例

后端仍然兼容原先的单库请求体；如果需要多数据源路由，可以传入 `datasources`：

```json
{
  "question": "请统计 orders 表里的订单数量",
  "datasources": [
    {
      "id": "sales",
      "name": "sales",
      "dbUrl": "jdbc:mysql://localhost:3306/sales",
      "username": "root",
      "password": "123456"
    },
    {
      "id": "inventory",
      "name": "inventory",
      "dbUrl": "jdbc:mysql://localhost:3306/inventory",
      "username": "root",
      "password": "123456"
    }
  ]
}
```

返回结果会附带 `datasourceId`，便于后续执行 SQL 时继续路由到正确的数据源。
