package org.aventyrs.api.scene;

import org.aventyrs.api.scene.dto.SceneResponse;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * The sole writer of {@code SceneDocument#active}: makes one scene the table's active scene and, in
 * the same call, clears the flag on every other scene.
 *
 * <p>It lives apart from {@link SceneService} on purpose. Every mutation there is scoped to a single
 * scene document; activation is a collection-wide switch whose whole point is the invariant "at most
 * one scene is active," which {@link SceneService#getAvailable} then leans on. {@link
 * SceneService#create} never touches {@code active} — a freshly persisted scene is inert until a
 * caller activates it here.
 */
@Service
public class SceneActivationService {

    private final SceneService sceneService;
    private final MongoTemplate mongoTemplate;

    public SceneActivationService(SceneService sceneService, MongoTemplate mongoTemplate) {
        this.sceneService = sceneService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Marks the scene {@code id} active and clears {@code active} on all others.
     *
     * <p>The clear runs as one {@code updateMulti} rather than a load-mutate-save per scene: no
     * {@code SceneDocument} carries a {@code @Version}, and this is a multi-document write, so a
     * read-modify-write loop would be both racy and slow. Two activations racing can still briefly
     * leave two scenes flagged — the loser sets its target back on after the winner cleared it — but
     * {@link SceneService#getAvailable} already treats "more than one active" as "fall back to the
     * latest created," so the window degrades gracefully rather than serving the wrong scene.
     *
     * @throws org.aventyrs.api.common.NotFoundException if no scene has this id
     */
    public SceneResponse activate(String id) {
        sceneService.get(id); // 404s before we touch anything if the id is unknown

        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").ne(id).and("active").is(true)),
                new Update().set("active", false),
                SceneDocument.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(id)),
                new Update().set("active", true),
                SceneDocument.class);

        return sceneService.get(id);
    }
}
