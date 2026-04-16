package esconvert;

import org.json.JSONObject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES Query to SQL 转换器的单元测试（与 Python 版本测试用例一一对应）。
 * Unit tests for ES Query to SQL Converter (mirrors all Python test cases).
 */
class ConverterTest {

    /** 测试复杂 bool 查询（must/should/must_not/filter）。 */
    @Test
    void testComplexBool() {
        String query =
                "GET /my_index/_search\n" +
                "{\n" +
                "  \"from\": 0,\n" +
                "  \"size\": 10,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        { \"match\": { \"title\": \"elasticsearch\" } },\n" +
                "        { \"range\": { \"created_at\": { \"gte\": \"2024-01-01\", \"lte\": \"2024-12-31\" } } }\n" +
                "      ],\n" +
                "      \"must_not\": [\n" +
                "        { \"term\": { \"status\": \"deleted\" } }\n" +
                "      ],\n" +
                "      \"should\": [\n" +
                "        { \"match\": { \"tags\": \"search\" } }\n" +
                "      ],\n" +
                "      \"filter\": [\n" +
                "        { \"exists\": { \"field\": \"author\" } }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"sort\": [\n" +
                "    { \"created_at\": \"desc\" },\n" +
                "    { \"_score\": \"desc\" }\n" +
                "  ],\n" +
                "  \"_source\": [\"title\", \"author\", \"created_at\", \"tags\"]\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("SELECT title, author, created_at, tags"));
        assertTrue(sql.contains("FROM my_index"));
        assertTrue(sql.contains("title LIKE '%elasticsearch%'"));
        assertTrue(sql.contains("created_at >= '2024-01-01'"));
        assertTrue(sql.contains("created_at <= '2024-12-31'"));
        assertTrue(sql.contains("author IS NOT NULL"));
        assertTrue(sql.contains("tags LIKE '%search%'"));
        assertTrue(sql.contains("NOT (status = 'deleted')"));
        assertTrue(sql.contains("ORDER BY created_at DESC, _score DESC"));
        assertTrue(sql.contains("LIMIT 10"));
    }

    /** 测试 terms + avg 聚合查询。 */
    @Test
    void testAggregation() {
        String query =
                "GET /sales/_search\n" +
                "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"range\": { \"date\": { \"gte\": \"2024-01-01\" } }\n" +
                "  },\n" +
                "  \"aggs\": {\n" +
                "    \"by_category\": {\n" +
                "      \"terms\": { \"field\": \"category\" }\n" +
                "    },\n" +
                "    \"avg_price\": {\n" +
                "      \"avg\": { \"field\": \"price\" }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("category"));
        assertTrue(sql.contains("COUNT(*) AS by_category_count"));
        assertTrue(sql.contains("AVG(price) AS avg_price"));
        assertTrue(sql.contains("GROUP BY category"));
        assertTrue(sql.contains("date >= '2024-01-01'"));
    }

    /** 测试 wildcard + terms 组合查询。 */
    @Test
    void testWildcardAndTerms() {
        String query =
                "GET /users/_search\n" +
                "{\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\"wildcard\": {\"name\": \"john*\"}},\n" +
                "        {\"terms\": {\"role\": [\"admin\", \"editor\"]}}\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"size\": 5\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("FROM users"));
        assertTrue(sql.contains("name LIKE 'john%'"));
        assertTrue(sql.contains("role IN ('admin', 'editor')"));
        assertTrue(sql.contains("LIMIT 5"));
    }

    /** 测试 match_all（无 query 体 + sort）。 */
    @Test
    void testMatchAll() {
        String query =
                "GET /logs/_search\n" +
                "{\n" +
                "  \"size\": 100,\n" +
                "  \"sort\": [{\"timestamp\": \"desc\"}]\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertEquals("SELECT *\nFROM logs\nORDER BY timestamp DESC\nLIMIT 100;", sql);
    }

    /** 测试 multi_match 多字段匹配（无 index）。 */
    @Test
    void testMultiMatch() {
        String query =
                "{\n" +
                "  \"query\": {\n" +
                "    \"multi_match\": {\n" +
                "      \"query\": \"database\",\n" +
                "      \"fields\": [\"title\", \"content\", \"summary\"]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("FROM unknown_table"));
        assertTrue(sql.contains("title LIKE '%database%'"));
        assertTrue(sql.contains("content LIKE '%database%'"));
        assertTrue(sql.contains("summary LIKE '%database%'"));
        assertTrue(sql.contains(" OR "));
    }

    /** 测试 match_phrase 短语匹配。 */
    @Test
    void testMatchPhrase() {
        String query =
                "GET /articles/_search\n" +
                "{\n" +
                "  \"query\": {\n" +
                "    \"match_phrase\": { \"body\": \"quick brown fox\" }\n" +
                "  }\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("body LIKE '%quick brown fox%'"));
    }

    /** 测试 exists 字段存在性检查。 */
    @Test
    void testExists() {
        String query =
                "GET /data/_search\n" +
                "{\n" +
                "  \"query\": {\n" +
                "    \"exists\": { \"field\": \"email\" }\n" +
                "  }\n" +
                "}";
        String sql = SqlBuilder.build(EsParser.parse(query));
        assertTrue(sql.contains("email IS NOT NULL"));
    }
}
