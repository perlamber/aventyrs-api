package org.aventyrs.api.skill;

import java.util.Arrays;
import java.util.List;
import org.aventyrs.core.skill.SkillType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillController {

    @GetMapping("/api/skills")
    public List<String> listSkills() {
        return Arrays.stream(SkillType.values()).map(Enum::name).toList();
    }
}
