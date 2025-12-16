package com.blackjack.blackjack.model;

import com.blackjack.blackjack.service.HandStateUpdater;
import com.blackjack.blackjack.service.HandValueCalculator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DealerHandUnmasked(
    List<Card> hand,
    @JsonProperty("natural_21") int natural21
) implements Hand, Serializable {

    public static DealerHandUnmasked createEmptyHand() {
        return DealerHandUnmasked.builder()
            .hand(new ArrayList<>())
            .natural21(0)
            .build();
    }

    public int getSum() {
        return HandValueCalculator.calculateSum(this.hand);
    }

    public int getHandState() {
        HandState state = HandStateUpdater.updateHandState(this.getSum(), this.hand.size());
        return state.getValue();
    }
}
