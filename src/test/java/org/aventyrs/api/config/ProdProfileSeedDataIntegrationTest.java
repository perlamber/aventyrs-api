package org.aventyrs.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Booting under the {@code prod} profile applies the schema changesets but none of the
 * {@code context: dev} seed data — no fake players, character sheets, or scene.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "aventyrs.security.jwt.secret=prod-profile-test-signing-key-0123456789")
@ActiveProfiles("prod")
class ProdProfileSeedDataIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void prodProfileSkipsDevSeedData() {
        assertThat(mongoTemplate.collectionExists("players")).isTrue();
        assertThat(mongoTemplate.getCollection("players").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("characterSheets").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("scenes").countDocuments()).isZero();
    }
}
