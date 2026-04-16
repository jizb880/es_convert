# ES Query to SQL Converter

<details>
<summary><b>English</b></summary>

A Python tool that converts Elasticsearch query DSL into standard SQL statements.

## Project Structure

```
es_convert/
├── main.py                  # Entry point, reads config and runs conversion
├── config.ini               # Configuration file (input path, output dir)
├── query.json               # Example ES query input
├── converter/
│   ├── __init__.py          # Exports parse_es_query and build_sql
│   ├── parser.py            # Parses ES query DSL into intermediate representation
│   └── sql_builder.py       # Builds SQL string from intermediate representation
├── test/
│   ├── __init__.py
│   └── test_converter.py    # Unit tests (7 cases covering all query types)
└── result/
    └── result.txt           # Generated SQL output
```

## Configuration

Edit `config.ini` to set the input query file and output directory:

```ini
[input]
# Path to the Elasticsearch query JSON file
query_file = query.json

[output]
# Directory to store the result
result_dir = ./result
```

## Usage

1. Write your ES query in a file (e.g., `query.json`). The file can optionally start with a `GET /index/_search` line:

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

2. Run the converter:

```bash
python main.py
```

3. The SQL result is printed to the screen and saved to `result/result.txt`.

4. Run tests:

```bash
python -m unittest test.test_converter -v
```

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

- Python 3.10+ (no external dependencies)

</details>

<details>
<summary><b>中文</b></summary>

一个将 Elasticsearch 查询 DSL 转换为标准 SQL 语句的 Python 工具。

## 项目结构

```
es_convert/
├── main.py                  # 程序入口，读取配置并执行转换
├── config.ini               # 配置文件（输入路径、输出目录）
├── query.json               # 示例 ES 查询输入
├── converter/
│   ├── __init__.py          # 导出 parse_es_query 和 build_sql
│   ├── parser.py            # 将 ES 查询 DSL 解析为中间表示
│   └── sql_builder.py       # 根据中间表示生成 SQL 字符串
├── test/
│   ├── __init__.py
│   └── test_converter.py    # 单元测试（7 个用例，覆盖所有查询类型）
└── result/
    └── result.txt           # 生成的 SQL 输出
```

## 配置说明

编辑 `config.ini` 来设置输入查询文件和输出目录：

```ini
[input]
# Elasticsearch 查询 JSON 文件路径
query_file = query.json

[output]
# 结果存储目录
result_dir = ./result
```

## 使用方法

1. 将 ES 查询写入文件（如 `query.json`）。文件可以包含 `GET /index/_search` 开头行：

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

2. 运行转换器：

```bash
python main.py
```

3. SQL 结果会输出到屏幕，同时保存到 `result/result.txt`。

4. 运行测试：

```bash
python -m unittest test.test_converter -v
```

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

- Python 3.10+（无需外部依赖）

</details>
