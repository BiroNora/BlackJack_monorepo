package com.blackjack.blackjack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState implements Serializable {
    private UUID clientId;
    private String sessionId; // Opcionális, de segíti a diagnosztikát

    // --- Játékmenet Kötelező Elemek ---
    private Deck deck;
    private int bet;
    private List<Integer> betList;
    private boolean isRoundActive;

    // --- Kéz (Hand) Objektumok ---
    private PlayerHand player;
    private DealerHandMasked dealerMasked;
    private DealerHandUnmasked dealerUnmasked;

    // --- Speciális/Számláló Változók ---
    private boolean aces;
    private int natural21;
    private int winner;
    private Map<String, PlayerHand> players;
    private int splitReq;
    private int handCounter;

    public int getDeckLen() {
        return this.deck != null ? this.deck.getDeckLength() : 0;
    }

    // --- Megjegyzés ---
    // A deckLen (get_deck_len()) egy származtatott érték, amit a DTO-ban adunk vissza:
    // deckLen = this.deck.getDeckLength();

    public String generateNextHandId() {
        this.handCounter++;
        String formattedCount = String.format("%03d", this.handCounter);

        return "H-" + formattedCount;
    }
}
