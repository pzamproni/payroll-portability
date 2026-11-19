import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, pendingRequest, clearPending } from "./api";
import type { OperationRequest } from "./types";
const request: OperationRequest = {
  beneficiarioId: "BEN-001",
  parcelas: ["b", "a"],
  credorCessionario: "Payroll Finance",
  contaRecebimento: { banco: "001", agencia: "1", conta: "2-3" },
  cenario: "NORMAL",
};
beforeEach(() => sessionStorage.clear());
describe("idempotência no navegador", () => {
  it("preserva a chave após resposta ambígua e recarregamento", () => {
    const first = pendingRequest(request);
    expect(pendingRequest({ ...request, parcelas: ["a", "b"] })).toEqual(first);
    expect(JSON.parse(sessionStorage.getItem("elo-pending-request")!).key).toBe(
      first.key,
    );
  });
  it("gera nova chave para conteúdo diferente ou solicitação concluída", () => {
    const first = pendingRequest(request);
    expect(pendingRequest({ ...request, cenario: "REJECT" }).key).not.toBe(
      first.key,
    );
    clearPending();
    expect(pendingRequest(request).key).not.toBe(first.key);
  });
  it("expõe erro de integração sem silenciar a mensagem", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({
          ok: false,
          json: async () => ({
            code: "FALHA_CENTRAL",
            message: "Central indisponível",
          }),
        }),
    );
    await expect(api("/operacoes")).rejects.toThrow("Central indisponível");
  });
});

it("normaliza erros sem objeto JSON válido", async () => {
  for (const payload of [null, [], 42, "erro", { code: 7, message: { detail: "invalid" } }]) {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, json: async () => payload }));
    await expect(api("/operacoes")).rejects.toMatchObject({
      code: "FALHA",
      message: "Não foi possível concluir a solicitação.",
    });
  }
});
