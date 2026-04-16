package esconvert;

import org.json.*;

import java.util.*;

/**
 * 根据 EsParser 生成的中间表示构建 SQL 字符串。
 * Builds SQL strings from the intermediate representation produced by EsParser.
 */
public class SqlBuilder {

    /**
     * 将解析后的 ES 查询 JSONObject 转换为 SQL 字符串。
     * Convert a parsed ES query JSONObject to a SQL string.
     *
     * @param parsed EsParser.parse() 返回的 JSONObject
     * @return 以分号结尾的完整 SQL 语句 / A complete SQL statement ending with ";"
     */
    public static String build(JSONObject parsed) {
        if (parsed.get("aggs") != JSONObject.NULL) {
            return buildAggSql(parsed);
        }
        return buildSelectSql(parsed);
    }

    /**
     * 构建标准 SELECT 查询语句。
     * Build a standard SELECT/FROM/WHERE/ORDER BY/LIMIT SQL statement.
     */
    private static String buildSelectSql(JSONObject parsed) {
        // SELECT
        String columns;
        if (parsed.get("source") != JSONObject.NULL) {
            JSONArray src = parsed.getJSONArray("source");
            columns = joinArray(src);
        } else {
            columns = "*";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(columns).append("\nFROM ").append(parsed.getString("index"));

        // WHERE
        String where = buildWhere(parsed.getJSONObject("query"));
        if (!where.isEmpty()) {
            sql.append("\nWHERE ").append(where);
        }

        // ORDER BY
        JSONArray sort = parsed.getJSONArray("sort");
        if (sort.length() > 0) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < sort.length(); i++) {
                JSONObject s = sort.getJSONObject(i);
                parts.add(s.getString("field") + " " + s.getString("order"));
            }
            sql.append("\nORDER BY ").append(String.join(", ", parts));
        }

        // LIMIT / OFFSET
        if (parsed.get("size") != JSONObject.NULL) {
            sql.append("\nLIMIT ").append(parsed.getInt("size"));
        }
        if (parsed.getInt("from") > 0) {
            sql.append("\nOFFSET ").append(parsed.getInt("from"));
        }

