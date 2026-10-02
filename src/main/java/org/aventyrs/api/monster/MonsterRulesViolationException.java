package org.aventyrs.api.monster;

import java.util.List;
import org.aventyrs.core.monster.MonsterViolation;

/**
 * A monster blueprint that breaks {@code criacao-de-monstros.txt}, as core's {@code
 * MonsterRules#validate} reported it. Mapped to {@code 400} by {@code GlobalExceptionHandler},
 * with one detail line per violation, {@code CODE:subject:actual:limit}.
 */
public class MonsterRulesViolationException extends RuntimeException {

    public static final String MESSAGE = "MONSTER_RULES_VIOLATED";

    private final transient List<MonsterViolation> violations;

    public MonsterRulesViolationException(List<MonsterViolation> violations) {
        super(MESSAGE);
        this.violations = List.copyOf(violations);
    }

    public List<MonsterViolation> getViolations() {
        return violations;
    }

    /** One {@code CODE:subject:actual:limit} line per violation — the subject is empty when there is none. */
    public List<String> details() {
        return violations.stream()
                .map(v -> v.code() + ":" + (v.subject() == null ? "" : v.subject()) + ":" + v.actual() + ":" + v.limit())
                .toList();
    }
}
