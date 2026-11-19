import { afterEach, expect, it, vi } from "vitest";
import { render, screen, cleanup, act, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import type { Operation, Installment } from "./types";
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  vi.restoreAllMocks();
});
it("exige consentimento explícito e mostra a agenda somente após autorização", async () => {
  const user = userEvent.setup();
  const fetch = vi
    .fn()
    .mockImplementation(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/operacoes")
          ? []
          : url.endsWith("/agenda")
            ? []
            : { id: "auth" },
    }));
  vi.stubGlobal("fetch", fetch);
  render(<App />);
  const authorize = screen.getByRole("button", {
    name: /Autorizar e consultar/,
  });
  expect(authorize).toBeDisabled();
  expect(
    screen.getByText("Sua agenda começa com uma autorização."),
  ).toBeInTheDocument();
  await user.click(
    screen.getByRole("checkbox", { name: /Autorizo a consulta/ }),
  );
  expect(authorize).toBeEnabled();
  await user.click(authorize);
  expect(
    await screen.findByText(
      "Autorização registrada. Sua agenda está pronta para consulta.",
    ),
  ).toBeInTheDocument();
  const call = fetch.mock.calls.find(([url]) => url === "/api/autorizacoes");
  expect(JSON.parse(call![1].body).consentimento).toBe(true);
});

it("repete atualização da agenda após falha transitória na confirmação", async () => {
  vi.useFakeTimers();
  vi.spyOn(document, "hidden", "get").mockReturnValue(false);
  const installment: Installment = {
    parcelaId: "p1",
    convenioId: "CONV-001",
    valor: 100,
    dataDesconto: "2030-01-01",
    status: "ABERTA",
    elegivel: true,
    contaRecebimento: null,
  };
  const operation: Operation = {
    id: "op",
    beneficiarioId: "BEN-001",
    status: "CONFIRMADA",
    protocolo: "protocol",
    tentativas: 0,
    proximaTentativa: "2030-01-01T00:00:00Z",
    ultimoErro: null,
    criadaEm: "2030-01-01T00:00:00Z",
    eventos: [],
    solicitacao: {
      beneficiarioId: "BEN-001",
      parcelas: ["p1"],
      credorCessionario: "Payroll Finance",
      contaRecebimento: { banco: "001", agencia: "1234", conta: "5678-9" },
      cenario: "NORMAL",
    },
  };
  let confirmed = false;
  let agendaCalls = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith("/agenda")) {
        agendaCalls++;
        if (agendaCalls === 2)
          return { ok: false, json: async () => ({ message: "Indisponível" }) };
        return {
          ok: true,
          json: async () => [{
            ...installment,
            ...(confirmed ? {
              status: "PORTADA",
              elegivel: false,
              contaRecebimento: operation.solicitacao.contaRecebimento,
            } : {}),
          }],
        };
      }
      return {
        ok: true,
        json: async () => url.endsWith("/operacoes")
          ? (confirmed ? [operation] : [])
          : { id: "auth" },
      };
    }),
  );
  render(<App />);
  await act(async () => {});
  await act(async () => {
    fireEvent.click(screen.getByRole("checkbox", { name: /Autorizo a consulta/ }));
  });
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: /Autorizar e consultar/ }));
  });
  await act(async () => {});
  expect(agendaCalls).toBe(1);
  confirmed = true;
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2001);
  });
  expect(agendaCalls).toBe(2);
  expect(screen.getByText("Reconectando à plataforma")).toBeInTheDocument();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2001);
  });
  expect(agendaCalls).toBe(3);
  expect(screen.getByText("Recebimento: 001 / 1234 / 5678-9")).toBeInTheDocument();
});
