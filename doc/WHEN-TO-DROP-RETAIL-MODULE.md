# When to remove the Web POS module dependency

`AD_MODULE_DEPENDENCY` on **Web POS** (`org.openbravo.retail.posterminal`) stays **required** while any of the following are true:

- Java code in `com.doceleguas.pos.webservices` extends or references `org.openbravo.retail.posterminal.*`
  (for example login/terminal, cashup, product/tax helper services).

## Current status (2026-04)

- `SaveOrder` / `OCOrder` already run through native pipeline:
  - `SaveOrder` accepts native v2 payload (`messageId`, `posTerminal`, `order`).
  - `OcreOrderLoadOrchestrator` uses native normalization + hooks + `CoreOrderPersistenceAdapter`.
  - No runtime delegation to `ExternalOrderLoader` / `OrderLoader` in OCOrder flow.
- Web POS dependency is still required by **other** webservices in this module.

## Checklist before deleting the dependency row

1. Keep `SaveOrder`/`OCOrder` regression green in `native` mode with golden payloads and SQL parity checks (`doc/regression-suite-OCOrder.md`).
2. Remove remaining `org.openbravo.retail.posterminal.*` usage across non-order verticals (login, terminal, cashup, product/tax/discount utilities).
3. Verify no compile/runtime references to Web POS classes remain in `com.doceleguas.pos.webservices`.
4. Validate module install/startup and end-to-end OCRE flows with Web POS removed from dependency graph.
5. Only then remove the `AD_MODULE_DEPENDENCY` row.

Until then, dependency on Web POS remains intentional technical debt for non-order services.
