# Review against the challenge PDF

Review scope: six functional flows, all non-functional requirements, Central contract, README delivery, Hexagonal Architecture, dependency scopes and scenario-to-test traceability. Earlier user instructions still apply: keep the standalone AI harness file removed; retain AI process documentation in README instead.

## Corrections and missing verification

- Validate null/duplicate/inconsistent agenda items and oversized rejection reasons at the HTTP adapter boundary.
- Recheck Central idempotency after acquiring installment locks and before eligibility validation, including a replay overtaken by another request's confirmation.
- Enforce domain-to-inner-only dependencies separately from the combined domain/application framework rule.
- Verify multi-installment confirmation, rejection of mixed eligibility and transactional rollback after an audit-write failure.
- Return only future, consented installments; verify cross-agreement isolation.
- Make operation audit snapshots immutable.
- Exercise UPDATE and DELETE audit protection using only the integration test's own operation, inside rollback-only SQL transactions.
- Tighten result matching in the acceptance checker; do not accept another method by suffix.
- Normalize malformed frontend error bodies.
- Review actual dependencies and production JAR scopes; refresh deprecated CI actions from official releases.

AC-052 through AC-067 provide distinct executable tests for the added criteria. Existing AC-021 and AC-047 are strengthened without changing their identities. Existing architecture, consent, idempotency, retry, rejection and persistence behaviors remain required.

Also reject invalid authorization identifiers and retry agenda refresh after transient failures following terminal operation states.
