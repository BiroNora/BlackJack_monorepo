import {useEffect, useState} from "react";
import type {GameStateData} from "../types/game-types";
import {formatNumber} from "../utilities/utils";

interface UniqueBetBankProps {
  gameState: GameStateData;
  isResultPhase: boolean; // Új prop, amivel jelezzük a várakozást
  preRewardBet?: number | null;
  preRewardTokens?: number | null;
}

const UniqueBetBank: React.FC<UniqueBetBankProps> = ({
                                                       gameState,
                                                       isResultPhase,
                                                       preRewardBet,
                                                       preRewardTokens,
                                                     }) => {
  // Belső állapot a megjelenített értékeknek
  const [displayedBet, setDisplayBet] = useState(gameState.player.bet);
  const [displayedTokens, setDisplayTokens] = useState(gameState.tokens);
  const isTransit = gameState.currentGameState === "MAIN_STAND_REWARDS_TRANSIT";

  useEffect(() => {
    if (
      isResultPhase &&
      preRewardBet !== undefined &&
      preRewardTokens !== undefined
    ) {
      // 1. Eredmény fázisban először beállítjuk a "snapshot" (régi) értékeket
      setDisplayBet(preRewardBet ?? gameState.player.bet);
      setDisplayTokens(preRewardTokens ?? gameState.tokens);

      // 2. Majd 2 másodperc múlva frissítünk a végsőre
      const timer = setTimeout(() => {
        setDisplayBet(gameState.player.bet);
        setDisplayTokens(gameState.tokens);
      }, 2000);
      return () => clearTimeout(timer);
    } else {
      // 3. Játék közben (MAIN_TURN stb.) azonnal frissítünk, nincs várakozás
      setDisplayBet(gameState.player.bet);
      setDisplayTokens(gameState.tokens);
    }
  }, [
    gameState.player.bet,
    gameState.tokens,
    isResultPhase,
    preRewardBet,
    preRewardTokens,
  ]);

  const tokensToDisplay =
    displayedTokens !== null ? formatNumber(displayedTokens) : "---";
  const betToDisplay =
    displayedBet !== null ? formatNumber(displayedBet) : "---";

  return (
    <div className="bank-area-wrapper">
      {/* BET SZEKCIÓ */}
      <div className="bank1 merriweather">
        Bet:{"\u00A0"}
        <div style={{display: "inline-grid", verticalAlign: "bottom"}}>
            <span
              className="uniquebet-fade-in"
              style={{gridArea: "1 / 1", whiteSpace: "nowrap"}}
            >
              {betToDisplay}
            </span>
        </div>
      </div>

      {/* BANK SZEKCIÓ */}
      <div className="bet-bank merriweather">
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
            key={tokensToDisplay}
            className={isTransit ? "bet-fade-in" : "bet-fade-in-delayed"}
            style={{
              gridArea: "1 / 1",
              whiteSpace: "nowrap",
              display: "inline-block",
            }}
          >
            {tokensToDisplay}
          </span>
        </div>
      </div>
    </div>
  );
};

export default UniqueBetBank;
