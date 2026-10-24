import { states, type GameStateData } from "../types/game-types";
import React from "react";
import "../styles/winner.css";

interface TableProps {
  gameState: GameStateData;
}

const Winner: React.FC<TableProps> = ({ gameState }) => {
  const { dealer_unmasked, winner } = gameState;

  const index =
      dealer_unmasked.natural_21 !== 0 ? dealer_unmasked.natural_21 : winner;
  const winners = states[index];

  return (
    <div className="winners merriweather9black">
      <span className="winners-fade-in">
      {winners}
    </span>
    </div>
  );
};

export default Winner;
