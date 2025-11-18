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

    // --- Alapvető azonosítók ---
    private UUID clientId;
    private String sessionId; // Opcionális, de segíti a diagnosztikát

    // --- Játékmenet Kötelező Elemek ---
    // A deck (ami a lapokat tárolja)
    private Deck deck;
    // Aktuális tét és tétek listája (BetController által frissül)
    private int bet; // Az aktuális tét
    private List<Integer> betList; // A korábbi/split tétek listája
    private boolean isRoundActive; // Helyettesíti az is_round_active-ot
    // --- Állapot Változók ---
    private String winner; // A winner tárolása (Pl. "PLAYER", "DEALER", "PUSH")

    // --- Kéz (Hand) Objektumok ---
    // A player, dealer_masked/unmasked, split_player hivatkozhatnak egy Hand objektumra
    // A Hand tartalmazza a kártyákat és a pontszámot.
    //private Hand playerHand; // A player (fő játékos kéz)
    //private Hand dealerHand; // A dealer_unmasked/masked kezelése ezen a Hand-en keresztül történik
    //private Hand splitPlayerHand; // A split_player
    private String phase; // Opcionális: a kézi állapot (pl. "DEALING", "PLAYERS_TURN")
    // --- Speciális/Számláló Változók ---
    private int aces; // Segíthet az Ász rugalmas pontozásában, de a Hand felelőssége is lehet
    private boolean natural21; // natural_21 (Pl. Blackjack az első két lapból)
    private int handCounter; // hand_counter (Melyik split kéz van soron, ha van)
    //private Map<String, Hand> players; // players - Ha több játékos van vagy a split kéz ID alapján megy
    private Map<String, Integer> playersIndex; // players_index (Komplexebb esetben)
    // --- Frontend Segítő Változók ---
    private boolean splitReq; // split kérés
    private boolean unmaskedSumSent; // unmasked_sum_sent (Elküldtük-e már a dealer rejtett lapjának értékét)

    public int getDeckLen() {
        return this.deck != null ? this.deck.getDeckLength() : 0;
    }

    // --- Megjegyzés ---
    // A deckLen (get_deck_len()) egy származtatott érték, amit a DTO-ban adunk vissza:
    // deckLen = this.deck.getDeckLength();
}
