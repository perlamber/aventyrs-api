package org.aventyrs.api.player;

/**
 * What a {@link PlayerDocument} is allowed to do in the client.
 *
 * <p><b>This is not an authorization boundary.</b> This API has no authentication at all — a
 * client identifies itself by looking a login up through {@code GET /api/players/by-login/{login}}
 * with no credential of any kind — so nothing here can be enforced server-side, and no endpoint
 * checks it. It exists so the client knows which screen to open after login: a {@link #GM} lands
 * on the scene/monster tooling, a {@link #PLAYER} on their character roster.
 *
 * <p>When real authentication arrives, this is the field the enforcement would hang off; until
 * then, treat it as a preference, not a permission.
 */
public enum PlayerRole {

    /** Plays characters. The default for every player, and what a missing value backfills to. */
    PLAYER,

    /** Runs the table: authors Scenes and monster stat blocks. */
    GM
}
