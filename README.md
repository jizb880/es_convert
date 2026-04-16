# ES Query to SQL Converter

<details open>
<summary><b>English</b></summary>

A tool that converts Elasticsearch query DSL into standard SQL statements. Available in both **Python** and **Java**.

## Project Structure

```
es_convert/
├── README.md                            # Bilingual documentation
├── LICENSE
├── query.json                           # Shared sample ES query input
│
├── python/                              # Python version
│   ├── main.py                          # Entry point, reads config.ini
│   ├── config.ini                       # Configuration (input path, output dir)
│   ├── converter/
│   │   ├── __init__.py                  # Exports parse_es_query, build_sql
│   │   ├── parser.py                    # Parses ES query DSL → intermediate dict
│   │   └── sql_builder.py              # Builds SQL string from intermediate dict
│   ├── test/
│   │   ├── __init__.py
│   │   └── test_converter.py            # 7 unit tests covering all query types
│   └── result/
│       └── result.txt                   # Generated SQL output
│
└── java/                                # Java version
    ├── pom.xml                          # Maven build (Java 8+, org.json, JUnit 5)
    ├── config.properties                # Configuration (input path, output dir)
    ├── src/main/java/esconvert/
    │   ├── Main.java                    # Entry point, reads config.properties
    │   ├── EsParser.java                # Parses ES query DSL → JSONObject
    │   └── SqlBuilder.java              # Builds SQL string from JSONObject
    ├── src/test/java/esconvert/
    │   └── ConverterTest.java           # 7 JUnit 5 tests (mirrors Python tests)
    └── result/
        └── result.txt                   # Generated SQL output
```

## Configuration

### Python (`python/config.ini`)

```ini
[input]
query_file = ../query.json

[output]
result_dir = ./result
```

### Java (`java/config.properties`)

```properties
query_file=../query.json
result_dir=./result
```

## Usage

### Python

```bash
cd python
python main.py
```

Run tests:

```bash
cd python
python -m unittest test.test_converter -v
```

### Java

Build and run with Maven:

```bash
cd java
mvn package -q
java -jar target/es-query-to-sql-1.0.0.jar
```

Or compile manually:

```bash
cd java
mkdir -p target/classes
javac -cp lib/json-20240303.jar -d target/classes src/main/java/esconvert/*.java
java -cp target/classes:lib/json-20240303.jar esconvert.Main
```

Run tests:

```bash
cd java
mvn test
```

### Sample Input (`query.json`)

```
GET /my_index/_search
{
  "from": 0,
  "size": 10,
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "elasticsearch" } }
      ]
    }
  },
  "sort": [{ "created_at": "desc" }],
  "_source": ["title", "author"]
}
```

The SQL result is printed to the screen and saved to `result/result.txt`.

## Supported ES Query Keywords

| ES Keyword | SQL Equivalent | Description |
|---|---|---|
| `match_all` | (no WHERE) | Match all documents |
| `match` | `LIKE '%value%'` | Full-text match |
| `match_phrase` | `LIKE '%value%'` | Phrase match |
| `multi_match` | `field1 LIKE ... OR field2 LIKE ...` | Match across multiple fields |
| `term` | `= value` | Exact match |
| `terms` | `IN (v1, v2, ...)` | Match any of the values |
| `range` | `>`, `>=`, `<`, `<=` | Range queries (gt/gte/lt/lte) |
| `wildcard` | `LIKE` with `%` / `_` | Wildcard pattern match |
| `exists` | `IS NOT NULL` | Field existence check |
| `bool.must` | `AND` | All conditions must match |
| `bool.should` | `OR` | Any condition may match |
| `bool.must_not` | `NOT (...)` | Conditions must not match |
| `bool.filter` | `AND` (same as must) | Filter context |
| `_source` | `SELECT field1, field2` | Field selection |
| `from` | `OFFSET` | Pagination offset |
| `size` | `LIMIT` | Number of results |
| `sort` | `ORDER BY` | Result ordering |
| `aggs` (terms) | `GROUP BY` + `COUNT(*)` | Terms aggregation |
| `aggs` (avg/sum/min/max) | `AVG()`/`SUM()`/`MIN()`/`MAX()` | Metric aggregations |
| `aggs` (value_count) | `COUNT()` | Count aggregation |
| `aggs` (cardinality) | `COUNT(DISTINCT)` | Distinct count |

## Requirements

| Version | Requirement |
|---|---|
| Python | 3.6+ (no external dependencies) |
| Java | JDK 8+, Maven 3.6+ (dependency: `org.json:json`) |

</details>

<details>
<summary><b>中文</b></summary>

一个将 Elasticsearch 查询 DSL 转换为标准 SQL 语句的工具，提供 **Python** 和 **Java** 两个版本。

