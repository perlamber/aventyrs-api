package org.aventyrs.api.scene.dto;

/** The GM's "Pular reações" — closes the open window whoever has not answered (GM-only, core 0.1.6.17). */
public record ReactionWindowCloseMessage(String windowId) {
}
