"""Test cases for ES Query to SQL Converter."""

import unittest
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from converter import parse_es_query, build_sql


class TestBoolQuery(unittest.TestCase):
    """Test bool query with must, should, must_not, filter."""

    def test_complex_bool(self):
        query = '''
GET /my_index/_search
{
  "from": 0,
  "size": 10,
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "elasticsearch" } },
        { "range": { "created_at": { "gte": "2024-01-01", "lte": "2024-12-31" } } }
      ],
      "must_not": [
        { "term": { "status": "deleted" } }
      ],
      "should": [
        { "match": { "tags": "search" } }
      ],
      "filter": [
        { "exists": { "field": "author" } }
      ]
    }
  },
  "sort": [
    { "created_at": "desc" },
    { "_score": "desc" }
  ],
  "_source": ["title", "author", "created_at", "tags"]
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("SELECT title, author, created_at, tags", sql)
        self.assertIn("FROM my_index", sql)
        self.assertIn("title LIKE '%elasticsearch%'", sql)
        self.assertIn("created_at >= '2024-01-01'", sql)
        self.assertIn("created_at <= '2024-12-31'", sql)
        self.assertIn("author IS NOT NULL", sql)
        self.assertIn("tags LIKE '%search%'", sql)
        self.assertIn("NOT (status = 'deleted')", sql)
        self.assertIn("ORDER BY created_at DESC, _score DESC", sql)
        self.assertIn("LIMIT 10", sql)


class TestAggregation(unittest.TestCase):
    """Test aggregation queries."""

    def test_terms_and_avg(self):
        query = '''
GET /sales/_search
{
  "size": 0,
  "query": {
    "range": { "date": { "gte": "2024-01-01" } }
  },
  "aggs": {
    "by_category": {
      "terms": { "field": "category" }
    },
    "avg_price": {
      "avg": { "field": "price" }
    }
  }
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("category", sql)
        self.assertIn("COUNT(*) AS by_category_count", sql)
        self.assertIn("AVG(price) AS avg_price", sql)
        self.assertIn("GROUP BY category", sql)
        self.assertIn("date >= '2024-01-01'", sql)


class TestWildcardAndTerms(unittest.TestCase):
    """Test wildcard and terms queries."""

    def test_wildcard_and_terms(self):
        query = '''
GET /users/_search
{
  "query": {
    "bool": {
      "must": [
        {"wildcard": {"name": "john*"}},
        {"terms": {"role": ["admin", "editor"]}}
      ]
    }
  },
  "size": 5
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("FROM users", sql)
        self.assertIn("name LIKE 'john%'", sql)
        self.assertIn("role IN ('admin', 'editor')", sql)
        self.assertIn("LIMIT 5", sql)


class TestMatchAll(unittest.TestCase):
    """Test match_all / no-query scenarios."""

    def test_no_query_with_sort(self):
        query = '''
GET /logs/_search
{
  "size": 100,
  "sort": [{"timestamp": "desc"}]
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertEqual(
            sql,
            "SELECT *\nFROM logs\nORDER BY timestamp DESC\nLIMIT 100;",
        )


class TestMultiMatch(unittest.TestCase):
    """Test multi_match query."""

    def test_multi_match_no_index(self):
        query = '''
{
  "query": {
    "multi_match": {
      "query": "database",
      "fields": ["title", "content", "summary"]
    }
  }
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("FROM unknown_table", sql)
        self.assertIn("title LIKE '%database%'", sql)
        self.assertIn("content LIKE '%database%'", sql)
        self.assertIn("summary LIKE '%database%'", sql)
        self.assertIn(" OR ", sql)


class TestMatchPhrase(unittest.TestCase):
    """Test match_phrase query."""

    def test_match_phrase(self):
        query = '''
GET /articles/_search
{
  "query": {
    "match_phrase": { "body": "quick brown fox" }
  }
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("body LIKE '%quick brown fox%'", sql)


class TestExistsOnly(unittest.TestCase):
    """Test standalone exists query."""

    def test_exists(self):
        query = '''
GET /data/_search
{
  "query": {
    "exists": { "field": "email" }
  }
}
'''
        sql = build_sql(parse_es_query(query))
        self.assertIn("email IS NOT NULL", sql)


if __name__ == "__main__":
    unittest.main()
