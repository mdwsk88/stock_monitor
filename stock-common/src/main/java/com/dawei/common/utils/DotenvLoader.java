package com.dawei.common.utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads environment variables from a root .env file and keeps legacy paths as a fallback.
 */
public final class DotenvLoader {

    private static final String ENV_FILE = ".env";
    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    private static final List<Path> SEARCH_DIRECTORIES = List.of(
            Path.of("."),
            Path.of("stock-web", "src", "main", "resources"),
            Path.of("us-stock-mcp", "src", "main", "resources"),
            Path.of("a-stock-mcp", "src", "main", "resources")
    );

    private DotenvLoader() {
    }

    public static void loadToSystemProperties() {
        for (Path directory : SEARCH_DIRECTORIES) {
            if (!Files.isRegularFile(directory.resolve(ENV_FILE))) {
                continue;
            }

            Dotenv dotenv = Dotenv.configure()
                    .directory(directory.toAbsolutePath().normalize().toString())
                    .filename(ENV_FILE)
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();

            dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

            if (Path.of(".").equals(directory)) {
                log.info("已从项目根目录加载 .env 配置");
            } else {
                log.warn("已从旧路径 {} 加载 .env，请迁移到项目根目录 .env", directory.resolve(ENV_FILE));
            }
            return;
        }

        log.info("未找到 .env 文件，继续使用系统环境变量");
    }
}
