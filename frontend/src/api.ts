import type { OperationRequest } from "./types";
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch("/api" + path, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });
  let data: unknown;
  try {
    data = await response.json();
  } catch {
    throw new ApiError(
      "RESPOSTA_INVALIDA",
      "O servidor retornou uma resposta inválida. Tente novamente.",
    );
  }
  if (!response.ok) {
    const error = data as { code?: string; message?: string };
    throw new ApiError(
      error.code ?? "FALHA",
      error.message ?? "Não foi possível concluir a solicitação.",
    );
  }
  return data as T;
}
export function pendingRequest(request: OperationRequest): {
  key: string;
  body: string;
} {
  const body = JSON.stringify({
    ...request,
    parcelas: [...request.parcelas].sort(),
  });
  // Session storage survives refresh/retry after an ambiguous network response.
  const stored = sessionStorage.getItem("elo-pending-request");
  if (stored) {
    try {
      const old = JSON.parse(stored);
      if (old.body === body && typeof old.key === "string") return old;
    } catch {
      /* Replace corrupt storage. */
    }
  }
  const pending = { key: crypto.randomUUID(), body };
  sessionStorage.setItem("elo-pending-request", JSON.stringify(pending));
  return pending;
}
export function clearPending() {
  sessionStorage.removeItem("elo-pending-request");
}
