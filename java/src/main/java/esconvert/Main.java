package esconvert;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/**
 * 程序主入口：读取配置、加载 ES 查询、转换为 SQL 并输出。
 * Main entry point: read config, load ES query file, convert to SQL,
 * print to screen and save to result file.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Load config
        Properties config = new Properties();
        Path configPath = Paths.get("config.properties");
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                config.load(is);
            }
        }

        String queryFile = config.getProperty("query_file", "../query.json");
        String resultDir = config.getProperty("result_dir", "./result");

        // Read query file
        Path queryPath = Paths.get(queryFile);
        if (!Files.exists(queryPath)) {
            System.err.println("Error: query file '" + queryFile + "' not found.");
            System.exit(1);
        }

        String rawText = new String(Files.readAllBytes(queryPath), StandardCharsets.UTF_8);

        // Convert
        JSONObject parsed = EsParser.parse(rawText);
        String sql = SqlBuilder.build(parsed);

        // Print to screen
        String sep = "==================================================";
        System.out.println(sep);
        System.out.println("Generated SQL:");
        System.out.println(sep);
        System.out.println(sql);
        System.out.println(sep);

        // Write to result file
        Path resultPath = Paths.get(resultDir);
        Files.createDirectories(resultPath);
        Path outputFile = resultPath.resolve("result.txt");
        Files.write(outputFile, (sql + "\n").getBytes(StandardCharsets.UTF_8));

        System.out.println("\nResult saved to: " + outputFile);
    }
}
