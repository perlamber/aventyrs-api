package org.aventyrs.api.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Spring Boot's built-in Liquibase autoconfiguration only wires up a JDBC {@code DataSource},
 * so it can't drive the Mongo extension — this runs the changelog against Mongo directly via
 * Liquibase's programmatic API before the application starts serving requests. Resolves the
 * connection through {@link MongoConnectionDetails} rather than the raw {@code
 * spring.data.mongodb.uri} property, so it also picks up connections supplied via {@code
 * @ServiceConnection} (e.g. Testcontainers in tests). Active Spring profiles are passed through
 * as Liquibase contexts, so changesets tagged {@code context: dev} (e.g. fake seed data) only
 * apply when the {@code dev} profile is active — a production run stays clean.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoLiquibaseRunner implements CommandLineRunner {

    private static final String CHANGELOG_PATH = "db/changelog/db.changelog-master.yaml";

    private final MongoConnectionDetails connectionDetails;
    private final Environment environment;

    public MongoLiquibaseRunner(MongoConnectionDetails connectionDetails, Environment environment) {
        this.connectionDetails = connectionDetails;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        String mongoUri = connectionDetails.getConnectionString().getConnectionString();
        Database database = DatabaseFactory.getInstance()
                .openDatabase(mongoUri, null, null, null, new ClassLoaderResourceAccessor());
        try (Liquibase liquibase = new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database)) {
            liquibase.update(new Contexts(environment.getActiveProfiles()), new LabelExpression());
        }
    }
}
