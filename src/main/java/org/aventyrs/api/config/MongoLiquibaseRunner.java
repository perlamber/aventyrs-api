package org.aventyrs.api.config;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring Boot's built-in Liquibase autoconfiguration only wires up a JDBC {@code DataSource},
 * so it can't drive the Mongo extension — this runs the changelog against Mongo directly via
 * Liquibase's programmatic API before the application starts serving requests. Resolves the
 * connection through {@link MongoConnectionDetails} rather than the raw {@code
 * spring.data.mongodb.uri} property, so it also picks up connections supplied via {@code
 * @ServiceConnection} (e.g. Testcontainers in tests).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoLiquibaseRunner implements CommandLineRunner {

    private static final String CHANGELOG_PATH = "db/changelog/db.changelog-master.yaml";

    private final MongoConnectionDetails connectionDetails;

    public MongoLiquibaseRunner(MongoConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
    }

    @Override
    public void run(String... args) throws Exception {
        String mongoUri = connectionDetails.getConnectionString().getConnectionString();
        Database database = DatabaseFactory.getInstance()
                .openDatabase(mongoUri, null, null, null, new ClassLoaderResourceAccessor());
        try (Liquibase liquibase = new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database)) {
            liquibase.update();
        }
    }
}
