package org.aventyrs.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Spring Boot's MongoDB autoconfiguration stops at a {@code MongoTemplate} — it never registers a
 * {@link MongoTransactionManager}, so {@code @Transactional} is a no-op until one exists. This adds
 * it, which is what lets {@link org.aventyrs.api.scene.SceneConnectionService} write both sides of a
 * scene link (and any third scene it displaces) as one all-or-nothing unit.
 *
 * <p>Multi-document transactions need the server to be a replica set. Production Mongo already is;
 * the Testcontainers {@code MongoDBContainer} the integration tests use starts a single-node replica
 * set for exactly this reason, so the transactional paths are exercised end to end.
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