## 项目结构

```
es_convert/
├── README.md                            # 中英文双语文档
├── LICENSE
├── query.json                           # 共享的示例 ES 查询输入
│
├── python/                              # Python 版本
│   ├── main.py                          # 程序入口，读取 config.ini
│   ├── config.ini                       # 配置文件（输入路径、输出目录）
│   ├── converter/
│   │   ├── __init__.py                  # 导出 parse_es_query、build_sql
│   │   ├── parser.py                    # 将 ES 查询 DSL 解析为中间字典
│   │   └── sql_builder.py              # 根据中间字典生成 SQL 字符串
│   ├── test/
│   │   ├── __init__.py
│   │   └── test_converter.py            # 7 个单元测试，覆盖所有查询类型
│   └── result/
│       └── result.txt                   # 生成的 SQL 输出
│
└── java/                                # Java 版本
    ├── pom.xml                          # Maven 构建（Java 8+、org.json、JUnit 5）
    ├── config.properties                # 配置文件（输入路径、输出目录）
    ├── src/main/java/esconvert/
    │   ├── Main.java                    # 程序入口，读取 config.properties
    │   ├── EsParser.java                # 将 ES 查询 DSL 解析为 JSONObject
    │   └── SqlBuilder.java              # 根据 JSONObject 生成 SQL 字符串
    ├── src/test/java/esconvert/
    │   └── ConverterTest.java           # 7 个 JUnit 5 测试（与 Python 测试一一对应）
    └── result/
        └── result.txt                   # 生成的 SQL 输出
```

## 配置说明

### Python (`python/config.ini`)

```ini
[input]
query_file = ../query.json

[output]
result_dir = ./result
```

### Java (`java/config.properties`)

```properties
query_file=../query.json
result_dir=./result
```

## 使用方法

### Python

```bash
cd python
python main.py
```

运行测试：

```bash
cd python
python -m unittest test.test_converter -v
```

### Java

使用 Maven 构建并运行：

```bash
cd java
mvn package -q
java -jar target/es-query-to-sql-1.0.0.jar
```

或手动编译：

```bash
cd java
mkdir -p target/classes
javac -cp lib/json-20240303.jar -d target/classes src/main/java/esconvert/*.java
java -cp target/classes:lib/json-20240303.jar esconvert.Main
```

运行测试：

```bash
cd java
mvn test
```

### 输入示例 (`query.json`)

```
GET /my_index/_search
{
  "from": 0,
  "size": 10,
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "elasticsearch" } }
      ]
    }
  },
  "sort": [{ "created_at": "desc" }],
  "_source": ["title", "author"]
}
```

SQL 结果会输出到屏幕，同时保存到 `result/result.txt`。

## 支持的 ES 查询关键词

| ES 关键词 | SQL 对应 | 说明 |
|---|---|---|
| `match_all` | （无 WHERE 条件） | 匹配所有文档 |
| `match` | `LIKE '%value%'` | 全文匹配 |
| `match_phrase` | `LIKE '%value%'` | 短语匹配 |
| `multi_match` | `field1 LIKE ... OR field2 LIKE ...` | 多字段匹配 |
| `term` | `= value` | 精确匹配 |
| `terms` | `IN (v1, v2, ...)` | 匹配多个值中的任意一个 |
| `range` | `>`、`>=`、`<`、`<=` | 范围查询（gt/gte/lt/lte） |
| `wildcard` | `LIKE`（`%` / `_`） | 通配符匹配 |
| `exists` | `IS NOT NULL` | 字段存在性检查 |
| `bool.must` | `AND` | 所有条件必须匹配 |
| `bool.should` | `OR` | 任意条件匹配即可 |
| `bool.must_not` | `NOT (...)` | 条件不得匹配 |
| `bool.filter` | `AND`（同 must） | 过滤上下文 |
| `_source` | `SELECT field1, field2` | 字段选择 |
| `from` | `OFFSET` | 分页偏移量 |
| `size` | `LIMIT` | 返回条数 |
| `sort` | `ORDER BY` | 结果排序 |
| `aggs`（terms） | `GROUP BY` + `COUNT(*)` | 分桶聚合 |
| `aggs`（avg/sum/min/max） | `AVG()`/`SUM()`/`MIN()`/`MAX()` | 指标聚合 |
| `aggs`（value_count） | `COUNT()` | 计数聚合 |
| `aggs`（cardinality） | `COUNT(DISTINCT)` | 去重计数 |

## 环境要求

| 版本 | 要求 |
|---|---|
| Python | 3.6+（无需外部依赖） |
| Java | JDK 8+，Maven 3.6+（依赖：`org.json:json`） |

</details>
