# Audit: ExternalOrderLoader → OrderLoader (reference for OCOrder parity)

This note captures the legacy chain used to validate `OCOrderImportEntryProcessor` behaviour. Primary source: `ExternalOrderLoader.java` in `org.openbravo.retail.posterminal`.

## Public entry suitable for server-side reuse

- **`ExternalOrderLoader.importOrder(JSONObject messageIn)`**
  - Sets `runInSynchronizedMode(true)`.
  - Calls `transformMessage(messageIn)` then `exec(transformed)` (OrderLoader path).
  - On exception, returns JSON with `status` = failure and `message`.

## Transform (external → internal)

- **`transformMessage`**
  - Requires `channel` = `"External"` for full transform; otherwise returns input unchanged.
  - Sets `channel` to `"FromExternal"`, `appName` to `External`, resolves `posTerminal` search key to terminal id, sets `pos`, `client`/`organization` context fields.
  - Ensures `messageId` exists (generates UUID if missing).
  - Iterates `data[]`: per-order `transformOrder`, handles cancel / cancel_replace steps, etc.

## Synchronized HTTP path (JSON REST)

- **`executeCreateImportEntry` / `isSynchronizedRequest()`**
  - When `synchronizedProcessing=true`, creates/updates import tracking with qualifier `OBPOS_ExternalOrder` and calls `super.exec(w, message)` on the **transformed** message.

## OCQueue design choice

- `SaveOrder` stores the **original** OCRE envelope in `JSONINFO`.
- `OCOrderImportRunnable` calls **`OcreOrderLoadOrchestrator#importEnvelope`**, which splits **transform** (`LegacyRetailOrderPipelineAdapter.transformInbound`) and **exec** (`RetailOrderPersistenceAdapter` → `executeLoaded`) and runs **Doceleguas `OcreOrderPreLoadHook`** between them (same timing as the old `OrderLoaderPreProcessHook` for invoice handling).

## Hooks

- Doceleguas hooks implement **`com.doceleguas.pos.webservices.orderload.spi.OcreOrderPreLoadHook`** (no `org.openbravo.retail.*` hook interfaces). Retail `OrderLoaderPreProcessHook` beans from other modules still run inside `OrderLoader.saveRecord` if present.
