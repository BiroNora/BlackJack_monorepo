package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Value
@Builder(toBuilder = true)
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Jacksonized
public class GameState implements Serializable {
    UUID clientId;

    // --- Játékmenet Kötelező Elemek ---
    Deck deck;
    int bet;

    @JsonProperty("bet_list")
    List<Integer> betList;

    @JsonProperty("is_round_active")
    boolean isRoundActive;

    // --- Kéz (Hand) Objektumok ---
    PlayerHand player;

    @JsonProperty("dealer_masked")
    DealerHandMasked dealerMasked;

    @JsonProperty("dealer_unmasked")
    DealerHandUnmasked dealerUnmasked;
    PlayerHand splitPlayer;

    // --- Speciális/Számláló Változók ---
    boolean aces;

    @JsonProperty("natural_21")
    int natural21;
    int winner;

    @Builder.Default
    Map<String, PlayerHand> players = Map.of();

    @JsonProperty("split_req")
    int splitReq;
    int handCounter;
    boolean wasSplitInRound;

    @Builder.Default
    Map<String, Boolean> playersIndex = Map.of();

    public int getDeckLen() {
        return this.deck != null ? this.deck.getDeckLength() : 0;
    }

    public int calculateNextHandCounter() {
        return this.handCounter + 1;
    }

    public int calculateNewSplitReq(int count) {
        return this.splitReq + count;
    }
}
