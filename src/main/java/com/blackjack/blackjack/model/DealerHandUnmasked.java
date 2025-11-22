package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealerHandUnmasked implements Hand, Serializable {
    private List<Card> hand;
    private int natural21;

    public DealerHandUnmasked() {
    }

    public DealerHandUnmasked(List<Card> hand, int natural21) {
        this.hand = hand;
        this.natural21 = natural21;
    }

    public static DealerHandUnmasked createEmptyHand() {
        DealerHandUnmasked unMaskedHand = new DealerHandUnmasked();
        unMaskedHand.setHand(new ArrayList<>());
        unMaskedHand.setNatural21(0);
        return unMaskedHand;
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public int getHandState() {
        HandState state = HandStateUpdater.updateHandState(this.getSum(), this.hand.size());

        return state.getValue();
    }
}
