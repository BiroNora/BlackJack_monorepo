package com.blackjack.blackjack.service;

import com.blackjack.blackjack.exception.GameRuleException;
import com.blackjack.blackjack.model.GameState;
import com.blackjack.blackjack.model.Player;
import com.blackjack.blackjack.repository.PlayerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PlayerService {
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    public PlayerService(PlayerRepository playerRepository, PasswordEncoder passwordEncoder) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Levonja a tétet a játékos tokenjeiből,
     * immutábilis módon létrehozva egy új Player objektumot a frissített állapottal.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param betAmount A levonandó tét összege.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player deductBet(Player oldPlayer, int betAmount) {
        if (oldPlayer.getTokens() < betAmount) {
            throw new GameRuleException("NOT_ENOUGH_TOKENS_FOR_BET");
        }

        int newTokens = oldPlayer.getTokens() - betAmount;

        // Immutábilis frissítés: új Player objektum létrehozása toBuilder() és build() segítségével
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(newTokens)
            .build();

        // Elmentjük az új, frissített objektumot.
        return playerRepository.save(updatedPlayer);
    }

    /**
     * Hozzáadja a nyereményt a játékos tokenjeihez.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param rewardAmount A hozzáadandó jutalom összege.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player updateTokens(Player oldPlayer, int rewardAmount) {
        int newTokens = oldPlayer.getTokens() + rewardAmount;

        if (newTokens < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
        }

        // Immutábilis frissítés
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(newTokens)
            .build();

        return playerRepository.save(updatedPlayer);
    }

    /**
     * Visszaállítja a játékos tokenjeit egy kezdő értékre.
     * * @param oldPlayer Az eredeti (régi) Player objektum.
     *
     * @param initialTokens A beállítandó kezdő token mennyiség.
     * @return Az újonnan mentett Player objektum.
     */
    @Transactional
    public Player resetTokens(Player oldPlayer, int initialTokens) {
        // Immutábilis frissítés
        Player updatedPlayer = oldPlayer.toBuilder()
            .tokens(initialTokens)
            .build();

        return playerRepository.save(updatedPlayer);
    }

    public Player getAndValidatePlayer(UUID clientId) {
        if (clientId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Missing Client ID");
        }
        return playerRepository.findByClientId(clientId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Player not found"));
    }

    public Object handleUserAuth(String email, String username, String password, boolean isLogIn, UUID currClientId, UUID idempotencyKey) {
        if (isLogIn) {
            //System.out.println(" *************** ISLOGIN TRUE");
            // --- BEJELENTKEZÉS ---
            Player targetUser = playerRepository.findByEmailOrUsername(email, username)
                .orElse(null);

            if (targetUser == null || !passwordEncoder.matches(password, targetUser.getPasswordHash())) {
                return Map.of("status", "IC"); // Invalid Credentials
            }

            if (currClientId != null && !currClientId.equals(targetUser.getId())) {
                Player sessionUser = playerRepository.findByClientId(currClientId).orElse(null);

                if (sessionUser != null && sessionUser.isGuest()) {
                    GameState dbRaw = targetUser.getCurrentGameState();
                    GameState sessionRaw = sessionUser.getCurrentGameState();

                    boolean hasDbGame = dbRaw != null && !dbRaw.toString().equals("{}");
                    boolean hasSessionGame = sessionRaw != null && !sessionRaw.toString().equals("{}");

                    // Ha mindkét helyen van aktív játékállás, ütközés van!
                    if (hasDbGame && hasSessionGame) {
                        return Map.of("status", "CONFLICT");
                    }

                    // Ha a session-ben van játék, de a célfiókban NINCS, átköltöztetjük
                    if (hasSessionGame) {
                        targetUser.setCurrentGameState(sessionRaw);
                        playerRepository.save(targetUser);
                    }
                }
            }

            return targetUser;

        } else {
            //System.out.println(" *************** ISLOGIN FALSE");
            // --- REGISZTRÁCIÓ ---
            // 1. Username formátum ellenőrzése regex-szel (3-30 karakter, betűk, számok, aláhúzás)
            if (username == null || !username.matches("^[a-zA-Z0-9_]{3,30}$")) {
                return Map.of("status", "IU"); // Invalid Username format
            }

            // 2. Létezik-e már ilyen email vagy username az adatbázisban?
            boolean exists = playerRepository.existsByEmailOrUsername(email, username);
            if (exists) {
                return Map.of("status", "UAE"); // User Already Exists
            }

            Player user = null;
            // 3. Ha van aktív vendég session (currClientId), megkeressük
            if (currClientId != null) {
                user = playerRepository.findByClientId(currClientId).orElse(null);
            }

            if (user != null) {
                user = user.toBuilder()
                    .email(email)
                    .username(username)
                    .idempotencyKey(idempotencyKey)
                    .passwordHash(passwordEncoder.encode(password))
                    .isGuest(false)
                    .build();

            } else {
                // Ha nincs session, létrehozunk egy teljesen új Playert
                user = new Player();
                user.setEmail(email);
                user.setUsername(username);
                user.setIdempotencyKey(idempotencyKey);
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setGuest(false);

                user.setClientId(Objects.requireNonNullElseGet(currClientId, UUID::randomUUID));

                if (idempotencyKey != null) {
                    user.setIdempotencyKey(idempotencyKey);
                } else if (user.getIdempotencyKey() == null) {
                    user.setIdempotencyKey(UUID.randomUUID());
                }
            }

            playerRepository.save(user);

            return user;
        }
    }

    public Object handleConflict(UUID currClientId, UUID targetUserId, boolean versionNew) {
        Player targetUser = playerRepository.findByClientId(targetUserId).orElse(null);  // régi mentett
        Player sessionUser = (currClientId != null) ? playerRepository.findByClientId(currClientId).orElse(null) : null; // jelenlegi

        if (targetUser == null) {
            return Map.of("status", "IC"); // Invalid Credentials
        }

        if (sessionUser != null && sessionUser.isGuest()) {
            if (versionNew) {
                if (sessionUser.getCurrentGameState() != null) {
                    targetUser.setCurrentGameState(sessionUser.getCurrentGameState());
                    targetUser.setTokens(sessionUser.getTokens());
                }
            }
        }

        targetUser.setGuest(false);
        playerRepository.save(targetUser);

        return targetUser;
    }

    @Transactional
    public Player savePlayer(Player player) {
        return playerRepository.save(player);
    }

    public boolean isDuplicateRequest(Player player, UUID incomingKey) {
        return player.getIdempotencyKey() != null &&
            player.getIdempotencyKey().equals(incomingKey);
    }

    public boolean hasEmptyFields(String email, String username, String password) {
        return (
            (email == null || email.trim().isEmpty()) ||
                (username == null || username.trim().isEmpty()) ||
                (password == null || password.trim().isEmpty()));
    }
}
