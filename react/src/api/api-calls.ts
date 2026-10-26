import { API_BASE_URL } from "../config/apiConfig";
import type { ErrorResponse, SessionInitResponse } from "../types/game-types";

const runningInitializations = new Map<string, Promise<SessionInitResponse>>();

// Statikus kulcs a futó inicializálási folyamat jelzésére (sik = Session Init Key)
const SESSION_INIT_KEY = '__sik__';
// localStorage számára (cid = Client ID)
const CLIENT_STORAGE_ID_KEY = 'cid';


export async function initializeSessionAPI(
  skipAuth: boolean = false,
): Promise<SessionInitResponse> {
  //console.log("🚀 initializeSessionAPI hívása elindult!");
  // 1. 🔑 BLOKKOLÁS ELLENŐRZÉS: Ellenőrizzük, hogy fut-e már inicializálás (__sik__ kulcs).
  const existingPromise = runningInitializations.get(SESSION_INIT_KEY);
  if (existingPromise) {
    //console.warn("[Cache Hit] A session inicializálás már folyamatban van. Várakozás a futó kérésre...");
    return existingPromise;
  }

  // 2. 💡 AZONOSÍTÓ KEZELÉSE A LOCALSTORAGE-BEN (cid kulcs)
  let clientStorageId = localStorage.getItem(CLIENT_STORAGE_ID_KEY);

  if (!clientStorageId) {
    // Ha még nincs mentve, generálunk egy újat és eltároljuk tartósan
    clientStorageId = window.crypto.randomUUID();
    localStorage.setItem(CLIENT_STORAGE_ID_KEY, clientStorageId);
    console.info(`[LocalStorage] Új tartós ID generálva és mentve a ${CLIENT_STORAGE_ID_KEY} kulcs alatt.`);
  }
  //else {
  //  console.info(`[LocalStorage] Meglévő tartós ID betöltve a ${CLIENT_STORAGE_ID_KEY} kulcs alól.`);
  //}

  // A szervernek küldendő azonosító az, amit a localStorage-ban tárolunk
  const idToSend = clientStorageId;

  // 3. KÉSZÍTÉS ÉS CACHELÉS
  const initializationPromise = (async () => {
    const maxRetries = 6; // Összesen kb. 30-40 másodperc (Render cold start ideje)
    const delayBetweenRetries = 5000; // 5 másodperc szünet két próbálkozás között

    for (let i = 0; i < maxRetries; i++) {
      try {
        const response = await callApiEndpoint<SessionInitResponse>(
            "/api/initialize_session",
            "POST",
            { clientId: idToSend, skip_auth: skipAuth }
        );

        if (response && response.token) {
          localStorage.setItem("jwtToken", response.token);
        }

        return response;
      } catch (error) {
        const isLastAttempt = i === maxRetries - 1;

        if (isLastAttempt) {
          console.error("Sikertelen ébresztés minden kísérlet után.");
          throw error;
        }

        console.warn(`Szerver ébresztése folyamatban... (${i + 1}/${maxRetries})`);
        // Várakozás a következő próbálkozás előtt
        await new Promise(resolve => setTimeout(resolve, delayBetweenRetries));
      }
    }
    throw new Error("Szerver nem válaszolt az ébresztési időn belül.");
  })();

  // 4. CACHELÉS ÉS VÉGLEGESÍTÉS
  runningInitializations.set(SESSION_INIT_KEY, initializationPromise);

  initializationPromise.finally(() => {
    runningInitializations.delete(SESSION_INIT_KEY);
  });

  // 5. Visszaadjuk a Promise-t.
  return initializationPromise;
}

export async function checkSessionAPI() {
  return await callApiEndpoint("/api/check_session", "POST", {});
}

export async function setAuth(
  email: string,
  username: string,
  password: string,
  isLogIn: boolean,
) {
  return await callApiEndpoint("/api/handle_auth", "POST", {
    email: email,
    username: username,
    password: password,
    is_login: isLogIn,
  });
}

export async function updateUsername(username: string) {
  return await callApiEndpoint(`/api/update_username`, "POST", {
    username: username,
  });
}

export async function handleConflictAPI(version_new: boolean) {
  return await callApiEndpoint(`/api/handle_conflict`, "POST", {
    version_new: version_new,
  });
}

export async function handleForgotPasswordAPI(identifier: string) {
  return await callApiEndpoint(`/api/forgot_password`, "POST", {
    identifier: identifier,
  });
}

export async function setForgotPasswordSubmit(token: string, password: string) {
  return await callApiEndpoint(`/api/reset_password/${token}`, "POST", {
    password: password,
  });
}

export async function clearGameState() {
  return await callApiEndpoint("/api/clear_game_state", "POST");
}

export async function recoverGameState() {
  return await callApiEndpoint("/api/recover_game_state", "POST");
}

export async function setBet(betAmount: number) {
  return  await callApiEndpoint("/api/bet", "POST", { bet: betAmount });
}

