package com.blackjack.blackjack.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitializationResponse {
    private UUID clientId;
    private int tokens;
}
