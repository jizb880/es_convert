package esconvert;

import org.json.*;

import java.util.*;
import java.util.regex.*;

/**
 * 将 Elasticsearch 查询 DSL 解析为中间表示（JSONObject）。
 * Parses Elasticsearch query DSL into an intermediate JSONObject representation.
 */
public class EsParser {

    private static final Pattern INDEX_PATTERN =
            Pattern.compile("^(?:GET|POST|PUT)\\s+/?([^/\\s]+)/?_search", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 ES 查询原始文本为结构化 JSONObject。
     * Parse raw ES query text (with optional GET/POST line) into a structured JSONObject.
     *
     * @param rawText 完整的 ES 查询字符串 / The full ES query string
     * @return 包含 index, source, from, size, query, sort, aggs 的 JSONObject
     */
    public static JSONObject parse(String rawText) {
        String[] lines = rawText.trim().split("\\r?\\n");

        String index = null;
        int jsonStart = 0;

        // Try to extract index from GET/POST/PUT line
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INDEX_PATTERN.matcher(lines[i].trim());
            if (m.find()) {
                index = m.group(1);
                jsonStart = i + 1;
                break;
            }
        }

        // Build JSON body
        StringBuilder sb = new StringBuilder();
        for (int i = jsonStart; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        String jsonText = sb.toString().trim();
        if (jsonText.isEmpty()) {
            jsonText = "{}";
        }

        JSONObject body = new JSONObject(jsonText);

        JSONObject result = new JSONObject();
        result.put("index", index != null ? index : "unknown_table");
        result.put("source", body.has("_source") ? body.getJSONArray("_source") : JSONObject.NULL);
        result.put("from", body.optInt("from", 0));
        result.put("size", body.has("size") ? body.getInt("size") : JSONObject.NULL);
        result.put("query", parseQueryNode(body.has("query") ? body.getJSONObject("query") : null));
        result.put("sort", parseSort(body.optJSONArray("sort")));
        result.put("aggs", parseAggs(body.has("aggs") ? body.getJSONObject("aggs")
                : body.has("aggregations") ? body.getJSONObject("aggregations") : null));

        return result;
    }

    /**
     * 递归解析 ES 查询节点为中间表示。
     * Recursively parse an ES query node into an intermediate representation.
     *
     * @param node 单个 ES 查询子句 / A single ES query clause JSONObject
     * @return 标准化的查询节点 / A normalized query node JSONObject with "type" key
     */
    static JSONObject parseQueryNode(JSONObject node) {
        if (node == null || node.isEmpty()) {
            return new JSONObject().put("type", "match_all");
        }

        if (node.has("match_all")) {
            return new JSONObject().put("type", "match_all");
        }

        if (node.has("match")) {
            return parseSingleFieldClause(node.getJSONObject("match"), "match");
        }

        if (node.has("match_phrase")) {
            return parseSingleFieldClause(node.getJSONObject("match_phrase"), "match_phrase");
        }

        if (node.has("multi_match")) {
            JSONObject mm = node.getJSONObject("multi_match");
            return new JSONObject()
                    .put("type", "multi_match")
                    .put("fields", mm.optJSONArray("fields"))
                    .put("value", mm.optString("query", ""));
        }

        if (node.has("term")) {
            JSONObject termObj = node.getJSONObject("term");
            String field = termObj.keys().next();
            Object raw = termObj.get(field);
            Object value = (raw instanceof JSONObject) ? ((JSONObject) raw).get("value") : raw;
            return new JSONObject().put("type", "term").put("field", field).put("value", value);
        }

        if (node.has("terms")) {
            JSONObject termsObj = node.getJSONObject("terms");
            String field = termsObj.keys().next();
            return new JSONObject().put("type", "terms").put("field", field)
                    .put("values", termsObj.getJSONArray(field));
        }

        if (node.has("range")) {
            JSONObject rangeObj = node.getJSONObject("range");
            String field = rangeObj.keys().next();
            return new JSONObject().put("type", "range").put("field", field)
                    .put("conditions", rangeObj.getJSONObject(field));
        }

        if (node.has("wildcard")) {
            JSONObject wcObj = node.getJSONObject("wildcard");
            String field = wcObj.keys().next();
            Object raw = wcObj.get(field);
            Object value = (raw instanceof JSONObject) ? ((JSONObject) raw).get("value") : raw;
            return new JSONObject().put("type", "wildcard").put("field", field).put("value", value);
        }

        if (node.has("exists")) {
            return new JSONObject().put("type", "exists")
                    .put("field", node.getJSONObject("exists").getString("field"));
        }

        if (node.has("bool")) {
            JSONObject b = node.getJSONObject("bool");
            return new JSONObject()
                    .put("type", "bool")
                    .put("must", parseNodeList(b.optJSONArray("must")))
                    .put("should", parseNodeList(b.optJSONArray("should")))
                    .put("must_not", parseNodeList(b.optJSONArray("must_not")))
                    .put("filter", parseNodeList(b.optJSONArray("filter")));
        }

        return new JSONObject().put("type", "match_all");
    }

    /**
     * 解析 match / match_phrase 等单字段查询子句。
     * Parse a single-field clause like match or match_phrase.
     */
    private static JSONObject parseSingleFieldClause(JSONObject clause, String type) {
        String field = clause.keys().next();
        Object raw = clause.get(field);
        Object value = (raw instanceof JSONObject) ? ((JSONObject) raw).opt("query") : raw;
        if (value == null) value = raw;
        return new JSONObject().put("type", type).put("field", field).put("value", value);
    }

    /**
     * 将 JSONArray 中的每个查询节点递归解析。
     * Recursively parse each query node in a JSONArray.
     */
    private static JSONArray parseNodeList(JSONArray arr) {
        JSONArray result = new JSONArray();
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            result.put(parseQueryNode(arr.getJSONObject(i)));
        }
        return result;
    }

