import { afterEach, expect, it, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
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
