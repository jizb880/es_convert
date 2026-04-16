# ES Query to SQL Converter

A Python tool that converts Elasticsearch query DSL into standard SQL statements.

## Project Structure

```
es_convert/
├── main.py              # Entry point
├── config.ini           # Configuration file
├── query.json           # Input ES query (example)
├── converter/
│   ├── __init__.py
│   ├── parser.py        # Parses ES query DSL into intermediate representation
│   └── sql_builder.py   # Builds SQL from the intermediate representation
└── result/
    └── result.txt       # Generated SQL output
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
