package com.evmonitor.testutil;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ein Postgres-Container pro Test-JVM statt einer pro Testklasse.
 *
 * <p>Jede Testklasse bekommt trotzdem eine eigene, frische Datenbank, die Isolation bleibt also
 * wie bei einem eigenen Container. Für Klassen mit Flyway wird die Datenbank aus einem einmal
 * migrierten Template geklont ({@code CREATE DATABASE ... TEMPLATE}), das spart den
 * Container-Start und den kompletten Migrationslauf pro Klasse.
 */
public final class SharedPostgres {

    private static final String TEMPLATE_DB = "evm_migrated_template";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static PostgreSQLContainer<?> container;
    private static boolean templateMigrated;

    private SharedPostgres() {
    }

    /** Frische Datenbank mit vollständig migriertem Schema (Flyway läuft im Context nur noch als No-op). */
    public static void registerMigrated(DynamicPropertyRegistry registry) {
        register(registry, createDatabase(true));
    }

    /** Frische, leere Datenbank, z. B. für Tests mit Hibernate {@code create-drop}. */
    public static void registerEmpty(DynamicPropertyRegistry registry) {
        register(registry, createDatabase(false));
    }

    private static void register(DynamicPropertyRegistry registry, String jdbcUrl) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    private static synchronized String createDatabase(boolean migrated) {
        ensureStarted();
        if (migrated) {
            ensureTemplateMigrated();
        }
        String name = "evm_test_" + COUNTER.incrementAndGet();
        exec(adminUrl(), "CREATE DATABASE " + name + (migrated ? " TEMPLATE " + TEMPLATE_DB : ""));
        return urlFor(name);
    }

    private static void ensureStarted() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:15-alpine");
            container.start();
        }
    }

    private static void ensureTemplateMigrated() {
        if (templateMigrated) {
            return;
        }
        exec(adminUrl(), "CREATE DATABASE " + TEMPLATE_DB);
        // Gleiche Flyway-Einstellungen wie in application.yml
        Flyway.configure()
                .dataSource(urlFor(TEMPLATE_DB), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        templateMigrated = true;
    }

    private static String adminUrl() {
        return container.getJdbcUrl();
    }

    private static String urlFor(String database) {
        return container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database);
    }

    private static void exec(String url, String sql) {
        try (Connection c = DriverManager.getConnection(url, container.getUsername(), container.getPassword());
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("SharedPostgres: " + sql, e);
        }
    }
}
