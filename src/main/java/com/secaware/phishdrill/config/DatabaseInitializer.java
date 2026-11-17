package com.secaware.phishdrill.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库初始化：监听 DataSource bean 创建事件，在任何仓储使用前执行建表与种子脚本。
 * schema.sql 全部使用 CREATE TABLE IF NOT EXISTS / MERGE，重复启动幂等。
 *
 * 关键点：
 * 1. BeanCreatedEventListener 在数据源就绪后立即触发，早于 ServerStartupEvent，
 *    因此 DataSeeder 等启动监听器执行时表结构必然已存在；
 * 2. 注入的 DataSource 是 Micronaut 的 ContextualAwareDataSource 代理，要求活动连接上下文，
 *    这里 unwrap 出底层 HikariDataSource，用真实连接执行 DDL/DML，
 *    并在同一事务里显式 commit，所建表对后续仓储的事务连接完全可见。
 */
@Singleton
public class DatabaseInitializer implements BeanCreatedEventListener<DataSource> {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final String schemaLocation;
    private final String seedLocation;
    private volatile boolean initialized = false;

    public DatabaseInitializer(
            @Value("${app.db.schema-location:classpath:db/schema.sql}") String schemaLocation,
            @Value("${app.db.seed-location:classpath:db/seed.sql}") String seedLocation) {
        this.schemaLocation = schemaLocation;
        this.seedLocation = seedLocation;
    }

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.getBean();
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    DataSource raw = unwrapDataSource(dataSource);
                    runScript(raw, schemaLocation);
                    runScript(raw, seedLocation);
                    verifyTables(raw);
                    initialized = true;
                }
            }
        }
        return dataSource;
    }

    private static DataSource unwrapDataSource(DataSource dataSource) {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
            return dataSource;
        } catch (SQLException e) {
            throw new IllegalStateException("无法获取底层数据源以执行初始化脚本", e);
        }
    }

    private void runScript(DataSource raw, String location) {
        String sql = readClasspath(location.replace("classpath:", ""));
        try (Connection connection = raw.getConnection();
             Statement statement = connection.createStatement()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (String stmt : splitStatements(sql)) {
                    if (!stmt.isBlank()) {
                        statement.execute(stmt);
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
            LOG.info("已执行数据库脚本: {}", location);
        } catch (SQLException e) {
            throw new IllegalStateException("执行数据库脚本失败: " + location, e);
        }
    }

    private void verifyTables(DataSource raw) {
        try (Connection connection = raw.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema='PUBLIC' ORDER BY table_name")) {
            StringBuilder tables = new StringBuilder();
            while (rs.next()) {
                tables.append(rs.getString(1)).append(' ');
            }
            LOG.info("数据库初始化完成，当前库表: {}", tables);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化后校验表清单失败", e);
        }
    }

    /** 粗略按分号拆分 SQL，忽略字符串字面量内的分号。 */
    private static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
            }
            if (ch == ';' && !inSingleQuote) {
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().trim().isBlank()) {
            out.add(current.toString().trim());
        }
        return out;
    }

    private static String readClasspath(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath 资源缺失: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath 资源失败: " + path, e);
        }
    }
}
