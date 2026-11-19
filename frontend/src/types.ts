export type Account = { banco: string; agencia: string; conta: string };
export type Installment = {
  parcelaId: string;
  convenioId: string;
  valor: number;
  dataDesconto: string;
  status: string;
  elegivel: boolean;
  contaRecebimento: Account | null;
};
export type Scenario =
  | "NORMAL"
  | "REJECT"
  | "UNAVAILABLE"
  | "TIMEOUT"
  | "TIMEOUT_AFTER_ACCEPTING"
  | "INVALID_RESPONSE"
  | "TAMPERED_RESPONSE";
export type OperationRequest = {
  beneficiarioId: string;
  parcelas: string[];
  credorCessionario: string;
  contaRecebimento: Account;
  cenario: Scenario;
};
export type Operation = {
  id: string;
  beneficiarioId: string;
  status: string;
  protocolo: string | null;
  tentativas: number;
  proximaTentativa: string;
  ultimoErro: string | null;
  criadaEm: string;
  solicitacao: OperationRequest;
  eventos: {
    sequence: number;
    quando: string;
    tipo: string;
    detalhe: string;
  }[];
};
export const money = (value: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    value,
  );
export const date = (value: string) =>
  new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(value + "T12:00:00"));
export const time = (value: string) =>
  new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
export const statusLabel: Record<string, string> = {
  RECEBIDA: "Recebida",
  PENDENTE: "Em processamento",
  CONFIRMADA: "Confirmada",
  REJEITADA: "Rejeitada",
  REVISAO_NECESSARIA: "Revisão necessária",
  ABERTA: "Em aberto",
  PAGA: "Paga",
  BLOQUEADA: "Bloqueada",
  PORTADA: "Portada",
};
