# SaveOrder webservice and OCOrder import queue

## Endpoint

- **URL:** `POST /ws/com.doceleguas.pos.webservices.SaveOrder`
- **Content-Type:** `application/json`
- **Body:** Native SaveOrder v2 payload (OrderLoader-oriented):
  - `messageId` (string, required) — used as `C_IMPORT_ENTRY` primary key for idempotency
  - `posTerminal` (string, optional but recommended)
  - `order` (object, required) — single order payload in native shape

## HTTP response (enqueue)

- **202 Accepted** when a new row is queued:
  - `status`: `"accepted"`
  - `requestId`: correlation id for logs
  - `importEntryId`: same as `messageId`
  - `receivedAt`: epoch millis
  - `duplicate`: `false`
- **202 Accepted** when the same `messageId` was already submitted:
  - `duplicate`: `true`
  - `importStatus`: current `C_IMPORT_ENTRY.IMPORTSTATUS` when available
- **400** for invalid JSON or missing `messageId` / `order`

Processing is **asynchronous**: success of the HTTP call means the row was queued, not that the order was persisted. Final outcome is in `C_IMPORT_ENTRY` (`Processed` / `Error`, `RESPONSEINFO`, `ERRORINFO`).

## Backend processing

- Type of data: **`OCOrder`** (`AD_REF_LIST` on reference *Type of Import Data*).
- Processor: `com.doceleguas.pos.webservices.orders.loader.OCOrderImportEntryProcessor` + `OCOrderImportRunnable`.
- Implementation: **`com.doceleguas.pos.webservices.orderload.OcreOrderLoadOrchestrator`** — normalizes native request (`messageId`, `posTerminal`, `order`) into internal queue payload (`data[]`), runs Doceleguas pre-load hooks, then persists using native Core/DAL adapter (`CoreOrderPersistenceAdapter`), without delegating to retail `ExternalOrderLoader` / `OrderLoader`.

## Native pipeline summary

| Step | Component | Role |
|------|-----------|------|
| 1 | `ExternalEnvelopeTransform` | Normalize native `order` request to queue internal shape (`data[]`). |
| 2 | `OcreOrderPreLoadHook` | Doceleguas pre-persistence adjustments per order. |
| 3 | `CoreOrderPersistenceAdapter` | Persist order, lines and payments using Core/DAL services. |

Source: `com.doceleguas.pos.webservices.orderload.*` native pipeline.

## Where the OrderLoader logic lives now

The logic that replaced the runtime role of retail `OrderLoader` in the OCOrder pipeline is implemented in:

- `src/com/doceleguas/pos/webservices/orderload/impl/CoreOrderPersistenceAdapter.java`

This class is the native persistence engine and currently handles the supported sale flow end-to-end:

- validates supported scenario (`create` standard sale only at this stage)
- resolves terminal context from `posTerminal` (`OBPOS_APPLICATIONS`)
- creates/reuses `C_Order` header
- creates `C_ORDERLINE` rows from payload lines
- resolves tax and assigns `C_TAX` on lines
- resolves payment method/account and creates `FIN_Payment` rows
- returns pipeline result JSON used by import framework

### Invocation path

`CoreOrderPersistenceAdapter` is invoked through this chain:

1. `SaveOrder.doPost` receives native request (`messageId`, `posTerminal`, `order`) and enqueues `C_IMPORT_ENTRY` (`OCOrder`).
2. `OCOrderImportEntryProcessor` selects `OCOrderImportRunnable` for queue processing.
3. `OCOrderImportRunnable.processEntry` reads `jsonInfo` and calls `OcreOrderLoadOrchestrator.importEnvelope(...)`.
4. `OcreOrderLoadOrchestrator`:
   - normalizes payload with `ExternalEnvelopeTransform` (`order` -> `data[]`)
   - executes `OcreOrderPreLoadHook` hooks
   - delegates persistence to `CoreOrderPersistenceAdapter.persistTransformedEnvelope(...)`
5. Import entry is marked `Processed` or `Error` according to returned `RESPONSE_STATUS`.

## Operational notes

- **Sincronía vs cola:** OCRE-POS previously used `synchronizedProcessing=true` on JSON REST so the client waited for completion. With `SaveOrder`, the client receives **202** immediately; align UX (e.g. rely on `GetOrder` / `GetOrdersFilter`, or add a status endpoint) if needed.
- **Idempotency:** Reusing `messageId` returns `duplicate: true` and does not enqueue a second row.

## Database / module install

- Apply module DB changes so `OCOrder` exists in *Type of Import Data*.
- OCOrder itself no longer delegates at runtime to retail order loaders; module dependency remains for other services until `doc/WHEN-TO-DROP-RETAIL-MODULE.md` checklist is satisfied.
