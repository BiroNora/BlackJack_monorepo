import React, { useEffect, useState } from "react";
import "../styles/loading.css";
import "../styles/auth.css";

interface LoadingProps {
  onOpenAuth: () => void;
  onSkipAuth: () => void;
  isAuthOpen: boolean;
  isWFSR: boolean;
}

const Loading: React.FC<LoadingProps> = ({
                                           onOpenAuth,
                                           onSkipAuth,
                                           isAuthOpen,
                                           isWFSR,
                                         }) => {
  const [isClicked, setIsClicked] = useState(false);
  const [showButtons, setShowButtons] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setShowButtons(true);
    }, 350);

    return () => clearTimeout(timer);
  }, []);
  const hideButtons = !showButtons || isClicked || isAuthOpen || isWFSR;

  return (
    <>
      {!isAuthOpen && (
        <div className="loading-container-centered">
          <h1>
            L O A D I N G<span className="dot dot-1">.</span>
            <span className="dot dot-2">.</span>
            <span className="dot dot-3">.</span>
          </h1>
        </div>
      )}

      {!hideButtons && (
        <div>
          <div className="text">
            <button
              className="submit-btn"
              onClick={(e) => {
                e.preventDefault();
                setIsClicked(true);
                onOpenAuth();
              }}
            >
              Log in to save your scores
            </button>

            <button
              className="submit-btn"
              onClick={(e) => {
                e.preventDefault();
                onSkipAuth();
              }}
            >
              No, thanks
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default Loading;