        sql.append(";");
        return sql.toString();
    }

    /**
     * 构建聚合查询的 SQL 语句（包含 GROUP BY）。
     * Build an aggregation SQL statement with GROUP BY.
     */
    private static String buildAggSql(JSONObject parsed) {
        Map<String, String> aggMap = new LinkedHashMap<>();
        aggMap.put("avg", "AVG");
        aggMap.put("sum", "SUM");
        aggMap.put("min", "MIN");
        aggMap.put("max", "MAX");
        aggMap.put("value_count", "COUNT");

        List<String> groupFields = new ArrayList<>();
        List<String> selectParts = new ArrayList<>();

        JSONArray aggs = parsed.getJSONArray("aggs");
        for (int i = 0; i < aggs.length(); i++) {
            JSONObject agg = aggs.getJSONObject(i);
            String type = agg.getString("type");
            String field = agg.getString("field");
            String name = agg.getString("name");

            if ("terms".equals(type)) {
                groupFields.add(field);
                selectParts.add(field);
                selectParts.add("COUNT(*) AS " + name + "_count");
            } else if ("cardinality".equals(type)) {
                selectParts.add("COUNT(DISTINCT " + field + ") AS " + name);
            } else if (aggMap.containsKey(type)) {
                selectParts.add(aggMap.get(type) + "(" + field + ") AS " + name);
            }
        }

        if (selectParts.isEmpty()) {
            selectParts.add("*");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectParts));
        sql.append("\nFROM ").append(parsed.getString("index"));

        String where = buildWhere(parsed.getJSONObject("query"));
        if (!where.isEmpty()) {
            sql.append("\nWHERE ").append(where);
        }

        if (!groupFields.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupFields));
        }

        sql.append(";");
        return sql.toString();
    }

    /**
     * 递归地将查询节点转换为 SQL WHERE 子句。
     * Recursively convert a parsed query node into a SQL WHERE clause string.
     *
     * @param node 解析后的查询节点 / A parsed query node JSONObject
     * @return SQL 条件字符串，match_all 返回空字符串 / SQL condition string
     */
    static String buildWhere(JSONObject node) {
        String type = node.getString("type");

        if ("match_all".equals(type)) {
            return "";
        }

        if ("match".equals(type) || "match_phrase".equals(type)) {
            return node.getString("field") + " LIKE " + quote("%" + node.get("value") + "%");
        }

        if ("multi_match".equals(type)) {
            JSONArray fields = node.getJSONArray("fields");
            List<String> clauses = new ArrayList<>();
            for (int i = 0; i < fields.length(); i++) {
                clauses.add(fields.getString(i) + " LIKE " + quote("%" + node.get("value") + "%"));
            }
            return "(" + String.join(" OR ", clauses) + ")";
        }

        if ("term".equals(type)) {
            return node.getString("field") + " = " + quote(node.get("value"));
        }

        if ("terms".equals(type)) {
            JSONArray values = node.getJSONArray("values");
            List<String> vals = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) {
                vals.add(quote(values.get(i)));
            }
            return node.getString("field") + " IN (" + String.join(", ", vals) + ")";
        }

        if ("range".equals(type)) {
            Map<String, String> ops = new LinkedHashMap<>();
            ops.put("gt", ">");
            ops.put("gte", ">=");
            ops.put("lt", "<");
            ops.put("lte", "<=");

            JSONObject conditions = node.getJSONObject("conditions");
            List<String> parts = new ArrayList<>();
            for (String op : conditions.keySet()) {
                if (ops.containsKey(op)) {
                    parts.add(node.getString("field") + " " + ops.get(op) + " " + quote(conditions.get(op)));
                }
            }
            return String.join(" AND ", parts);
        }

        if ("wildcard".equals(type)) {
            String pattern = node.get("value").toString().replace("*", "%").replace("?", "_");
            return node.getString("field") + " LIKE " + quote(pattern);
        }

        if ("exists".equals(type)) {
            return node.getString("field") + " IS NOT NULL";
        }

        if ("bool".equals(type)) {
            List<String> allClauses = new ArrayList<>();

            List<String> must = filterEmpty(buildWhereList(node.getJSONArray("must")));
            List<String> should = filterEmpty(buildWhereList(node.getJSONArray("should")));
            List<String> mustNot = filterEmpty(buildWhereList(node.getJSONArray("must_not")));
            List<String> filters = filterEmpty(buildWhereList(node.getJSONArray("filter")));

            List<String> andClauses = new ArrayList<>(must);
            andClauses.addAll(filters);
            if (!andClauses.isEmpty()) {
                allClauses.add(String.join(" AND ", andClauses));
            }

            if (!should.isEmpty()) {
                allClauses.add("(" + String.join(" OR ", should) + ")");
            }

            if (!mustNot.isEmpty()) {
                List<String> negated = new ArrayList<>();
                for (String c : mustNot) {
                    negated.add("NOT (" + c + ")");
                }
                allClauses.add(String.join(" AND ", negated));
            }

            return String.join(" AND ", allClauses);
        }

        return "";
    }

    /**
     * 对 JSONArray 中的每个查询节点生成 WHERE 子句。
     * Generate WHERE clauses for each query node in the JSONArray.
     */
    private static List<String> buildWhereList(JSONArray arr) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            result.add(buildWhere(arr.getJSONObject(i)));
        }
        return result;
    }

    /**
     * 过滤掉空字符串。
     * Filter out empty strings from a list.
     */
    private static List<String> filterEmpty(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    /**
     * 将值转换为 SQL 字面量（数字保持原样，字符串加单引号并转义）。
     * Convert a value to a SQL literal.
     *
     * @param value 待引用的值 / The value to quote
     * @return SQL 字面量字符串 / SQL literal string
     */
    static String quote(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Float) {
            return value.toString();
        }
        String s = value.toString().replace("'", "''");
        return "'" + s + "'";
    }

    /**
     * 将 JSONArray 的字符串元素用逗号连接。
     * Join string elements of a JSONArray with commas.
     */
    private static String joinArray(JSONArray arr) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            parts.add(arr.getString(i));
        }
        return String.join(", ", parts);
    }
}
