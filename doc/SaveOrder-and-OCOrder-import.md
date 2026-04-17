# SaveOrder webservice and OCWS_Order import queue

## Endpoint

- **URL:** `POST /ws/com.doceleguas.pos.webservices.SaveOrder`
- **Content-Type:** `application/json`
- **Body:** Native SaveOrder v2: `messageId`, `posTerminal`, `order` (see `SaveOrder-native-contract-v2.md` if present in your tree).

## Database prerequisite (required)

`ImportEntry` validates `typeofdata` against the **Type of Import Data** reference list. You **must** install list value `OCWS_Order` via module sourcedata and `ant update.database`. Without it, `SaveOrder` fails at enqueue with `ValidationException`.

See: [OCOrder-import-type-database.md](OCOrder-import-type-database.md)

## Processing chain

`SaveOrder` → `C_IMPORT_ENTRY` (`OCWS_Order`) → `OCOrderImportRunnable` → `OcreOrderLoadOrchestrator` → `CoreOrderPersistenceAdapter`.

HTTP `202` means queued only; final status is in `C_IMPORT_ENTRY`.

## Native flow routing (no retail fallback)

`OcreOrderLoadOrchestrator` now classifies each order payload and routes it through the native
pipeline by flow type:

- `STANDARD_SALE`
- `RETURN` (including blind return)
- `QUOTATION`
- `LAYAWAY`
- `OTHER` (best-effort create path with diagnostics)

There is no runtime delegation to `ExternalOrderLoader`/`OrderLoader`. The objective is full
functional coverage inside `com.doceleguas.pos.webservices`.

## Standard-sale parity outcome

For accepted standard-sale payloads that pass native validation and persistence:

- `C_Order.DocStatus = CO` (Booked)
- `C_Order.Processed = Y`
- `C_Order.Processing = N`
- `C_OrderLine` stores net and gross values (`PriceActual`, `PriceList`, `PriceStd`, `LineNetAmt`,
  `Gross_Unit_Price`, `GrossPriceList`, `grosspricestd`, `Line_Gross_Amount`)
