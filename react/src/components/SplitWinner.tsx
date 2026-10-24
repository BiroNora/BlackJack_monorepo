import {type GameStateData, states} from "../types/game-types";
import "../styles/winner.css";
import React from "react";

interface TableProps {
  gameState: GameStateData;
}

const SplitWinner: React.FC<TableProps> = ({gameState}) => {
  const {dealer_unmasked, winner} = gameState;
  const BJ_DEALER_WON = 3;

  const winners =
    dealer_unmasked.natural_21 === BJ_DEALER_WON
      ? states[BJ_DEALER_WON]
      : states[winner];

  return (
    <div className="winners merriweather9black">
      <span className="winners-fade-in">
      {winners}
      </span>
    </div>
  );
};

export default SplitWinner;
