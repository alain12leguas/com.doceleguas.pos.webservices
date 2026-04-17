# Referencia de secuencia: OrderLoader (retail) vs pipeline nativo OCOrder

Documento de trabajo para alinear el comportamiento sin copiar código bajo OBCL. Basado en lectura de `OrderLoader.saveRecord` y `initializeVariables` en el módulo retail.

## Variables de control (`initializeVariables`)

- `createShipment`: `!isQuotation && !isDeleted && generateShipment` (boolean JSON, default false).
- `deliver`: `!isQuotation && !isDeleted && deliver`.
- `createInvoice`: **`calculatedInvoice` presente en JSON** o **`generateExternalInvoice` true**.  
  Nota: `ExternalOrderLoader.handleOrderSteps` pone `generateExternalInvoice` en pasos `SHIP` y `ALL`, equivalente a facturar al completar el flujo completo.

## Orden principal en `saveRecord` (simplificado)

1. Actualizar cashup si viene `cashUpReportInformation`.
2. `initializeVariables`, validaciones, carga de `Order` existente si aplica.
3. Hooks previos / `DeferredServiceDelivery`.
4. Crear o actualizar cabecera y líneas (`createOrderAndLines` / ramas layaway).
5. Atributos en líneas (`updateLinesWithAttributes`).
6. **Envío**: si `createShipment` → `ShipmentUtils.createNewShipment(order, jsonorder, lineReferences, documentNoHandlers)`.
7. **Factura**: si `createInvoice` (y no `OBPOSNotInvoiceOnCashUp`) → `InvoiceUtils.createNewInvoice` con `calculatedInvoice` o el propio `jsonorder`.
8. **`handlePayments(jsonorder, order, invoice)`**: planes de pago, `FIN_Payment`, enlaces a PSD de pedido/factura.
9. Aplicar `DocumentNoHandler` diferidos (numeración tardía).
10. Hooks (`OrderLoaderHook`, cancel/replace, etc.) y post-proceso.

## Implicación para OCOrder nativo

- Sin `generateExternalInvoice` / `calculatedInvoice`, **OrderLoader tampoco factura**. El cliente nativo debe enviar uno de esos flags o el backend debe aplicar el equivalente a `step=ALL` del `ExternalOrderLoader` para ventas cerradas.
- El servicio `OcreNativeStandardDocumentsService` en Doceleguas implementa envío + factura para **venta estándar completada** (`completeTicket`) sin depender de clases `utility` retail.

## Servicios nativos (diseño consolidado)

Los roles previstos en el plan (`OcreInvoicePersistence`, `OcreShipmentPersistence`, `OcrePaymentScheduleBridge`) quedan cubiertos en esta primera entrega así:

| Rol planificado | Implementación actual |
|-----------------|------------------------|
| Factura desde pedido/líneas | `OcreNativeStandardDocumentsService#createInvoiceIfRequested` |
| Envío C- y stock | `OcreNativeStandardDocumentsService#createShipmentIfRequested` |
| Plan de pagos / PSD / `FIN_Payment` | Sigue en `CoreOrderPersistenceAdapter#createBasicPayments` (sin `OrderLoader.handlePayments`) |

Orquestación en `CoreOrderPersistenceAdapter`: líneas → `completeOrder` → (si `STANDARD_SALE` + `completeTicket` + flags de documento) → flags POS en líneas → envío → factura → pagos.

## Paridad SQL

Validar con el runbook del cliente (`docs/testing/ocorder-parity/runbook.md`, sección 4) y las consultas de `C_INVOICE`, `M_INOUT`, `FIN_PAYMENTSCHEDULE` tras una corrida con `-Ddoceleguas.ocorder.mode=native`.

## Dependencia del módulo retail

El pipeline OCOrder ya no importa `InvoiceUtils` / `ShipmentUtils`. El módulo `com.doceleguas.pos.webservices` **sigue** dependiendo de `org.openbravo.retail.posterminal` para terminal (`OBPOSApplications`), login, cashup y otros endpoints; no se puede retirar `AD_MODULE_DEPENDENCY` al retail solo con este cambio.

## Licencia

El código retail citado aquí es solo referencia de comportamiento; la implementación en `com.doceleguas.pos.webservices` es original (OBPL).
