package org.aventyrs.api.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Freezes a combatant's sheet as it stood when an analytics event happened, so the warehouse can tell which build
 * dealt a hit after the sheet itself has moved on — {@code characterSheets}/{@code monsterSheets} are overwritten in
 * place.
 *
 * <p>Snapshots are content-addressed: the id is a SHA-256 of the sheet with its {@link #VOLATILE_FIELDS} left out,
 * and a snapshot is only written the first time that id is seen. Those fields are the resource pools every
 * {@code /status} frame rewrites; hashing them in would mint a new snapshot per hit for a build that never changed.
 * What's stored is still the <em>whole</em> raw sheet, pools included, as it stood at that first sighting — the
 * pools at any later moment are on the {@code STATUS} events instead.
 */
@Service
public class SheetSnapshotService {

    static final String COLLECTION = "analytics_sheet_snapshots";
    static final String CHARACTER_SHEETS = "characterSheets";
    static final String MONSTER_SHEETS = "monsterSheets";

    /** Top-level sheet fields that churn during play without the build changing. */
    static final Set<String> VOLATILE_FIELDS = Set.of(
            "hitPointsSpent", "magicPointsSpent", "determinationPointsSpent", "shieldPoints",
            "temporaryEgoPoints", "temporaryBonuses", "bleedingEffects", "manaDrains", "witheringEffects",
            "pendingEgoRecoveries", "lifeSteals", "hourlyEgoRecoveries", "exhausted", "lockedHitPoints",
            "lifeStealLockedHitPoints", "restScopedUses", "egoLedger", "restLockedHitPoints", "subordinates");

    /** The same, inside the embedded {@code character}: its damage tier follows the PV every {@code /status} sets. */
    static final Set<String> VOLATILE_CHARACTER_FIELDS = Set.of("status");

    private final MongoTemplate mongoTemplate;

    public SheetSnapshotService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Snapshots the sheet behind a participant id — which names a character sheet or a monster sheet with no
     * discriminator, so both collections are tried — and returns the snapshot's id, or {@code null} when neither
     * holds it.
     */
    public String snapshot(String characterSheetId) {
        if (characterSheetId == null) {
            return null;
        }
        Query byId = Query.query(Criteria.where("_id").is(characterSheetId));
        String kind = "CHARACTER";
        Document sheet = mongoTemplate.findOne(byId, Document.class, CHARACTER_SHEETS);
        if (sheet == null) {
            kind = "MONSTER";
            sheet = mongoTemplate.findOne(byId, Document.class, MONSTER_SHEETS);
        }
        if (sheet == null) {
            return null;
        }

        String id = hashOf(kind, sheet);
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(id)),
                new Update()
                        .setOnInsert("characterSheetId", characterSheetId)
                        .setOnInsert("kind", kind)
                        .setOnInsert("firstSeenAt", Instant.now())
                        .setOnInsert("sheet", sheet),
                COLLECTION);
        return id;
    }

    static String hashOf(String kind, Document sheet) {
        Document stable = new Document(sheet);
        VOLATILE_FIELDS.forEach(stable::remove);
        if (stable.get("character") instanceof Document character) {
            Document stableCharacter = new Document(character);
            VOLATILE_CHARACTER_FIELDS.forEach(stableCharacter::remove);
            stable.put("character", stableCharacter);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(kind.getBytes(StandardCharsets.UTF_8));
            digest.update(stable.toJson().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
