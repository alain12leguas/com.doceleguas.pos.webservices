# Transform and persistence seams (strangler)

Implemented types live under `com.doceleguas.pos.webservices.orderload`.

| Seam | Implementation today | Next step |
|------|------------------------|-----------|
| `ExternalEnvelopeTransform` | `IdentityExternalEnvelopeTransform` (no-op) | Replace with OCRE-native mapping that produces the same post-transform shape as `ExternalOrderLoader.transformMessage`, then stop calling `LegacyRetailOrderPipelineAdapter.transformInbound`. |
| `OcreOrderPreLoadHook` | `OcreInvoiceCalculatedInfoHook` | Add more hooks as needed; they stay in OBPL. |
| `OrderPersistencePort` | `RetailOrderPersistenceAdapter` → `OrderLoader.exec` | Add `CoreOrderPersistenceAdapter` and switch CDI default when parity is proven (see `doc/regression-suite-OCWS_Order.md`). |

`OcreOrderLoadOrchestrator#importEnvelope` mirrors legacy `ExternalOrderLoader#importOrder` error JSON on failure (`RESPONSE_STATUS`, `message`, etc.).
