package com.blackjack.blackjack.controller;

import com.blackjack.blackjack.dto.ActionRequest;
import com.blackjack.blackjack.dto.ApiResponse;
import com.blackjack.blackjack.dto.bet.GameStateBet;
import com.blackjack.blackjack.dto.others.*;
import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.service.GameService;
import com.blackjack.blackjack.service.GameStateManager;
import com.blackjack.blackjack.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.blackjack.blackjack.common.GameConstants.INITIAL_DECK_LENGTH;
import static com.blackjack.blackjack.common.GameConstants.INITIAL_TOKENS;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;
    private final PlayerService playerService;
    private final GameStateManager gameStateManager;

    public GameController(GameService gameService, PlayerService playerService, GameStateManager gameStateManager) {
        this.gameService = gameService;
        this.playerService = playerService;
        this.gameStateManager = gameStateManager;
    }

    /**
     * GENERIKUS KERETRENDSZER (DRY)
     * Ez a metódus kezeli a validálást, idempotenciát, mentést és DTO konverziót.
     */
    private <T> ResponseEntity<?> handleAction(
        ActionRequest request,
        String hint,
        Function<GameState, T> dtoBuilder,
        UnaryOperator<GameState> action,
        Integer tokenChange
    ) {
        Player player = playerService.getAndValidatePlayer(request.getClientId());

        // 1. Idempotencia ellenőrzés
        if (playerService.isDuplicateRequest(player, request.getIdempotencyKey())) {
            return ResponseEntity.ok(ApiResponse.builder()
                .status("SUCCESS")
                .currentTokens(player.getTokens())
                .gameState(dtoBuilder.apply(player.getCurrentGameState()))
                .build());
        }

        GameState current = player.getCurrentGameState();
        if (current == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active game state");

        // 2. Üzleti logika futtatása
        GameState updated = action.apply(current);

        // 3. Mentés előkészítése
        Player playerToSave = player.toBuilder()
            .currentGameState(updated)
            .idempotencyKey(request.getIdempotencyKey())
            .build();

        // 4. Atomi mentés (Token változással vagy anélkül)
        Player savedPlayer = (tokenChange != null && tokenChange != 0)
            ? playerService.updateTokens(playerToSave, tokenChange)
            : playerService.savePlayer(playerToSave);

        // 5. Válasz küldése
        return ResponseEntity.ok(ApiResponse.builder()
            .status("SUCCESS")
            .gameState(dtoBuilder.apply(updated))
            .gameStateHint(hint)
            .currentTokens(savedPlayer.getTokens())
            .build());
    }

    // --- STANDARD JÁTÉK VÉGPONTOK ---
    @PostMapping("/create_deck")
    public ResponseEntity<?> createDeck(@RequestBody ActionRequest req) {
        return handleAction(req, "DECK_CREATED", this::buildGameStateBet, gameStateManager::createNewDeck, null);
    }

    @PostMapping("/start_game")
    public ResponseEntity<?> initializeNewRound(@RequestBody ActionRequest req) {
        return handleAction(req, "NEW_ROUND_INITIALIZED", this::buildGameStateStart, gameService::initializeNewRound, null);
    }

    @PostMapping("/hit")
    public ResponseEntity<?> playerHit(@RequestBody ActionRequest req) {
        return handleAction(req, "HIT_RECEIVED", this::buildGameStateStart, gameService::playerHit, null);
    }

    @PostMapping("/stand_and_rewards")
    public ResponseEntity<?> standAndRewards(@RequestBody ActionRequest req) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());

        GameState stoodState = gameService.playerStand(player.getCurrentGameState());
        GameService.TransactionResult result = gameService.calculateRewards(stoodState);

        return handleAction(
            req,
            "REWARDS_PROCESSED",
            this::buildGameStateRewards,
            state -> result.gameState(),    // Az új állapot (benne a dealer lapjaival)
            result.transactionResult()      // A TÉNYLEGES nyeremény (pl. +200), így bekerül a DB-be!
        );
    }

    @PostMapping("/double_request")
    public ResponseEntity<?> doubleRequest(@RequestBody ActionRequest req) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = p.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round");
        }

        if (current.getPlayer() == null) {
            throw new IllegalStateException("Critical error: Player object is missing from GameState during Double request");
        }

        if (current.getPlayer().hand().size() != 2) {
            throw new GameRuleException("DOUBLE NOT ALLOWED");
        }

        int betToDeduct = current.getBet();
        if (p.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS");
        }

        return handleAction(req, "DOUBLE_PROCESSED", this::buildGameStateDouble, state -> {
            GameState afterHit = gameService.playerHit(state);
            return gameService.applyDoubleBet(afterHit, betToDeduct);
        }, -betToDeduct);
    }

    @PostMapping("/ins_request")
    public ResponseEntity<?> insuranceRequest(@RequestBody ActionRequest req) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameService.TransactionResult res = gameService.insuranceRequest(player.getCurrentGameState());

        return handleAction(req, "INS_PROCESSED", this::buildGameStateInsurance,
            state -> res.gameState(),
            res.transactionResult()
        );
    }

    @PostMapping("/rewards")
    public ResponseEntity<?> rewards(@RequestBody ActionRequest req) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = player.getCurrentGameState();

        if (current == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active game state for rewards");
        }

        GameService.TransactionResult rewardResult = gameService.calculateRewards(current);

        return handleAction(
            req,
            "REWARDS_PROCESSED",
            this::buildGameStateRewards,
            state -> rewardResult.gameState(), // Az action visszaadja a már kiszámolt állapotot
            rewardResult.transactionResult()   // Itt adjuk át a token változást (nyereményt)
        );
    }

    // --- SPLIT VÉGPONTOK ---
    @PostMapping("/split_request")
    public ResponseEntity<?> splitRequest(@RequestBody ActionRequest req) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());
        int bet = p.getCurrentGameState().getBet();
        return handleAction(req, "SPLIT_PROCESSED", this::buildGameStateSplitHand, gameService::splitHand, -bet);
    }

    @PostMapping("/add_to_players_list_by_stand")
    public ResponseEntity<?> addToPlayersByStand(@RequestBody ActionRequest req) {
        return handleAction(req, "HAND_SAVED", this::buildGameStateAddToPlayers, gameService::addToPlayersListByStand, null);
    }

    @PostMapping("/add_split_player_to_game")
    public ResponseEntity<?> addSplitPlayerToGame(@RequestBody ActionRequest req) {
        return handleAction(req, "NEXT_SPLIT_HAND_ACTIVE", this::buildGameStateSplitHand, gameService::addSplitPlayerToGame, null);
    }

    @PostMapping("/add_player_from_players")
    public ResponseEntity<?> addPlayerFromPlayers(@RequestBody ActionRequest req) {
        return handleAction(req, "ALL_HANDS_COLLECTED", this::buildGameStateAddPlayerFromPlayers, gameService::addPlayerFromPlayers, null);
    }

    @PostMapping("/split_hit")
    public ResponseEntity<?> splitHit(@RequestBody ActionRequest req) {
        return handleAction(req, "SPLIT_HIT_RECEIVED", this::buildGameStateSplitHand, gameService::playerHit, null);
    }

    @PostMapping("/split_stand_and_rewards")
    public ResponseEntity<?> splitStandAndRewards(@RequestBody ActionRequest req) {
        Player player = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = player.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round to stand");
        }

        GameState stoodState = gameService.playerStand(current);
        GameService.TransactionResult rewardResult = gameService.calculateRewards(stoodState);

        return handleAction(
            req,
            "REWARDS_PROCESSED",
            this::buildGameStateSplitRewards,
            state -> rewardResult.gameState(),
            rewardResult.transactionResult()
        );
    }

    @PostMapping("/split_double_request")
    public ResponseEntity<?> splitDoubleRequest(@RequestBody ActionRequest req) {
        Player p = playerService.getAndValidatePlayer(req.getClientId());
        GameState current = p.getCurrentGameState();

        if (current == null || !current.isRoundActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active round for split double");
        }

        if (current.getPlayer() == null) {
            throw new IllegalStateException("Critical error: Player object is missing from GameState during Double request");
        }

        if (current.getPlayer().hand().size() != 2) {
            throw new GameRuleException("DOUBLE_ONLY_ALLOWED_ON_STARTING_HAND");
        }

        int betToDeduct = current.getPlayer().bet();
        if (p.getTokens() < betToDeduct) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_DOUBLE");
        }

        return handleAction(
            req,
            "SPLT_REQUEST_PROCESSED",
            this::buildGameStateSplitHand,
            state -> {
                GameState afterHit = gameService.playerHit(state);
                return gameService.applyDoubleBet(afterHit, betToDeduct);
            },
            -betToDeduct
        );
    }

    @PostMapping("/set_restart")
    public ResponseEntity<?> setRestart(@RequestBody ActionRequest actionRequest) {
        Player player = playerService.getAndValidatePlayer(actionRequest.getClientId());

        Player playerToUpdate = player.toBuilder()
            .tokens(INITIAL_TOKENS)
            .currentGameState(null) // Ha már null volt, null marad, ha nem, akkor törlődik
            .idempotencyKey(actionRequest.getIdempotencyKey())
            .build();

        Player savedPlayer = playerService.savePlayer(playerToUpdate);

        GameStateBet emptyGameState = GameStateBet.builder()
            .deckLen(INITIAL_DECK_LENGTH)
            .build();

        return ResponseEntity.ok(ApiResponse.builder()
            .status("SUCCESS")
            .currentTokens(savedPlayer.getTokens())
            .gameState(emptyGameState)
            .gameStateHint("GAME_RESET_TO_NULL")
            .build());
    }


    // --- DTO BUILDEREK ---
    private GameStateBet buildGameStateBet(GameState s) {
        return GameStateBet.builder().bet(s.getBet()).betList(s.getBetList()).deckLen(s.getDeckLen()).build();
    }

    private GameStateStart buildGameStateStart(GameState s) {
        return GameStateStart.builder().player(s.getPlayer()).dealerMasked(s.getDealerMasked()).bet(s.getBet()).deckLen(s.getDeckLen()).isRoundActive(s.isRoundActive()).build();
    }

    private GameStateRewards buildGameStateRewards(GameState s) {
        return GameStateRewards.builder().player(s.getPlayer()).dealerUnmasked(s.getDealerUnmasked()).deckLen(s.getDeckLen()).bet(s.getBet()).winner(s.getWinner()).isRoundActive(s.isRoundActive()).build();
    }

    private GameStateDouble buildGameStateDouble(GameState s) {
        return GameStateDouble.builder().player(s.getPlayer()).deckLen(s.getDeckLen()).isRoundActive(s.isRoundActive()).build();
    }

    private GameStateInsurance buildGameStateInsurance(GameState s) {
        return GameStateInsurance.builder().player(s.getPlayer()).natural21(s.getNatural21()).deckLen(s.getDeckLen()).bet(s.getBet()).isRoundActive(s.isRoundActive()).dealerMasked(s.getDealerMasked()).dealerUnmasked(s.getDealerUnmasked()).build();
    }

    private GameStateSplitHand buildGameStateSplitHand(GameState s) {
        return GameStateSplitHand.builder().player(s.getPlayer()).dealerHandMasked(s.getDealerMasked()).aces(s.isAces()).players(s.getPlayers()).splitReq(s.getSplitReq()).deckLen(s.getDeckLen()).bet(s.getBet()).isRoundActive(s.isRoundActive()).build();
    }

    private GameStateAddToPlayersByStand buildGameStateAddToPlayers(GameState s) {
        return GameStateAddToPlayersByStand.builder().player(s.getPlayer()).aces(s.isAces()).players(s.getPlayers()).splitReq(s.getSplitReq()).deckLen(s.getDeckLen()).bet(s.getBet()).isRoundActive(s.isRoundActive()).dealerMasked(s.getDealerMasked()).dealerUnmasked(s.getDealerUnmasked()).build();
    }

    private GameStateAddPlayerFromPlayers buildGameStateAddPlayerFromPlayers(GameState s) {
        return GameStateAddPlayerFromPlayers.builder().player(s.getPlayer()).dealerUnmasked(s.getDealerUnmasked()).aces(s.isAces()).players(s.getPlayers()).splitReq(s.getSplitReq()).deckLen(s.getDeckLen()).bet(s.getBet()).isRoundActive(s.isRoundActive()).build();
    }

    private GameStateSplitStandAndRewards buildGameStateSplitRewards(GameState state) {
        return GameStateSplitStandAndRewards.builder()
            .player(state.getPlayer())
            .dealerUnmasked(state.getDealerUnmasked())
            .players(state.getPlayers())
            .winner(state.getWinner())
            .splitReq(state.getSplitReq())
            .deckLen(state.getDeckLen())
            .bet(state.getBet())
            .isRoundActive(state.isRoundActive())
            .build();
    }
}
