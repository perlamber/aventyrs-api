package org.aventyrs.api.scene.dto;

/**
 * One temporary bonus a Título activation grants, as it travels between clients.
 *
 * <p>{@code modifierType} and {@code scope} are <b>Strings, not core enums</b>, and that is
 * load-bearing rather than lazy. This API compiles against an older aventyrs-core than the clients
 * do, and the rules engine runs only client-side (see {@link org.aventyrs.api.scene.SceneService}):
 * a client on a newer core can legitimately report a {@code ModifierType} constant this server's
 * own enum has never heard of — {@code RETALIATION_DAMAGE} is exactly such a case — and binding
 * that to an enum here would reject the whole frame instead of relaying it. As text it passes
 * through untouched and every client resolves it against its own catalog, skipping what it cannot
 * name.
 *
 * @param modifierType the core {@code ModifierType} constant name
 * @param value        the bonus amount
 * @param rounds       its Duração in Rodadas
 * @param scope        the core {@code TargetScope} constant name the granting clause declared
 * @param source       what granted it, for the recipient's own non-cumulative bookkeeping
 */
public record BlessingDto(String modifierType, int value, int rounds, String scope, String source) {
}
