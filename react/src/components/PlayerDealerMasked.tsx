import React, { type JSX } from "react";
import type { GameStateData } from "../types/game-types";
import "../styles/playerDealer.css";

interface TableProps {
  gameState: GameStateData;
  showInsLost: boolean;
}

const PlayerDealerMasked: React.FC<TableProps> = ({
  gameState,
  showInsLost,
}) => {
  const { player, dealer_masked } = gameState;

  const formatCard = (card: string): JSX.Element | string => {
    if (card.trim() === "✪") {
      return <span className="red-suit"> ✪ </span>;
    }

    const suit = card[0]; // Az első karakter a szín
    const value = card.substring(1).trim(); // A többi karakter az érték

    let suitClass = "";
    if (suit === "♥" || suit === "♦") {
      suitClass = "red-suit";
    } else if (suit === "♠" || suit === "♣") {
      suitClass = "black-suit";
    } else {
      return card; // Visszaadja a nyers stringet, ha nem felismerhető a szín
    }

    return (
      <React.Fragment>
        <span style={{ whiteSpace: "nowrap" }}>
          <span className={suitClass}>{suit}</span>
          <span className="merriweatherblack">{value}</span>
        </span>
      </React.Fragment>
    );
  };

  const formatHand = (cardStrings: string[]): JSX.Element[] => {
    const formattedElements = cardStrings.map((cardString, index) => {
      const separator =
        index > 0 ? (
          <span
            key={`hand-sep-${index}`}
            className="equal-text merriweather5grey"
          >
            {" "}
            +{" "}
          </span>
        ) : null;

      // React.Fragment használata a szeparátor és a kártya csoportosítására (fontos a 'key' prop!)
      return (
        <React.Fragment key={cardString + index}>
          {separator} {formatCard(cardString)}{" "}
        </React.Fragment>
      );
    });

    return formattedElements; // JSX elemek tömbje
  };

  const loop = (data: string[]): string[] => {
    return data.map((card) => String(card).trim());
  };

  const playerHand = loop(player.hand);
  const dealerHand = loop(dealer_masked.hand);

  const formattedPlayerHand = formatHand(playerHand);
  const formattedDealerHand = formatHand(dealerHand);

  return (
    <div>
      <div id="dealer-hand" className="play">
        <div className="hand hand-area-wrapper">{formattedDealerHand}</div>
        <div className="score-area-wrapper">
          <span className="score-mood merriweather5grey2">{}</span>
        </div>
        <div className="band-area-wrapper">
          <span className="label-text">Dealer: </span>
          <span className="label-text1">{dealer_masked.sum}</span>
        </div>
      </div>
      <div id="player-hand" className="play">
        <div className="band-area-wrapper">
          <span className="label-text">Player: </span>
          <span className="label-text1"> {player.sum}</span>
        </div>
        <div className="score-area-wrapper">
            {" "}
            {showInsLost ? (
              <span
                className="score-mood merriweather9red ins-fade-animation"
              >
                Insurance lost
              </span>
            ) : (
              <span
                className="score-mood merriweather5grey2"
              >
                {"\u00A0"} {/* Üres karakter, hogy a magasság megmaradjon */}
              </span>
            )}
        </div>
        <div className="hand hand-area-wrapper">{formattedPlayerHand}</div>
      </div>
    </div>
  );
};

export default PlayerDealerMasked;
