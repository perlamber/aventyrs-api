package org.aventyrs.api.player;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlayerRepository extends MongoRepository<PlayerDocument, String> {

    Optional<PlayerDocument> findByLogin(String login);
}
