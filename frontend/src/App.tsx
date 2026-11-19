import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Check,
  ChevronRight,
  CircleHelp,
  FileCheck2,
  Layers3,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
  Wallet,
  X,
} from "lucide-react";
import { api, pendingRequest, clearPending } from "./api";
import type { Installment, Operation, Scenario } from "./types";
import { date, money, statusLabel } from "./types";
import OperationPanel from "./OperationPanel";
const scenarioLabels: Record<Scenario, string> = {
  NORMAL: "Confirmação normal",
  REJECT: "Rejeição pela Central",
  UNAVAILABLE: "Indisponibilidade temporária",
  TIMEOUT: "Timeout na comunicação",
  TIMEOUT_AFTER_ACCEPTING: "Timeout depois do aceite",
  INVALID_RESPONSE: "Resposta inesperada",
  TAMPERED_RESPONSE: "Assinatura inválida",
};
export default function App() {
  const [beneficiary, setBeneficiary] = useState("BEN-001"),
    [agreement, setAgreement] = useState("CONV-001"),
    [consent, setConsent] = useState(false);
  const [authorized, setAuthorized] = useState<string | null>(null),
    [agenda, setAgenda] = useState<Installment[]>([]),
    [selected, setSelected] = useState<string[]>([]);
  const [operations, setOperations] = useState<Operation[]>([]),
    [activeId, setActiveId] = useState<string | null>(null);
  const [account, setAccount] = useState({ banco: "", agencia: "", conta: "" }),
    [creditor, setCreditor] = useState("Payroll Finance");
  const [scenario, setScenario] = useState<Scenario>("NORMAL"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [connected, setConnected] = useState(true);
  const terminalSnapshot = useRef("");
  const refreshAgenda = useCallback(
    async (id: string, signal?: AbortSignal) => {
      const data = await api<Installment[]>(
        "/beneficiarios/" + encodeURIComponent(id) + "/agenda",
        { signal },
      );
      setAgenda(data);
      setSelected((old) =>
        old.filter((p) => data.some((i) => i.parcelaId === p && i.elegivel)),
      );
    },
    [],
  );
  const refresh = useCallback(
    async (signal?: AbortSignal) => {
      const data = await api<Operation[]>("/operacoes", { signal });
      setOperations(data);
      setConnected(true);
      const snapshot = data
        .filter((o) => ["CONFIRMADA", "REJEITADA"].includes(o.status))
        .map((o) => o.id + o.status)
        .join();
      if (snapshot !== terminalSnapshot.current) {
        terminalSnapshot.current = snapshot;
        if (authorized) await refreshAgenda(authorized, signal);
      }
    },
    [authorized, refreshAgenda],
  );
  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const controller = new AbortController();
    async function poll() {
      try {
        if (!document.hidden && !cancelled) await refresh(controller.signal);
      } catch {
        if (!cancelled) setConnected(false);
      } finally {
        if (!cancelled) timer = setTimeout(poll, 2000);
      }
    }
    void poll();
    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, [refresh]);
  async function authorize(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api("/autorizacoes", {
        method: "POST",
        body: JSON.stringify({
          beneficiarioId: beneficiary,
          convenioId: agreement,
          consentimento: consent,
        }),
      });
      setAuthorized(beneficiary);
      await refreshAgenda(beneficiary);
      setNotice(
        "Autorização registrada. Sua agenda está pronta para consulta.",
      );
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Falha ao registrar autorização.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!authorized || selected.length === 0) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const request = pendingRequest({
        beneficiarioId: authorized,
        parcelas: selected,
        credorCessionario: creditor,
        contaRecebimento: account,
        cenario: scenario,
      });
      const op = await api<Operation>("/operacoes", {
        method: "POST",
        headers: { "Idempotency-Key": request.key },
        body: request.body,
      });
      clearPending();
      setActiveId(op.id);
      setSelected([]);
      setNotice(
        "Operação registrada. Você pode acompanhar a confirmação abaixo.",
      );
      await refresh();
      await refreshAgenda(authorized);
      document
        .getElementById("operacoes")
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Falha ao enviar. Tente novamente; a mesma solicitação não será duplicada.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function resume(id: string) {
    setBusy(true);
    setError("");
    try {
      await api("/operacoes/" + id + "/retomar", { method: "POST" });
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao retomar.");
    } finally {
      setBusy(false);
    }
  }
  const total = agenda
    .filter((i) => selected.includes(i.parcelaId))
    .reduce((sum, i) => sum + i.valor, 0);
  const confirmed = operations.filter((o) => o.status === "CONFIRMADA").length,
    pending = operations.filter((o) =>
      ["RECEBIDA", "PENDENTE"].includes(o.status),
    ).length;
  const eligible = agenda.filter((p) => p.elegivel);
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a href="#" className="brand" aria-label="Payroll Portability início">
          <span className="brand-symbol">P</span>
          <span className="brand-name">Payroll<span>Portability</span></span>
        </a>
        <p className="workspace-label">PORTABILIDADE DE CONVÊNIOS</p>
        <nav aria-label="Navegação principal">
          <a className="nav-active" href="#visao-geral">
            <Layers3 size={19} />
            Visão geral
            <ChevronRight size={15} />
          </a>
          <a href="#nova-operacao">
            <ArrowDownLeft size={19} />
            Nova portabilidade
          </a>
          <a href="#operacoes">
            <FileCheck2 size={19} />
            Operações<span className="nav-count">{operations.length}</span>
          </a>
        </nav>
        <div className="sidebar-bottom">
          <div className="secure-card">
            <ShieldCheck size={23} />
            <strong>Confiança em cada etapa</strong>
            <p>Operações assinadas e um histórico que permanece.</p>
          </div>
          <div className="user-profile">
            <span>PZ</span>
            <div>
              <strong>Ambiente de avaliação</strong>
              <small>Dados fictícios · Simulação</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} />
            <strong>Portabilidade</strong>
          </div>
          <div className="topbar-right">
            <span className="demo-pill">DEMONSTRAÇÃO</span>
            <a href="#como-funciona" aria-label="Como funciona">
              <CircleHelp size={19} />
            </a>
          </div>
        </header>
        <main id="visao-geral">
          <div className="page-heading">
            <div>
              <p className="eyebrow">CONEXÕES QUE MOVEM O FUTURO</p>
              <h1>Portabilidade, com clareza.</h1>
              <p>
                Uma nova conta de recebimento. O mesmo cuidado em cada etapa.
              </p>
            </div>
            <span className={"connection " + (!connected ? "offline" : "")}>
              <i />
              {connected ? "Plataforma conectada" : "Reconectando à plataforma"}
            </span>
          </div>
          <div className="stats-grid">
            <div className="stat-card">
              <div>
                <p>Operações registradas</p>
                <strong>{String(operations.length).padStart(2, "0")}</strong>
                <span>Histórico completo de solicitações</span>
              </div>
              <span className="stat-icon">
                <Layers3 size={22} />
              </span>
            </div>
            <div className="stat-card">
              <div>
                <p>Em processamento</p>
                <strong>{String(pending).padStart(2, "0")}</strong>
                <span>Acompanhamento junto à Central</span>
              </div>
              <span className="stat-icon amber">
                <RefreshCw size={22} />
              </span>
            </div>
            <div className="stat-card">
              <div>
                <p>Portabilidades confirmadas</p>
                <strong>{String(confirmed).padStart(2, "0")}</strong>
                <span>Conta de recebimento atualizada</span>
              </div>
              <span className="stat-icon lime">
                <Check size={24} />
              </span>
            </div>
          </div>
          <section className="journey-banner" id="como-funciona">
            <div>
              <span className="small-label">
                DO CONSENTIMENTO À CONFIRMAÇÃO
              </span>
              <h2>
                Você escolhe.
                <br /> A gente conecta.
              </h2>
            </div>
            <div className="journey-steps">
              {[
                ["01", "Autorize", "Consulta com consentimento"],
                ["02", "Selecione", "Parcelas e conta de destino"],
                ["03", "Acompanhe", "Confirmação e trilha completa"],
              ].map(([n, title, subtitle], index) => (
                <div className="journey-step" key={n}>
                  <span>{n}</span>
                  <div>
                    <strong>{title}</strong>
                    <p>{subtitle}</p>
                  </div>
                  {index < 2 && <ArrowRight size={17} />}
                </div>
              ))}
            </div>
            <div className="banner-rings" aria-hidden="true" />
          </section>
          {error && (
            <div className="notice danger" role="alert">
              <span>{error}</span>
              <button aria-label="Fechar erro" onClick={() => setError("")}>
                <X size={17} />
              </button>
            </div>
          )}
          {notice && (
            <div className="notice success" role="status">
              <Check size={18} />
              {notice}
              <button aria-label="Fechar aviso" onClick={() => setNotice("")}>
                <X size={17} />
              </button>
            </div>
          )}
          <section id="nova-operacao" aria-labelledby="new-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">UM NOVO COMEÇO</p>
                <h2 id="new-title">Nova portabilidade</h2>
              </div>
              <span className="section-note">
                <ShieldCheck size={15} />
                Consulta mediante autorização
              </span>
            </div>
            <div className="new-layout">
              <div className="flow-panel">
                <div className="panel-title">
                  <span className="step-number">1</span>
                  <div>
                    <h3>Identifique e autorize</h3>
                    <p>O beneficiário está no controle da consulta.</p>
                  </div>
                  {authorized && (
                    <span className="mini-check">
                      <Check size={17} />
                    </span>
                  )}
                </div>
                <form onSubmit={authorize} className="authorization-form">
                  <div className="two-fields">
                    <label>
                      Identificador do beneficiário
                      <input
                        required
                        pattern="[a-zA-Z0-9_-]{1,80}"
                        value={beneficiary}
                        onChange={(e) => {
                          setBeneficiary(e.target.value);
                          setAuthorized(null);
                          setAgenda([]);
                          setSelected([]);
                        }}
                        placeholder="BEN-001"
                      />
                    </label>
                    <label>
                      Convênio de origem
                      <input
                        required
                        pattern="[a-zA-Z0-9_-]{1,80}"
                        value={agreement}
                        onChange={(e) => {
                          setAgreement(e.target.value);
                          setAuthorized(null);
                          setAgenda([]);
                          setSelected([]);
                        }}
                        placeholder="CONV-001"
                      />
                    </label>
                  </div>
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={consent}
                      onChange={(e) => setConsent(e.target.checked)}
                      required
                    />
                    <span>
                      Autorizo a consulta da minha agenda de descontos para
                      avaliar a portabilidade deste convênio.
                    </span>
                  </label>
                  <button
                    className="button secondary"
                    disabled={busy || !consent}
                    type="submit"
                  >
                    {busy ? (
                      <LoaderCircle className="spin" size={16} />
                    ) : (
                      <ShieldCheck size={16} />
                    )}
                    Autorizar e consultar
                    <ArrowUpRight size={16} />
                  </button>
                </form>
                <div className="agenda-header">
                  <div>
                    <span className="step-number">2</span>
                    <h3>Selecione as parcelas</h3>
                  </div>
                  {authorized && (
                    <button
                      className="icon-button"
                      aria-label="Atualizar agenda"
                      disabled={busy}
                      onClick={() =>
                        void refreshAgenda(authorized).catch((e) =>
                          setError(e.message),
                        )
                      }
                    >
                      <RefreshCw size={16} />
                    </button>
                  )}
                </div>
                {authorized ? (
                  <>
                    <div className="table-scroll">
                      <table>
                        <thead>
                          <tr>
                            <th>
                              <input
                                aria-label="Selecionar todas as elegíveis"
                                type="checkbox"
                                disabled={eligible.length === 0}
                                checked={
                                  eligible.length > 0 &&
                                  eligible.every((p) =>
                                    selected.includes(p.parcelaId),
                                  )
                                }
                                onChange={(e) =>
                                  setSelected(
                                    e.target.checked
                                      ? eligible.map((p) => p.parcelaId)
                                      : [],
                                  )
                                }
                              />
                            </th>
                            <th>DESCONTO</th>
                            <th>VALOR</th>
                            <th>STATUS</th>
                          </tr>
                        </thead>
                        <tbody>
                          {agenda.map((p) => (
                            <tr
                              key={p.parcelaId}
                              className={
                                selected.includes(p.parcelaId) ? "selected" : ""
                              }
                            >
                              <td>
                                <input
                                  aria-label={
                                    "Selecionar parcela " + p.parcelaId
                                  }
                                  type="checkbox"
                                  disabled={!p.elegivel || busy}
                                  checked={selected.includes(p.parcelaId)}
                                  onChange={(e) =>
                                    setSelected((old) =>
                                      e.target.checked
                                        ? [...old, p.parcelaId]
                                        : old.filter(
                                            (id) => id !== p.parcelaId,
                                          ),
                                    )
                                  }
                                />
                              </td>
                              <td>
                                <strong>{date(p.dataDesconto)}</strong>
                                <small>{p.convenioId}</small>
                                {p.contaRecebimento && (
                                  <small className="green">
                                    Recebimento: {p.contaRecebimento.banco} /{" "}
                                    {p.contaRecebimento.agencia} /{" "}
                                    {p.contaRecebimento.conta}
                                  </small>
                                )}
                              </td>
                              <td className="amount">{money(p.valor)}</td>
                              <td>
                                <span className={"badge " + p.status}>
                                  {p.status === "ABERTA" && !p.elegivel
                                    ? "Reservada"
                                    : statusLabel[p.status]}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <p className="table-note">
                      Somente parcelas futuras, em aberto e disponíveis podem
                      ser selecionadas.
                    </p>
                  </>
                ) : (
                  <div className="agenda-empty">
                    <FileCheck2 size={28} />
                    <p>Sua agenda começa com uma autorização.</p>
                    <small>
                      Registre o opt-in acima para visualizar as parcelas.
                    </small>
                  </div>
                )}
              </div>
              <form className="destination-panel" onSubmit={submit}>
                <div className="panel-title">
                  <span className="step-number">3</span>
                  <div>
                    <h3>Defina o destino</h3>
                    <p>A conta muda após a confirmação.</p>
                  </div>
                </div>
                <label>
                  Novo credor
                  <input
                    required
                    maxLength={80}
                    value={creditor}
                    onChange={(e) => setCreditor(e.target.value)}
                  />
                </label>
                <div className="account-title">
                  <Wallet size={17} />
                  Conta de recebimento
                </div>
                <label>
                  Código do banco
                  <input
                    required
                    inputMode="numeric"
                    pattern="[0-9]{3}"
                    maxLength={3}
                    placeholder="Ex.: 001"
                    value={account.banco}
                    onChange={(e) =>
                      setAccount({ ...account, banco: e.target.value })
                    }
                  />
                </label>
                <div className="two-fields">
                  <label>
                    Agência
                    <input
                      required
                      pattern="[0-9]{1,5}(-[0-9Xx])?"
                      placeholder="1234"
                      value={account.agencia}
                      onChange={(e) =>
                        setAccount({ ...account, agencia: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Conta com dígito
                    <input
                      required
                      pattern="[0-9]{1,12}(-[0-9Xx])?"
                      placeholder="12345-6"
                      value={account.conta}
                      onChange={(e) =>
                        setAccount({ ...account, conta: e.target.value })
                      }
                    />
                  </label>
                </div>
                <details className="simulation">
                  <summary>Cenário da demonstração</summary>
                  <label>
                    Comportamento da Central
                    <select
                      value={scenario}
                      onChange={(e) => setScenario(e.target.value as Scenario)}
                    >
                      {Object.entries(scenarioLabels).map(([v, label]) => (
                        <option key={v} value={v}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p>
                    Falhas transitórias ocorrem nas primeiras duas tentativas;
                    timeout após aceite ocorre na primeira.
                  </p>
                </details>
                <div className="selection-summary">
                  <span>
                    {selected.length} parcela{selected.length !== 1 ? "s" : ""}{" "}
                    selecionada{selected.length !== 1 ? "s" : ""}
                  </span>
                  <strong>{money(total)}</strong>
                </div>
                <button
                  type="submit"
                  className="button primary"
                  disabled={!authorized || selected.length === 0 || busy}
                >
                  {busy ? (
                    <LoaderCircle className="spin" size={17} />
                  ) : (
                    <>
                      Solicitar portabilidade
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>
                <p className="destination-note">
                  <ShieldCheck size={15} />
                  Sua solicitação é protegida contra duplicidade e acompanhada
                  até a confirmação.
                </p>
              </form>
            </div>
          </section>
          <OperationPanel
            operations={operations}
            activeId={activeId}
            onSelect={setActiveId}
            onResume={resume}
            busy={busy}
          />
          <footer>
            <span className="footer-brand">Payroll Portability</span>
            <p>Conectando convênios. Simplificando caminhos.</p>
            <span>Projeto demonstrativo · React + Java</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
