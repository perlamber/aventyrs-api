package org.aventyrs.api.scene;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SceneRepository extends MongoRepository<SceneDocument, String> {

    Optional<SceneDocument> findTopByOrderByCreatedAtDesc();

    /**
     * Every scene currently flagged {@code active}. {@link SceneActivationService} keeps this to at
     * most one; {@link SceneService#getAvailable} treats a longer list (a race between two
     * activations) the same as an empty one and falls back to the latest created.
     */
    List<SceneDocument> findByActiveTrue();
}
