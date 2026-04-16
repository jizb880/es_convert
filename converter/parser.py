"""Parse Elasticsearch query DSL into an intermediate representation."""

import json
import re


def parse_es_query(raw_text: str) -> dict:
    """解析 ES 查询原始文本为结构化字典。
    Parse raw ES query text (with optional GET/POST line) into a structured dict.

    The input may optionally start with a line like ``GET /index_name/_search``,
    from which the index (table) name is extracted.

    Args:
        raw_text: The full ES query string, including optional HTTP method line
                  and JSON body.

    Returns:
        A dict with keys: index, source, from, size, query, sort, aggs.
    """
    lines = raw_text.strip().splitlines()

    index = None
    json_start = 0

    # Try to extract index from GET/POST line
    for i, line in enumerate(lines):
        line = line.strip()
        m = re.match(r'^(?:GET|POST|PUT)\s+/?([^/\s]+)/?_search', line, re.IGNORECASE)
        if m:
            index = m.group(1)
            json_start = i + 1
            break

    # Find the JSON body
    json_text = "\n".join(lines[json_start:]).strip()
    if not json_text:
        json_text = "{}"

    body = json.loads(json_text)

    return {
        "index": index or "unknown_table",
        "source": body.get("_source"),
        "from": body.get("from", 0),
        "size": body.get("size"),
        "query": _parse_query_node(body.get("query", {"match_all": {}})),
        "sort": _parse_sort(body.get("sort", [])),
        "aggs": _parse_aggs(body.get("aggs") or body.get("aggregations")),
    }


def _parse_query_node(node: dict) -> dict:
    """递归解析 ES 查询节点为中间表示。
    Recursively parse an ES query node into an intermediate representation.

    Supports: match_all, match, match_phrase, multi_match, term, terms,
    range, wildcard, exists, bool (must/should/must_not/filter).

    Args:
        node: A single ES query clause dict, e.g. {"match": {"title": "foo"}}.

    Returns:
        A normalized dict with a "type" key and type-specific fields.
    """
    if not node:
        return {"type": "match_all"}

    if "match_all" in node:
        return {"type": "match_all"}

    if "match" in node:
        field, value = next(iter(node["match"].items()))
        if isinstance(value, dict):
            value = value.get("query", value)
        return {"type": "match", "field": field, "value": value}

    if "match_phrase" in node:
        field, value = next(iter(node["match_phrase"].items()))
        if isinstance(value, dict):
            value = value.get("query", value)
        return {"type": "match_phrase", "field": field, "value": value}

    if "multi_match" in node:
        mm = node["multi_match"]
        return {
            "type": "multi_match",
            "fields": mm.get("fields", []),
            "value": mm.get("query", ""),
        }

    if "term" in node:
        field, value = next(iter(node["term"].items()))
        if isinstance(value, dict):
            value = value.get("value", value)
        return {"type": "term", "field": field, "value": value}

    if "terms" in node:
        field, values = next(iter(node["terms"].items()))
        return {"type": "terms", "field": field, "values": values}

    if "range" in node:
        field, conditions = next(iter(node["range"].items()))
        return {"type": "range", "field": field, "conditions": conditions}

    if "wildcard" in node:
        field, value = next(iter(node["wildcard"].items()))
        if isinstance(value, dict):
            value = value.get("value", value)
        return {"type": "wildcard", "field": field, "value": value}

    if "exists" in node:
        return {"type": "exists", "field": node["exists"]["field"]}

    if "bool" in node:
        b = node["bool"]
        return {
            "type": "bool",
            "must": [_parse_query_node(c) for c in b.get("must", [])],
            "should": [_parse_query_node(c) for c in b.get("should", [])],
            "must_not": [_parse_query_node(c) for c in b.get("must_not", [])],
            "filter": [_parse_query_node(c) for c in b.get("filter", [])],
        }

    return {"type": "match_all"}


def _parse_sort(sort_list) -> list:
    """解析 ES sort 数组为排序字段列表。
    Parse ES sort array into a list of {field, order} dicts.

    Handles both simple string entries (``"field"``) and dict entries
    (``{"field": "desc"}`` or ``{"field": {"order": "desc"}}``).

    Args:
        sort_list: The "sort" value from the ES query body.

    Returns:
        List of dicts, each with "field" (str) and "order" ("ASC"/"DESC").
    """
    if not sort_list:
        return []
    result = []
    for item in sort_list:
        if isinstance(item, str):
            result.append({"field": item, "order": "ASC"})
        elif isinstance(item, dict):
            for field, val in item.items():
                if isinstance(val, str):
                    order = val.upper()
                elif isinstance(val, dict):
                    order = val.get("order", "asc").upper()
                else:
                    order = "ASC"
                result.append({"field": field, "order": order})
    return result


def _parse_aggs(aggs: dict | None) -> list | None:
    """解析 ES 聚合定义为聚合描述列表。
    Parse ES aggregation definitions into a list of aggregation descriptors.

    Supports: terms, avg, sum, min, max, value_count, cardinality.

    Args:
        aggs: The "aggs" or "aggregations" dict from the ES query body.

    Returns:
        A list of dicts with keys: name, type, field; or None if no aggs.
    """
    if not aggs:
        return None
    result = []
    for name, body in aggs.items():
        agg_type = None
        field = None
        for t in ("terms", "avg", "sum", "min", "max", "value_count", "cardinality"):
            if t in body:
                agg_type = t
                field = body[t].get("field")
                break
        if agg_type:
            result.append({"name": name, "type": agg_type, "field": field})
    return result or None
