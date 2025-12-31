package com.blackjack.blackjack.dto.init;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InitializationRequest(
    @JsonProperty("clientId")
    UUID clientId) {
}
