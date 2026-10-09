package org.aventyrs.api.player;

/**
 * What a {@link PlayerDocument} is allowed to do.
 *
 * <p>Carried as the {@code role} claim of the JWT issued at login and enforced server-side:
 * {@code SecurityConfig} reserves the table-running REST endpoints for {@link #GM}, and {@code
 * StompAuthChannelInterceptor} does the same for the GM-only realtime destinations. The client
 * also uses it to pick the screen to open after login — a GM lands on the scene/monster tooling,
 * a {@link #PLAYER} on their character roster.
 */
public enum PlayerRole {

    /** Plays characters. The default for every player, and what a missing value backfills to. */
    PLAYER,

    /** Runs the table: authors Scenes and monster stat blocks. */
    GM
}
