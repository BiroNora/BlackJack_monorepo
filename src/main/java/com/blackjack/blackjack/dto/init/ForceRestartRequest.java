package com.blackjack.blackjack.dto.init;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForceRestartRequest(
    @JsonProperty("client_id")
    String clientId) {
}