export async function takeBackDeal() {
  return await callApiEndpoint("/api/retake_bet", "POST");
}

export async function getShuffling() {
  return await callApiEndpoint("/api/create_deck", "POST");
}

export async function startGame() {
  return await callApiEndpoint("/api/start_game", "POST");
}

export async function handleHit() {
  return await callApiEndpoint("/api/hit", "POST");
}

export async function handleRewards() {
  return await callApiEndpoint("/api/rewards", "POST");
}

export async function handleInsurance() {
  return await callApiEndpoint("/api/ins_request", "POST");
}

export async function handleDouble() {
  return await callApiEndpoint("/api/double_request", "POST");
}

export async function handleStandAndRewards() {
  return await callApiEndpoint("/api/stand_and_rewards", "POST");
}

export async function handleSplitHand() {
  return await callApiEndpoint("/api/split_request", "POST");
}

export async function handleSplitHit() {
  return await callApiEndpoint("/api/split_hit", "POST");
}

export async function addSplitPlayerToGame() {
  return await callApiEndpoint("/api/add_split_player_to_game", "POST");
}

export async function addToPlayersListByStand() {
  return await callApiEndpoint(
    "/api/add_to_players_list_by_stand",
    "POST"
  );
}

export async function addPlayerFromPlayers() {
  return await callApiEndpoint("/api/add_player_from_players", "POST");
}

export async function handleSplitDouble() {
  return await callApiEndpoint("/api/split_double_request", "POST");
}

export async function handleSplitStandAndRewards() {
  return await callApiEndpoint(
    "/api/split_stand_and_rewards",
    "POST"
  );
}

export async function setRestart() {
  return await callApiEndpoint("/api/set_restart", "POST");
}

export async function forceRestart() {
  const clientUuid = localStorage.getItem(CLIENT_STORAGE_ID_KEY);
  if (!clientUuid) {
    throw new Error("No id.");
  }

  return await callApiEndpoint("/api/force_restart", "POST", {
    clientId: clientUuid,
  });
}

export interface HttpError extends Error {
  response?: {
    status: number;
    statusText: string;
    error?: string;
    data?: ErrorResponse; // A szerver válasza (pl. { error: 'No more split hands.' })
  };
}

export async function callApiEndpoint<T>(
  endpoint: string,
  method: string = "GET",
  body?: Record<string, unknown>,
): Promise<T> {
  const fullUrl = `${API_BASE_URL}${endpoint}`;

  /* if (import.meta.env.DEV) {
      console.log(`🚀 Request to: ${fullUrl} [${method}]`);
    } */

  const clientId = localStorage.getItem(CLIENT_STORAGE_ID_KEY);

  let finalBody = body;

  if (method.toUpperCase() === "POST") {
    const idempotencyKey = crypto.randomUUID();

    finalBody = {
      ...(body && typeof body === 'object' ? body : {}),
      clientId: clientId,
      idempotencyKey: idempotencyKey
    };
  }
  const jwtToken = localStorage.getItem("jwtToken");

  try {
    const options: RequestInit = {
      method: method,
      headers: {
        "Content-Type": "application/json",
        ...(clientId ? { "X-Client-Id": clientId } : {}),
        ...(jwtToken ? { "Authorization": `Bearer ${jwtToken}` } : {}),
      },
      body: finalBody ? JSON.stringify(finalBody) : undefined,
      credentials: 'omit',
    };

    const response = await fetch(fullUrl, options);

    if (response.ok || response.status === 204) {
      if (endpoint !== "/api/initialize_session") {
        sessionStorage.removeItem(`ik_${endpoint}`);
      }

      if (response.status === 204) {
        return {} as T;
      }

      return await response.json();
    } else {
      let errorData: ErrorResponse = {};
      try {
        errorData = await response.json();
      } catch {
        errorData = { message: "Ismeretlen API válasz formátum (nem JSON)." };
      }

      const status = response.status;
      const statusText = response.statusText || "Ismeretlen hiba";
      const errorMessage =
        errorData.message || `HTTP hiba! Státusz: ${status} ${statusText}.`;

      if (!(status === 400 && errorData.error === "No more split hands.")) {
        console.error(
          `API hiba a(z) '${endpoint}' végponton (státusz: ${status}):`,
          errorData
        );
      }
      const errorToThrow: HttpError = new Error(errorMessage);
      errorToThrow.response = {
        status: status,
        statusText: statusText,
        data: errorData,
      };
      throw errorToThrow;
    }
  } catch (error: unknown) {
    const isSpecificSplitHandError =
      error instanceof Error &&
      "response" in error &&
      (error as HttpError).response?.status === 400 &&
      (error as HttpError).response?.data?.error === "No more split hands.";

    if (!isSpecificSplitHandError) {
      console.error(
        `Hálózati vagy feldolgozási hiba a(z) '${endpoint}' végponton:`,
        error
      );
    }
    throw error;
  }
}
