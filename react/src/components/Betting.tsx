import type {GameStateData} from "../types/game-types";
import "../styles/betting.css";
import {formatNumber} from "../utilities/utils";
import React, {useEffect, useRef, useState} from "react";

interface BettingProps {
  gameState: GameStateData;
  onPlaceBet: (amount: number) => void;
  retakeBet: () => void;
  onStartGame: () => void;
  isWFSR: boolean;
}

const Betting: React.FC<BettingProps> = ({
                                           gameState,
                                           onPlaceBet,
                                           retakeBet,
                                           onStartGame,
                                           isWFSR,
                                         }) => {
  const {tokens, bet} = gameState;

  const [showButtons, setShowButtons] = useState(false);
  const timeoutIdRef = useRef<number | null>(null);

  useEffect(() => {
    timeoutIdRef.current = window.setTimeout(() => {
      setShowButtons(true);
    }, 1000); // 1000 ms = 1 másodperc késleltetés

    return () => {
      if (timeoutIdRef.current !== null) {
        window.clearTimeout(timeoutIdRef.current);
      }
    };
  }, []);

  const betAmounts = [1, 5, 10, 50, 100, 500, 1000, 5000, 10000];

  const handleAllIn = () => {
    onPlaceBet(tokens);
  };

  const isDisabled = bet === 0;

  const getButtonStateClass = () => {
    if (isWFSR) return "btn-disabled-server";
    if (isDisabled) return "btn-disabled-user";
    return "btn-enabled";
  };

  return (
    <div className="betting-screen-container">
      <button
        id="start-button"
        className={`start-button-base ${getButtonStateClass()}`}
        onClick={onStartGame}
        disabled={isDisabled || isWFSR}
      >
        <span>Start Game</span>
      </button>

      <div id="deal-bank" className="deal-bank">
        <button
          id="deal-button"
          className={getButtonStateClass()}
          onClick={() => retakeBet()}
          disabled={isDisabled || isWFSR}
        >
          <span>
            Bet: {"  " + formatNumber(bet)}
          </span>
        </button>
      </div>

      {/* BANK section */}
      <div className="bank merriweather">
        Player's bank:{"\u00A0"}
        <div
          style={{
            display: "inline-grid",
            verticalAlign: "bottom",
            placeItems: "start",
            width: "5rem",
          }}
        >
          <span
            key={formatNumber(tokens)}
            className="players-bank-fade-in"
            style={{
              gridArea: "1 / 1",
              whiteSpace: "nowrap",
              display: "inline-block",
            }}
          > {"\u00A0"}
            {formatNumber(tokens)}
          </span>
        </div>
      </div>

      <div
        id="chips"
        className={`button-container ${showButtons ? "show-buttons" : ""}`}
      >
        {/* All In */}
        <button
          id="all-in"
          type="button"
          onClick={handleAllIn}
          className={
            isWFSR
              ? "btn-disabled-server"
              : tokens === 0
                ? "btn-disabled-user"
                : "btn-enabled"
          }
          disabled={tokens === 0 || isWFSR}
        >
          All In
        </button>

        {/* Bets */}
        {betAmounts.map((amount) => {
          const isUserDisabled = tokens < amount;
          const btnClass = isWFSR
            ? "btn-disabled-server"
            : isUserDisabled
            ? "btn-disabled-user"
            : "btn-enabled";

          return (
            <button
              key={amount}
              id={String(amount)}
              type="button"
              data-bet={amount}
              onClick={() => onPlaceBet(amount)}
              className={btnClass}
              disabled={isUserDisabled || isWFSR}
            >
              {formatNumber(amount)}
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default Betting;
