package com.blackjack.blackjack.dto.init;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForceRestartRequest(
    @JsonProperty("clientId")
    String clientId) {
}