    /**
     * 解析 ES sort 数组为排序字段列表。
     * Parse ES sort array into a JSONArray of {field, order} objects.
     *
     * @param sortArr ES 查询中的 "sort" 数组 / The "sort" JSONArray
     * @return 排序描述列表 / JSONArray of sort descriptors
     */
    static JSONArray parseSort(JSONArray sortArr) {
        JSONArray result = new JSONArray();
        if (sortArr == null) return result;
        for (int i = 0; i < sortArr.length(); i++) {
            Object item = sortArr.get(i);
            if (item instanceof String) {
                result.put(new JSONObject().put("field", item).put("order", "ASC"));
            } else if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                for (String field : obj.keySet()) {
                    Object val = obj.get(field);
                    String order;
                    if (val instanceof String) {
                        order = ((String) val).toUpperCase();
                    } else if (val instanceof JSONObject) {
                        order = ((JSONObject) val).optString("order", "asc").toUpperCase();
                    } else {
                        order = "ASC";
                    }
                    result.put(new JSONObject().put("field", field).put("order", order));
                }
            }
        }
        return result;
    }

    /**
     * 解析 ES 聚合定义为聚合描述列表。
     * Parse ES aggregation definitions into a JSONArray of aggregation descriptors.
     *
     * @param aggs ES 查询中的 "aggs" 对象 / The "aggs" JSONObject
     * @return 聚合描述列表，或 null / JSONArray of agg descriptors, or null
     */
    static Object parseAggs(JSONObject aggs) {
        if (aggs == null) return JSONObject.NULL;

        String[] types = {"terms", "avg", "sum", "min", "max", "value_count", "cardinality"};
        JSONArray result = new JSONArray();

        for (String name : aggs.keySet()) {
            JSONObject body = aggs.getJSONObject(name);
            for (String t : types) {
                if (body.has(t)) {
                    result.put(new JSONObject()
                            .put("name", name)
                            .put("type", t)
                            .put("field", body.getJSONObject(t).optString("field")));
                    break;
                }
            }
        }

        return result.isEmpty() ? JSONObject.NULL : result;
    }
}
