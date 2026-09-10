package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.scene.Direction;
import org.aventyrs.core.scene.TerrainType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted state backing an {@code org.aventyrs.core.scene.Scene}. Participants reference a
 * CharacterSheet by id rather than embedding it, same reasoning as {@code
 * CharacterSheetDocument}'s {@code playerId}: the referenced graph is expensive and mostly
 * static, so clients that already loaded it via REST shouldn't need it re-embedded here.
 * {@code currentRound}/{@code currentIndex} mirror {@code Scene}'s own turn cursor. {@code
 * SceneService#advanceTurn} replays {@code Scene#next()}'s active/pending-entry merge on a wrap,
 * but — mirroring the Rodada gate core added in 0.0.32 — {@code currentRound} only advances while
 * {@code combatScene} is true; before combat a wrap just cycles the cursor and the Round stays 0. {@code createdAt} exists purely to resolve "latest" (see
 * {@code SceneRepository#findTopByOrderByCreatedAtDesc}) — ids are random UUIDs, not ObjectIds,
 * so there's no implicit chronological ordering to fall back on. {@code combatScene} mirrors
 * core's own {@code Scene#isCombatScene()} — {@code false} until combat breaks out, flipped by
 * {@code SceneService#startCombat} (core 0.0.32's {@code Scene#startCombat()}); the full {@code
 * PUT} also sets it straight through, for a scene rebuilt from persistence already mid-combat
 * (core's {@code setCombatScene}). {@code imageUrl} is null until a caller sets one via update;
 * the image itself is uploaded separately through {@code /api/images}, so this only ever stores
 * the URL that upload handed back. {@code width}/{@code height} size the playable grid within
 * {@code GridPosition}'s fixed {@value org.aventyrs.core.scene.grid.GridPosition#GRID_SIZE}x{@value
 * org.aventyrs.core.scene.grid.GridPosition#GRID_SIZE} ceiling. They're set at creation, sized to
 * the background map, and thereafter changed only through {@code SceneService#resizeGrid} — the
 * GM's live grid control, which refuses a shrink that would strand a participant outside the new
 * bounds rather than moving anyone's token for them. {@code PUT /scenes/{id}} deliberately leaves
 * both alone: it's a full replace driven by the scene editor, and a resize is not part of the
 * shape that editor edits. {@code actionHistory} mirrors core's {@code Scene#getActionHistory()}
 * — appended to, never cleared, by {@code SceneService#recordAction} — and is {@code null} on any
 * document persisted before it existed, which {@code SceneService} normalizes to an empty list at
 * read time; same "no changeset needed" reasoning as {@code SceneParticipantEntry#joinedAtRound}.
 * {@code itemStoreMaxRarity} mirrors core's own {@code Scene#getItemStore()} — {@code null} on a
 * Scene with nowhere to shop, same lifecycle as {@code combatScene}, set once a caller attaches a
 * store and cleared back to {@code null} once the party leaves it. Only the ceiling Raridade is
 * kept, matching {@code ItemStore} itself: it carries no stock of its own, just the whole {@code
 * ItemCatalog} up to that ceiling, so a client rebuilds a real {@code ItemStore} from this one
 * value the same way it rebuilds a {@code Scene} from {@code participants}.
 */
@Document(collection = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SceneDocument {

    @Id
    private String id;

    private String name;

    private TerrainType terrain;

    private List<SceneParticipantEntry> participants;

    private int currentRound;

    /** -1 before the first {@code next()}-equivalent action, same convention as core's {@code Scene}. */
    private int currentIndex;

    private boolean combatScene;

    /**
     * Whether this is the scene the table is currently playing. This API's own notion, with no core
     * counterpart. A freshly persisted scene is never active ({@link SceneService#create} leaves it
     * {@code false}); {@link SceneActivationService#activate} is the sole writer, flipping it on for
     * one scene and off for every other in the same call. {@link SceneService#getAvailable} trusts
     * that "at most one" invariant: one active scene wins, and anything else (none, or a stale pair
     * left by a race) falls back to the latest created. {@code null}/absent on any document
     * persisted before this field existed, which Mongo maps to {@code false}; the 013 changeset
     * backfills it so {@code $ne: true} style queries stay predictable.
     */
    private boolean active;

    /**
     * This scene's place in the scene-to-scene map graph: up to four neighbours, one per {@link
     * Direction}, each held only as the neighbour scene's id (a UUID string, same shape as {@link
     * #id}). Mirrors core's {@code Scene#getConnections()} — map topology only, no distance and no
     * pathfinding, and no lifecycle method touches it.
     *
     * <p>Every link here is two-sided: if this scene points {@code NORTH} at scene B, B points
     * {@code SOUTH} back at this one. Core's own {@code setConnection}/{@code removeConnection} are
     * single-sided by design; {@link SceneConnectionService} is what keeps both ends (and any third
     * scene a re-point displaces) consistent, in one transaction.
     *
     * <p>{@code null} on any document persisted before this field existed, normalised to an empty
     * map at read time the same way {@code actionHistory} is — nothing queries on it, so there's no
     * changeset.
     */
    private Map<Direction, String> connections;

    private ItemRarity itemStoreMaxRarity;

    private String imageUrl;

    private int width;

    private int height;

    private Instant createdAt;

    private List<SceneActionEntry> actionHistory;

    /**
     * Rolls the Narrador has asked the table for, and the answers to them — two flat lists rather
     * than responses nested inside their request, so both append with a plain {@code $push} and no
     * positional operator. That matters: several players answering at once is the ordinary case,
     * and this collection has no {@code @Version}, so a read-modify-write save would lose answers.
     * See {@code SceneService#respondToRoll}.
     *
     * <p>{@code null} on any document persisted before they existed, normalised at read time the
     * same way {@code actionHistory} is.
     */
    private List<SceneRollRequestEntry> rollRequests;

    private List<SceneRollResponseEntry> rollResponses;
}
