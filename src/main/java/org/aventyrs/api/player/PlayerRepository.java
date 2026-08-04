package org.aventyrs.api.player;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlayerRepository extends MongoRepository<PlayerDocument, String> {
}
