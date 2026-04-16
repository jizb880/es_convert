# ES 查询转 SQL 转换器

一个将 Elasticsearch 查询 DSL 转换为标准 SQL 语句的 Python 工具。

## 项目结构

```
es_convert/
├── main.py              # 程序入口
├── config.ini           # 配置文件
├── query.json           # 输入的 ES 查询（示例）
├── converter/
│   ├── __init__.py
│   ├── parser.py        # 将 ES 查询 DSL 解析为中间表示
│   └── sql_builder.py   # 根据中间表示生成 SQL
└── result/
    └── result.txt       # 生成的 SQL 输出
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
