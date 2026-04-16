"""Build SQL from the intermediate representation produced by parser.py."""


def build_sql(parsed: dict) -> str:
    """将解析后的 ES 查询字典转换为 SQL 字符串。
    Convert a parsed ES query dict to a SQL string.

    Routes to aggregation SQL if aggs are present, otherwise builds a
    standard SELECT query.

    Args:
        parsed: The dict returned by ``parse_es_query()``.

    Returns:
        A complete SQL statement string ending with ``;``.
    """
    # If aggregations exist, generate aggregation SQL
    if parsed["aggs"]:
        return _build_agg_sql(parsed)

    return _build_select_sql(parsed)


def _build_select_sql(parsed: dict) -> str:
    """构建标准 SELECT 查询语句。
    Build a standard SELECT/FROM/WHERE/ORDER BY/LIMIT SQL statement.

    Args:
        parsed: The parsed ES query dict.

    Returns:
        SQL string with SELECT, FROM, optional WHERE/ORDER BY/LIMIT/OFFSET.
    """
    # SELECT
    if parsed["source"]:
        columns = ", ".join(parsed["source"])
    else:
        columns = "*"

    sql = f"SELECT {columns}\nFROM {parsed['index']}"

    # WHERE
    where = _build_where(parsed["query"])
    if where:
        sql += f"\nWHERE {where}"

    # ORDER BY
    if parsed["sort"]:
        parts = [f"{s['field']} {s['order']}" for s in parsed["sort"]]
        sql += f"\nORDER BY {', '.join(parts)}"

    # LIMIT / OFFSET
    if parsed["size"] is not None:
        sql += f"\nLIMIT {parsed['size']}"
    if parsed["from"]:
        sql += f"\nOFFSET {parsed['from']}"

    return sql + ";"


def _build_agg_sql(parsed: dict) -> str:
    """构建聚合查询的 SQL 语句（包含 GROUP BY）。
    Build an aggregation SQL statement with GROUP BY.

    Maps ES aggregation types to SQL aggregate functions:
    terms -> GROUP BY + COUNT(*), avg/sum/min/max -> corresponding SQL function,
    value_count -> COUNT(), cardinality -> COUNT(DISTINCT).

    Args:
        parsed: The parsed ES query dict containing aggs.

    Returns:
        SQL string with SELECT (aggregates), FROM, optional WHERE, GROUP BY.
    """
    agg_map = {
        "avg": "AVG",
        "sum": "SUM",
        "min": "MIN",
        "max": "MAX",
        "value_count": "COUNT",
        "cardinality": "COUNT(DISTINCT",
    }

    group_fields = []
    select_parts = []

    for agg in parsed["aggs"]:
        if agg["type"] == "terms":
            group_fields.append(agg["field"])
            select_parts.append(agg["field"])
            select_parts.append(f"COUNT(*) AS {agg['name']}_count")
        elif agg["type"] == "cardinality":
            select_parts.append(f"COUNT(DISTINCT {agg['field']}) AS {agg['name']}")
        elif agg["type"] in agg_map:
            func = agg_map[agg["type"]]
            select_parts.append(f"{func}({agg['field']}) AS {agg['name']}")

    if not select_parts:
        select_parts = ["*"]

    sql = f"SELECT {', '.join(select_parts)}\nFROM {parsed['index']}"

    where = _build_where(parsed["query"])
    if where:
        sql += f"\nWHERE {where}"

    if group_fields:
        sql += f"\nGROUP BY {', '.join(group_fields)}"

    return sql + ";"


def _build_where(node: dict) -> str:
    """递归地将查询节点转换为 SQL WHERE 子句。
    Recursively convert a parsed query node into a SQL WHERE clause string.

    Args:
        node: A parsed query node dict with a "type" key.

    Returns:
        A SQL condition string, or empty string for match_all.
    """
    t = node["type"]

    if t == "match_all":
        return ""

    if t in ("match", "match_phrase"):
        return f"{node['field']} LIKE {_quote(f'%{node["value"]}%')}"

    if t == "multi_match":
        clauses = [f"{f} LIKE {_quote(f'%{node["value"]}%')}" for f in node["fields"]]
        return f"({' OR '.join(clauses)})"

    if t == "term":
        return f"{node['field']} = {_quote(node['value'])}"

    if t == "terms":
        vals = ", ".join(_quote(v) for v in node["values"])
        return f"{node['field']} IN ({vals})"

    if t == "range":
        ops = {"gt": ">", "gte": ">=", "lt": "<", "lte": "<="}
        parts = []
        for op, val in node["conditions"].items():
            if op in ops:
                parts.append(f"{node['field']} {ops[op]} {_quote(val)}")
        return " AND ".join(parts)

    if t == "wildcard":
        sql_pattern = node["value"].replace("*", "%").replace("?", "_")
        return f"{node['field']} LIKE {_quote(sql_pattern)}"

    if t == "exists":
        return f"{node['field']} IS NOT NULL"

    if t == "bool":
        all_clauses = []

        must = [_build_where(c) for c in node["must"] if _build_where(c)]
        should = [_build_where(c) for c in node["should"] if _build_where(c)]
        must_not = [_build_where(c) for c in node["must_not"] if _build_where(c)]
        filters = [_build_where(c) for c in node["filter"] if _build_where(c)]

        # must and filter are both AND
        and_clauses = must + filters
        if and_clauses:
            all_clauses.append(" AND ".join(and_clauses))

        if should:
            all_clauses.append(f"({' OR '.join(should)})")

        if must_not:
            negated = [f"NOT ({c})" for c in must_not]
            all_clauses.append(" AND ".join(negated))

        return " AND ".join(all_clauses)

    return ""


def _quote(value) -> str:
    """将值转换为 SQL 字面量（数字保持原样，字符串加单引号并转义）。
    Convert a value to a SQL literal. Numbers stay as-is; strings are
    single-quoted with internal quotes escaped.

    Args:
        value: The value to quote (int, float, or str).

    Returns:
        SQL literal string.
    """
    if isinstance(value, (int, float)):
        return str(value)
    s = str(value).replace("'", "''")
    return f"'{s}'"
