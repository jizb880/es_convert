"""ES Query to SQL Converter - Entry Point."""

import configparser
import os
import sys

from converter import parse_es_query, build_sql


def main():
    """程序主入口：读取配置、加载 ES 查询、转换为 SQL 并输出。
    Main entry point: read config, load ES query file, convert to SQL,
    print to screen and save to result file.
    """
    config = configparser.ConfigParser()
    config.read("config.ini")

    query_file = config.get("input", "query_file", fallback="query.json")
    result_dir = config.get("output", "result_dir", fallback="./result")

    if not os.path.isfile(query_file):
        print(f"Error: query file '{query_file}' not found.")
        sys.exit(1)

    with open(query_file, "r", encoding="utf-8") as f:
        raw_text = f.read()

    parsed = parse_es_query(raw_text)
    sql = build_sql(parsed)

    # Print to screen
    print("=" * 50)
    print("Generated SQL:")
    print("=" * 50)
    print(sql)
    print("=" * 50)

    # Write to result file
    os.makedirs(result_dir, exist_ok=True)
    result_path = os.path.join(result_dir, "result.txt")
    with open(result_path, "w", encoding="utf-8") as f:
        f.write(sql + "\n")

    print(f"\nResult saved to: {result_path}")


if __name__ == "__main__":
    main()
