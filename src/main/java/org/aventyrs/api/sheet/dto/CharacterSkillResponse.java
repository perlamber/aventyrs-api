package org.aventyrs.api.sheet.dto;

import java.util.List;

public record CharacterSkillResponse(List<String> specializations, int graduationValue) {
}
