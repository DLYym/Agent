# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

Text2Sql 是一个基于 Spring Boot + LangChain4j + RAG 的 AI 驱动的 SQL 助手，将自然语言问题转换为可执行的 SQL 查询。系统支持多种数据库（MySQL、Oracle、PostgreSQL），并采用 ReAct agent 工作流实现具有错误恢复能力的健壮 SQL 生成。

## 构建与运行命令

### 开发环境
```bash
# 使用 'local' profile 本地开发
./mvnw spring-boot:run

# 使用 'server' profile 本地开发（类生产环境）
./mvnw spring-boot:run -Dspring-boot.run.profiles=server

# 运行测试
./mvnw test

# 打包生产版本
./mvnw clean package -DskipTests
```

### Docker 部署
```bash
# 使用 Docker Compose 构建并运行
docker compose up -d --build

# 查看日志
docker compose logs -f text2sql-app
```

## 架构

### 核心组件

1. **ReAct Agent 工作流** (`AgentWorkflowServiceImpl`)
   - 7步流程：规划 → 检索 Schema → 检查 → 草拟 SQL → 验证 → 修复 → 执行
   - 支持流式输出以提供实时反馈
   - 失败时自动修复 SQL（最多 2 次尝试）

2. **Schema RAG 系统**
   - 混合检索：向量相似度 + 关键词匹配
   - 支持 Elasticsearch，回退到内存存储
   - 从 JDBC 元数据自动提取 Schema

3. **动态数据源管理**
   - 基于 Caffeine 的缓存，使用 LRU 淘汰策略
   - HikariCP 连接池
   - 支持多数据源和路由

4. **安全层**
   - 基于 Regex 阻止危险操作（DELETE/DROP/UPDATE）
   - 强制只读连接
   - SQL 执行前需要用户确认

### 关键服务依赖关系

```
SqlPilotController
├── AgentWorkflowService (ReAct 工作流编排)
│   ├── SchemaHybridRetriever (Schema 搜索)
│   │   ├── DatabaseSchemaExtractor (JDBC 元数据)
│   │   └── SchemaIndexStore (向量存储)
│   ├── SqlGenerator/StreamingSqlGenerator (LLM 集成)
│   ├── SqlRepairGenerator/StreamingSqlRepairGenerator (错误恢复)
│   └── ReadOnlySqlExecutor (安全 SQL 执行)
└── DynamicDataSourceManager (连接池)
```

## 配置

### 环境配置文件
- `local`（默认）：无 URL 前缀，本地开发
- `server`：添加 `/text2sql` 上下文路径用于生产部署

### 关键配置属性
```yaml
# RAG 配置
app.rag.schema-top-k: 6
app.rag.schema-focus-top-k: 3
app.rag.vector-weight: 0.65
app.rag.keyword-weight: 0.35

# Elasticsearch（可选）
app.elasticsearch.enabled: false
app.elasticsearch.base-url: http://elasticsearch:9200

# 数据源缓存
app.datasource-cache.max-size: 32
app.datasource-cache.expire-after-access: 30m

# 工作流
app.workflow.max-repair-attempts: 2
app.workflow.auto-execute-generated-sql: true
```

### 数据库支持
- **MySQL**: `jdbc:mysql://host:port/db`
- **Oracle**: `jdbc:oracle:thin:@host:port:sid` 或 `jdbc:oracle:thin://host:port/service_name`
- **PostgreSQL**: `jdbc:postgresql://host:port/db`

## 数据库 Schema 提取

系统使用 JDBC 元数据提取：
- 表名和注释
- 列名、类型、注释
- 主键信息
- 外键关系

提取的 Schema 被转换为搜索文档用于 RAG 检索。

## API 端点

- `POST /api/sql/generate` - 根据自然语言生成 SQL
- `POST /api/sql/execute` - 执行生成的 SQL
- `POST /api/sql/test-connection` - 测试数据库连接
- `POST /api/sql/databases` - 列出可用数据库
- `POST /api/sql/analyze-stream` - 流式输出 ReAct 工作流步骤
- `POST /api/sql/preview` - 预览工作流（不执行）

## 测试

单元测试覆盖核心功能，无需外部依赖：
- 数据源缓存和 LRU 淘汰
- Schema 提取和混合检索
- 多数据源路由
- Text-to-SQL 服务链

使用 `./mvnw test` 或 `./mvnw test -Dspring.profiles.active=local` 运行

## 前端

Vue 3 静态应用程序：
- 位置：`src/main/resources/static/index.html`
- 使用 CDN 加载 Vue.js 和 Element Plus
- 基于 URL 上下文自动解析 API 路径

## 开发说明

1. **LLM 配置**：在 application.yml 中设置 OpenAI API key
2. **ElasticSearch**：可选，回退到内存存储
3. **数据库 Schema**：按需提取并缓存
4. **SQL 生成**：使用 `sql_prompt.md` 和 `sql_repair_prompt.md` 中的提示词
5. **安全**：`ReadOnlySqlExecutor` 中的正则表达式模式防止危险操作
6. **流式输出**：通过 `analyze-stream` 端点提供实时 token 流式输出

## 数据梳理工作指南

如果要使用此工程进行自然语言生成 SQL，需要进行系统化的数据梳理工作，具体如下：

### 一、基础数据准备

#### 1. 数据库 Schema 提取与验证
**必要性**：Schema 是系统工作的基础数据源

