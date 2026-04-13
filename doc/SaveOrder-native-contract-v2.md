# SaveOrder Native Contract v2

`POST /ws/com.doceleguas.pos.webservices.SaveOrder`

## Purpose

Receive OCRE-POS orders with native OrderLoader-oriented payload and enqueue them in
`C_IMPORT_ENTRY` (`TYPEOFDATA=OCWS_Order`) for asynchronous processing.

## Request

```json
{
  "messageId": "unique-id",
  "posTerminal": "POS001",
  "order": { "...": "native order fields" }
}
```

Required:

- `messageId` (string)
- `order` (json object)

Required for standard-sale parity (`order`):

- `grossAmount` (number)
- `netAmount` (number)
- `lines` (non-empty array)
- Each line must include:
  - `product` (string)
  - `qty` (number, non-zero)
  - Net price information: at least one of `baseNetUnitPrice`, `price`, `lineNetAmount`
  - Gross price information: at least one of `grossUnitPrice`, `baseGrossUnitPrice`, `grossListPrice`
  - Gross amount information: at least one of `lineGrossAmount`, `grossAmount`

## Queue normalization

SaveOrder stores normalized payload in queue:

- `messageId`
- `posTerminal`
- `appName = OCRE`
- `channel = Native`
- `data = [order]`

This is the internal shape consumed by `OCOrderImportRunnable` and
`OcreOrderLoadOrchestrator`.

## Response

- `202 Accepted` when queued.
- `202 Accepted` + `duplicate:true` for repeated `messageId`.
- `400` for invalid JSON / missing required fields.

## Runtime guarantees

- Asynchronous semantics preserved.
- Idempotency preserved through `messageId` as queue entry id.
- No runtime call to legacy `ExternalOrderLoader`/`OrderLoader`.
- Native persistence completes the order lifecycle for standard sale path (via Order Post process):
  - `C_Order.DocStatus = CO`
  - `C_Order.Processed = Y`
  - `C_Order.Processing = N`
  - `C_OrderLine` net + gross monetary fields are persisted (no silent zeroing).
