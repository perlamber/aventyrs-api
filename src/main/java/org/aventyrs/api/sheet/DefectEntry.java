package org.aventyrs.api.sheet;

import java.util.List;

/**
 * Persisted mirror of core's {@code HeldDefect} — one Defeito a character holds. {@code type} is the {@code
 * Defect#name()} ({@code "CORPO_FRAGIL"}), {@code severity} the {@code DefectSeverity}, {@code superacao} the
 * {@code SuperacaoBenefit} or {@code null}. {@code choices} and {@code superacaoPicks} are the picks as strings —
 * an enum constant's name, or a Fobia's free text — resolved back by the client against the type each choice
 * declares, the same convention {@code BackgroundEntry#benefitChoices} uses. {@code overcome} is a Defeito
 * superado: kept on record, its effects ended.
 */
public record DefectEntry(String type, String severity, List<String> choices, boolean fromCreation,
                          String superacao, List<String> superacaoPicks, boolean overcome) {
}