**具体工作**：
- **表结构完整性检查**
  - 确保所有业务表都有清晰的注释
  - 验证字段名规范（避免使用特殊字符、中文等）
  - 检查关键字段是否标注主键、是否可空

- **注释标准化**
  - 表注释格式：建议使用"业ba务领域-表用途"格式
  - 字段注释包含：业务含义+数据类型+单位（如："用户ID BIGINT 主键标识"）
  - 为核心业务表添加"核心表-"前缀标记

- **数据类型统一**
  - 统一相似数据类型的命名规范
  - 确保日期、金额等关键字段类型一致

#### 2. 数据质量评估
**评估维度**：
- **注释覆盖率**：目标 > 90% 的表和字段都有注释
- **字段完整性**：关键字段（如ID、时间戳、状态）必须完整
- **命名规范**：表名、字段名符合统一的命名约定
- **数据一致性**：跨表关联的字段类型和含义一致

### 二、业务梳理与标注

#### 1. 业务领域划分
**工作内容**：
- 按业务模块划分数据源（如：订单系统、用户系统、商品系统）
- 为每个数据源设置清晰的业务标识
- 建立数据源间的关联关系图

#### 2. 核心表识别与标注
**重要程度分级**：
- **核心表**：主要业务表（如订单、用户、商品）
- **关联表**：中间表、关系表
- **维度表**：基础数据表（如地区分类、状态枚举）
- **日志表**：记录型表（通常不用于查询）

**标注方法**：
- 在表注释前添加前缀：`核心表-`、`关联表-`、`维度表-`
- 为干扰表添加 `干扰表-` 标记，避免模型误匹配

#### 3. 业务术语标准化
**术语库建设**：
- 建立业务术语与字段的映射关系
- 处理同义词（如：订单单号=order_id=订单ID）
- 统一度量单位（金额用元/万元，时间用天/小时）

### 三、数据治理优化

#### 1. 数据源管理
**配置优化**：
```yaml
# application.yml 优化配置
app:
  datasource-cache:
    max-size: 32              # 根据并发量调整
    expire-after-access: 30m  # 根据访问频率调整
  rag:
    schema-top-k: 6           # 宽召回数量
    schema-focus-top-k: 3      # 聚焦数量
    vector-weight: 0.65        # 向量权重
    keyword-weight: 0.35       # 关键词权重
```

#### 2. 索引优化
**检索策略调整**：
- 根据业务特点调整向量与关键词权重
- 为高频查询的表名设置精确匹配优先级
- 建立业务关键词与表的映射关系

#### 3. 性能监控
**监控指标**：
- Schema 检索响应时间 < 500ms
- SQL 生成成功率 > 85%
- 连接池使用率 < 80%
- Token 消耗量监控

### 四、测试与验证

#### 1. 数据质量测试
**测试用例设计**：
```java
// Schema 质量测试示例
@Test
void shouldValidateSchemaQuality() {
    // 测试注释覆盖率
    assertThat(schema.getTableCommentCoverage()).isGreaterThan(0.9);
    
    // 测试关键字段完整性
    assertThat(schema.containsRequiredFields("order", 
        List.of("id", "user_id", "amount", "create_time"))).isTrue();
    
    // 测试数据类型一致性
    assertThat(schema.checkDataTypeConsistency()).isTrue();
}
```

#### 2. 生成质量验证
**验证维度**：
- SQL 准确性：生成的 SQL 是否能正确执行
- 语义理解：是否正确理解用户意图
- 性能指标：查询响应时间、数据量控制

#### 3. 用户反馈收集
**反馈机制**：
- 记录用户问题和生成结果的对比例子
- 收集用户对 SQL 准确性的评分
- 建立常见问题库和优化方向

### 五、生产环境部署

#### 1. 数据源管理策略
**动态数据源配置**：
- 数据源连接池：支持多数据源动态切换
- 缓存策略：LRU 缓存常用数据源
- 降级方案：数据源不可用时使用内存回退

#### 2. 监控与告警
**监控指标**：
- 数据源连接状态
- Schema 检索成功率
- SQL 生成成功率
- Token 使用量趋势

#### 3. 运维自动化
**自动化流程**：
- 定期 Schema 同步
- 数据源健康检查
- 性能基线监控
- 异常自动修复

### 六、持续优化

#### 1. 数据质量持续改进
**优化方向**：
- 根据用户反馈补充缺失的注释
- 调整检索权重以适应业务特点
- 优化提示词以提升生成质量

#### 2. 业务扩展支持
**扩展能力**：
- 支持新数据源的快速接入
- 业务规则动态更新
- 多语言注释支持

### 七、实施建议

#### 1. 分阶段实施
1. **第一阶段**：核心业务数据梳理（1-2周）
   - 提取现有 Schema
   - 补充关键注释
   - 建立基础测试用例

2. **第二阶段**：数据质量提升（2-3周）
   - 完善注释体系
   - 建立业务术语库
   - 优化检索策略

3. **第三阶段**：生产部署与监控（1-2周）
   - 部署到生产环境
   - 建立监控体系
   - 收集用户反馈

#### 2. 关键成功因素
- **业务参与**：需要业务人员深度参与数据标注
- **技术支持**：确保数据库访问权限和性能支持
- **持续迭代**：建立数据质量持续改进机制

通过系统化的数据梳理工作，可以显著提升 Text2Sql 系统的 SQL 生成质量和用户体验，为企业的智能数据分析提供有力支撑。