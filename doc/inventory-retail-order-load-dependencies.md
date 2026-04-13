# Inventory: ExternalOrderLoader → OrderLoader (OCRE-POS import path)

Reference for decoupling `com.doceleguas.pos.webservices` from `org.openbravo.retail.posterminal`. Sources under `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/`.

## Call graph (high level)

1. **`ExternalOrderLoader.importOrder(JSONObject messageIn)`**  
   `setRunInSynchronizedMode(true)` → **`transformMessage(messageIn)`** → **`exec(transformed)`** (inherited `OrderLoader` / mobile sync stack).

2. **`ExternalOrderLoader.transformMessage`** (protected)  
   External envelope (`channel` = `External`) → internal shape (`FromExternal`, terminal UUID, `transformOrder` per `data[]` element, cancel / cancel_replace handling). Uses DAL (`OBDal`, `OBContext`), preferences, `OBPOSApplications`, etc.

3. **`OrderLoader.exec` / `saveRecord`**  
   Per-order persistence: taxes, payments (`FIN_*`, `advpaymentmngt`), shipments (`ShipmentUtils`), invoices (`InvoiceUtils`), promos, hooks (`OrderLoaderPreProcessHook`, `OrderLoaderHook`, …), `mobile.core` sync, `JSONPropertyToEntity`, `CancelAndReplaceUtils`, retail utilities (`AttributesUtils`, `DocumentNoHandler`), and extension `com.smf.pos.change.funds.utils.UtilsBPChange`.

## ExternalOrderLoader — direct Java dependencies (imports)

Openbravo / Hibernate / JSON / servlet: `OBDal`, `OBQuery`, `OBContext`, `SessionHandler`, `OrganizationStructureProvider`, `ModelProvider`, `Entity`, `LockOptions`, `Query`, `JSONObject` / `JSONArray`, `ServletException`, `HttpServletResponse`, `AuthenticationManager.Stateless`, CDI `Inject` / `Instance` / `Any`.

Domain: `User`, `Role`, `BusinessPartner`, `Location`, `Organization`, `Warehouse`, `OrgWarehouse`, `DocumentType`, `Order`, `OrderLine`, `Invoice`, `Product`, `UOM`, `Currency`, `TaxRate`, `FIN_PaymentSchedule`, `ShipmentInOut`, `PriceAdjustment`.

Services: `RequestContext`, `OBMOBCUtils`, `Preferences`, `PropertyException`, `SequenceIdData`, `DbUtility`, `ImportEntry`, `ImportEntryArchive`, `ImportEntryProcessor`, `ImportEntryQualifier`, `DataToJsonConverter`, `DataResolvingMode`, `JsonConstants`, `JsonUtils`, `WeldUtils`, `OBException`.

## OrderLoader — additional dependency clusters (beyond ExternalOrderLoader)

- **Payments:** `FIN_AddPayment`, `FIN_PaymentProcess`, `FIN_Utility`, entities `FIN_Payment`, `FIN_PaymentDetail`, `FIN_PaymentMethod`, `FIN_FinancialAccount`, `FIN_FinaccTransaction`, `FIN_PaymentSchedule`, `FIN_PaymentScheduleDetail`, `PaymentTerm`.
- **DAL / ERP:** `OBCriteria`, `TriggerHandler`, `AcctServer`, `Utility`, `OBMessageUtils`, `DalConnectionProvider`, `FinancialUtils`, `CancelAndReplaceUtils`.
- **Mobile / sync:** `DataSynchronizationImportProcess`, `DataSynchronization`, `JSONPropertyToEntity`, `OutDatedDataChangeException`, `OBMOBCUtils`.
- **Retail utilities:** `ShipmentUtils`, `InvoiceUtils`, `AttributesUtils`, `DocumentNoHandler`.
- **Pricing / tax / order model:** `PriceList`, `ProductPrice`, `OrderLineOffer`, `OrderTax`, `OrderLineTax`, `OrderlineServiceRelation`, `TaxRate`, `Locator`, `Country`, `Region`.
- **Extension:** `com.smf.pos.change.funds.utils.UtilsBPChange`.

## Custom module seam (target architecture)

| Layer | Current retail | Future native |
|--------|----------------|----------------|
| Envelope adjust | `ExternalEnvelopeTransform` (identity) | OCRE-specific mapping replacing `transformMessage` |
| Post-transform, pre-persist | `OcreOrderPreLoadHook` (Doceleguas) | Same or merged into native transform |
| Persist | `OrderPersistencePort` → `RetailOrderPersistenceAdapter` → `OrderLoader.exec` | `CoreOrderPersistenceAdapter` (DAL / Core APIs only) |

## Related Doceleguas code

- `OcreOrderLoadOrchestrator` — wires transform port, hooks, persistence port.
- `LegacyRetailOrderPipelineAdapter` — extends `ExternalOrderLoader`; exposes `transformInbound` / `executeLoaded` and applies `OcreOrderPreLoadHook` **after** `transformMessage` (same timing as former `OrderLoaderPreProcessHook` for invoice flags).
