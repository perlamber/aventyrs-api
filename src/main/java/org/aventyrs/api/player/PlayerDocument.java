package org.aventyrs.api.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDocument {

    @Id
    private String id;

    private String name;

    private String login;

    /**
     * Which client experience this player gets. Null on documents written before the field
     * existed and on any request that omits it; both are read as {@link PlayerRole#PLAYER} rather
     * than left null, so callers never have to branch on absence. See {@link PlayerRole} for why
     * this isn't a permission.
     */
    private PlayerRole role;
}
