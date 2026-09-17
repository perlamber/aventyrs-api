package org.aventyrs.api.campaign.dto;

import jakarta.validation.constraints.NotBlank;

public record CampaignCreateRequest(
        @NotBlank String name) {
}
