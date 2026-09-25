package org.aventyrs.api.campaign.dto;

import jakarta.validation.constraints.NotBlank;

/** A participant takes a bag item into their sheet's inventory. */
public record BagClaimRequest(@NotBlank String characterSheetId) {
}
