import {
  ArrowUpRight,
  Check,
  Clock3,
  RefreshCw,
  ShieldCheck,
  X,
  AlertCircle,
} from "lucide-react";
import type { Operation } from "./types";
import { money, time, statusLabel } from "./types";
type Props = {
  operations: Operation[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onResume: (id: string) => void;
  busy: boolean;
};
export default function OperationPanel({
  operations,
  activeId,
  onSelect,
  onResume,
  busy,
}: Props) {
  const active = operations.find((o) => o.id === activeId) ?? operations[0];
  return (
    <section
      className="operations-section"
      id="operacoes"
      aria-labelledby="operations-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">ACOMPANHAMENTO</p>
          <h2 id="operations-title">Cada etapa, registrada.</h2>
        </div>
        <span className="live-dot">Atualização automática</span>
      </div>
      <div className="operations-layout">
        <div className="operation-list">
          {operations.length === 0 ? (
            <div className="empty">
              <Clock3 size={30} />
              <h3>Sua primeira operação começa aqui</h3>
              <p>
                Autorize a consulta, escolha as parcelas e acompanhe o caminho
                até a confirmação.
              </p>
            </div>
          ) : (
            operations.map((op) => (
              <button
                key={op.id}
                className={
                  "operation-card " + (active?.id === op.id ? "active" : "")
                }
                onClick={() => onSelect(op.id)}
                aria-pressed={active?.id === op.id}
              >
                <div className="row">
                  <span className={"badge " + op.status}>
                    {statusLabel[op.status]}
                  </span>
                  <ArrowUpRight size={17} />
                </div>
                <strong>
                  {op.solicitacao.parcelas.length} parcela
                  {op.solicitacao.parcelas.length > 1 ? "s" : ""} ·{" "}
                  {op.beneficiarioId}
                </strong>
                <span className="muted">
                  {time(op.criadaEm)} · #{op.id.slice(0, 8)}
                </span>
              </button>
            ))
          )}
        </div>
        <div className="event-panel">
          {active ? (
            <>
              <div className="row">
                <div>
                  <p className="eyebrow">TRILHA DE AUDITORIA</p>
                  <h3>Operação #{active.id.slice(0, 8)}</h3>
                </div>
                <ShieldCheck className="green" size={26} />
              </div>
              <div className="operation-summary">
                <div>
                  <span>Conta de destino</span>
                  <strong>
                    {active.solicitacao.contaRecebimento.banco} · Ag.{" "}
                    {active.solicitacao.contaRecebimento.agencia} ·{" "}
                    {active.solicitacao.contaRecebimento.conta}
                  </strong>
                </div>
                <div>
                  <span>Protocolo da Central</span>
                  <code>{active.protocolo ?? "Aguardando envio"}</code>
                </div>
              </div>
              {active.ultimoErro && (
                <div
                  className={
                    "notice " +
                    (active.status === "REJEITADA" ? "danger" : "warning")
                  }
                  role="status"
                >
                  <AlertCircle size={19} />
                  <div>
                    {active.ultimoErro}
                    {["RECEBIDA", "PENDENTE"].includes(active.status) && (
                      <small>
                        Próxima tentativa: {time(active.proximaTentativa)}
                      </small>
                    )}
                  </div>
                </div>
              )}
              {active.status === "REVISAO_NECESSARIA" && (
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => onResume(active.id)}
                >
                  <RefreshCw size={16} />
                  Retomar com segurança
                </button>
              )}
              <ol className="timeline">
                {active.eventos.map((event) => (
                  <li key={event.sequence}>
                    <span
                      className={
                        "event-dot " + (event.tipo === "REJEITADA" ? "red" : "")
                      }
                    >
                      {event.tipo === "CONFIRMADA" ||
                      event.tipo === "CONTA_ATUALIZADA" ? (
                        <Check size={12} />
                      ) : event.tipo === "REJEITADA" ? (
                        <X size={12} />
                      ) : (
                        <span />
                      )}
                    </span>
                    <div>
                      <div className="row">
                        <strong>
                          {event.tipo
                            .replaceAll("_", " ")
                            .toLocaleLowerCase("pt-BR")}
                        </strong>
                        <time dateTime={event.quando}>
                          {time(event.quando)}
                        </time>
                      </div>
                      <p>{event.detalhe}</p>
                    </div>
                  </li>
                ))}
              </ol>
              <p className="audit-note">
                <ShieldCheck size={14} /> Histórico permanente · Eventos
                registrados em UTC
              </p>
            </>
          ) : (
            <div className="empty audit-empty">
              <ShieldCheck size={36} />
              <h3>Transparência do início ao fim</h3>
              <p>
                O protocolo, os retornos da Central e cada mudança de estado
                aparecerão aqui.
              </p>
              <span className="small-label">
                ASSINATURA HMAC · HISTÓRICO AUDITÁVEL
              </span>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
