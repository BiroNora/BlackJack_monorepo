package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DealerHandUnmasked implements Hand, Serializable {
    private List<Card> hand;

    @JsonProperty("natural_21")
    private int natural21;

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
