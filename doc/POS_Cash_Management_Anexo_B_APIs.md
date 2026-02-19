# Anexo B: Referencia Técnica de APIs y Flujos

> **Referencia para:** [Análisis Principal](POS_Cash_Management_Analysis.md)

---

## 1. State API — Acciones Registradas

### 1.1. Acciones Globales (`OB.App.StateAPI.Global`)

| Acción | Archivo | Descripción |
|--------|---------|-------------|
| `initCashup` | `actions/InitCashup.js` | Inicializa el cashup (local/backend/scratch). Tiene ActionPreparation para determinar el origen |
| `completeCashupAndCreateNew` | `actions/CompleteCashupAndCreateNew.js` | Cierra el cashup actual y crea uno nuevo. Envía mensaje al backend |
| `processCashManagements` | `actions/ProcessCashManagements.js` | Procesa los cash managements en draft, crea mensajes de sincronización |

### 1.2. Acciones del Modelo Cashup (`OB.App.StateAPI.Cashup`)

| Acción | Archivo | Descripción |
|--------|---------|-------------|
| `createCashManagement` | `actions/CreateCashManagement.js` | Agrega un cash management en modo draft al cashup |
| `cancelCashManagements` | `actions/CancelCashManagements.js` | Elimina cash managements en draft (no procesados) |
| `resetNewPayments` | `Cashup.js` | Marca todos los payment methods como `newPaymentMethod: false` |

### 1.3. Utility Functions (`OB.App.State.Cashup.Utils`)

| Función | Archivo | Descripción |
|---------|---------|-------------|
| `isValidTheLocalCashup(cashup)` | `CashupUtils.js` | Verifica si existe cashup local válido (`cashup.id != null`) |
| `requestNoProcessedCashupFromBackend()` | `CashupUtils.js` | Consulta al backend por cashup no procesado (isprocessed=N, isprocessedbo=N) |
| `requestProcessedCashupFromBackend()` | `CashupUtils.js` | Consulta al backend por último cashup procesado (isprocessed=Y) |
| `isValidTheBackendCashup(data)` | `CashupUtils.js` | Valida que la respuesta del backend contenga datos válidos |
| `createNewCashupFromScratch(payload)` | `CashupUtils.js` | Crea cashup con UUID, valores en cero, fecha actual |
| `createNewCashupFromBackend(payload)` | `CashupUtils.js` | Reconstruye cashup desde datos del backend |
| `initializePaymentMethodCashup(payload)` | `CashupUtils.js` | Inicializa array de payment methods con startingCash del cashup anterior |
| `addNewPaymentMethodsToCurrentCashup(payload)` | `CashupUtils.js` | Agrega nuevos payment methods al cashup existente (escenario local) |
| `addPaymentsFromBackendCashup(payload)` | `CashupUtils.js` | Reconstruye payments desde datos del backend |
| `countTicketInCashup(cashup, ticket, ...)` | `CashupUtils.js` | Acumula ventas, devoluciones e impuestos de un ticket en el cashup |
| `updateCashupFromTicket(ticket, cashup, payload)` | `CashupUtils.js` | Wrapper que llama countTicketInCashup y actualiza el ticket con el cashup id |
| `getStatisticsToIncludeInCashup()` | `CashupUtils.js` | Obtiene métricas de rendimiento (latencia, bandwidth, errores, etc.) |
| `resetStatisticsIncludedInCashup()` | `CashupUtils.js` | Resetea métricas a cero |
| `getCashupFilteredForSendToBackendInEachTicket(payload)` | `CashupUtils.js` | Filtra cashup para incluir solo payments del terminal |
| `getCashupPaymentsThatAreAlsoInTerminalPayments(...)` | `CashupUtils.js` | Filtra payments que existen en la config del terminal |
| `getCashManagements(paymentMethods)` | `CashManagementUtils.js` | Obtiene todos los cash managements de todos los payment methods |
| `getCashManagementsInDraft(paymentMethods)` | `CashManagementUtils.js` | Obtiene solo los cash managements con isDraft=true |
| `getCashManagementsByPaymentMethodId(...)` | `CashManagementUtils.js` | Filtra cash managements por payment method id |

---

## 2. Servicios Backend (Mobile Service Requests)

### 2.1. Endpoints que el POS consulta

| Servicio | Clase Java | Parámetros | Uso |
|----------|-----------|------------|-----|
| `org.openbravo.retail.posterminal.master.Cashup` | `Cashup.java` | `{ pos, isprocessed, isprocessedbo }` | Obtener cashup actual o último procesado |
| `org.openbravo.retail.posterminal.ProcessCashCloseSlave` | `ProcessCashCloseSlave.java` | `{ cashUpId }` | Registrar cashup de slave en master |
| `org.openbravo.retail.posterminal.ProcessCashCloseMaster` | `ProcessCashCloseMaster.java` | `{ masterterminal, cashUpId }` | Verificar estado de slaves, acumular pagos |

### 2.2. Procesadores de Sincronización (Import Entry)

| Entity | Procesador | Clase Destino | Descripción |
|--------|-----------|---------------|-------------|
| `OBPOS_App_Cashup` | `CashUpImportEntryProcessor` | `ProcessCashClose` | Procesa cierre de caja completo |
| `FIN_Finacc_Transaction` | `CashManagementImportEntryProcessor` | `ProcessCashMgmt` | Procesa operaciones de Cash Management |

### 2.3. Mensajes enviados por el POS

| Tipo de Mensaje | Clase Destino | Cuándo se envía |
|-----------------|---------------|-----------------|
| `OBPOS_CashUp` | `ProcessCashClose` | Al completar cashup (completeCashupAndCreateNew) |
| `OBPOS_CashUp` | `ProcessCashClose` | Al iniciar cashup en modo master/slave (initCashup) |
| `OBPOS_CashManagment` | `ProcessCashMgmt` | Al procesar cash managements (processCashManagements) |

---

## 3. Hooks Disponibles

### 3.1. Hooks Java (Backend)

| Interface | Descripción | Ejemplo de Uso |
|-----------|-------------|----------------|
| `CashupHook` | Se ejecuta al final del procesamiento de cashup | Lógica post-cierre personalizada |
| `ProcessCashMgmtHook` | Se ejecuta después de procesar un Cash Management | Gift Cards (`ProcessCashMgmtHookGiftCard`) |
| `ExtendsCashManagementPaymentTypeHook` | Extiende los tipos de pago disponibles en Cash Mgmt | Gift Cards (agrega GL Items adicionales) |
| `CountSafeboxHook` | Se ejecuta al contar una safe box | Lógica personalizada de conteo |
| `CashupReportHook` | Extiende la generación del reporte de cashup | Datos adicionales en el reporte |

### 3.2. Hooks JavaScript (Frontend)

| Hook | Cuándo se ejecuta | Payload |
|------|-------------------|---------|
| `OBPOS_EditCashupReport` | Al inicializar el modelo de CashUp, antes de mostrar resumen | `{ cashUpReport }` |
| `OBPOS_ProcessCashup` | Antes de ejecutar processAndFinish (cierre) | `{ cashupext }` |
| `OBPOS_PrePrintCashupHook` | Después de procesar, antes de imprimir | `{ cashupModel }` |
| `OBPOS_AfterCashUpSent` | Después de enviar el cashup al backend | — |

---

## 4. Estructura de Datos JSON

### 4.1. Cashup State (Frontend)

```json
{
  "id": "UUID",
  "netSales": 15000.00,
  "grossSales": 17250.00,
  "netReturns": 500.00,
  "grossReturns": 575.00,
  "totalRetailTransactions": 16675.00,
  "totalStartings": 1000.00,
  "creationDate": "2026-02-12T08:00:00Z",
  "userId": "UUID",
  "posterminal": "UUID",
  "trxOrganization": "UUID",
  "isprocessed": false,
  "cashTaxInfo": [
    {
      "id": "UUID",
      "name": "VAT 15%",
      "amount": 2250.00,
      "orderType": "0"
    }
  ],
  "cashPaymentMethodInfo": [
    {
      "id": "UUID",
      "paymentMethodId": "UUID",
      "searchKey": "POSCUR_payment.CASH",
      "name": "Cash USD",
      "startingCash": 500.00,
      "totalSales": 8000.00,
      "totalReturns": 200.00,
      "totalDeposits": 1000.00,
      "totalDrops": 500.00,
      "rate": 1,
      "isocode": "USD",
      "newPaymentMethod": false,
      "cashManagements": [
        {
          "id": "UUID",
          "description": "Deposit - Daily Cash",
          "amount": 1000.00,
          "origAmount": 1000.00,
          "type": "deposit",
          "reasonId": "UUID",
          "paymentMethodId": "UUID",
          "isDraft": false,
          "creationDate": "2026-02-12T10:30:00Z",
          "userId": "UUID",
          "user": "John Doe",
          "isocode": "USD",
          "cashup_id": "UUID"
        }
      ]
    }
  ],
  "cashCloseInfo": []
}
```

### 4.2. Mensaje de Cash Up (enviado al backend)

```json
{
  "id": "UUID-message",
  "terminal": "POS001",
  "cacheSessionId": "session-uuid",
  "data": [{
    "id": "UUID-cashup",
    "netSales": 15000.00,
    "grossSales": 17250.00,
    "netReturns": 500.00,
    "grossReturns": 575.00,
    "totalRetailTransactions": 16675.00,
    "isprocessed": "Y",
    "userId": "UUID",
    "posterminal": "UUID",
    "creationDate": "2026-02-12T08:00Z",
    "cashUpDate": "2026-02-12T18:00:00.000Z",
    "timezoneOffset": -120,
    "lastcashupeportdate": "2026-02-12T18:00:00.000",
    "cashPaymentMethodInfo": [...],
    "cashTaxInfo": [...],
    "cashCloseInfo": [
      {
        "id": "UUID",
        "paymentTypeId": "UUID",
        "expected": 8800.00,
        "foreignExpected": 8800.00,
        "difference": -50.00,
        "foreignDifference": -50.00,
        "paymentMethod": {
          "amountToKeep": 500.00,
          "automatemovementtoother": true,
          "allowmoveeverything": true,
          "allowdontmove": true,
          "keepfixedamount": true,
          "amount": 500.00
        }
      }
    ],
    "approvals": {
      "message": "Approved difference of -50.00",
      "supervisor": "UUID",
      "approvalReason": "UUID"
    },
    "cashMgmtIds": ["UUID-1", "UUID-2"],
    "currentSafeBox": "SAFEBOX001",
    "transitionsToOnline": 2,
    "logclientErrors": 0,
    "averageLatency": 150.5,
    "averageUploadBandwidth": 2048.0,
    "averageDownloadBandwidth": 5120.0
  }]
}
```

### 4.3. Mensaje de Cash Management (enviado al backend)

```json
{
  "id": "UUID-message",
  "terminal": "POS001",
  "cacheSessionId": "session-uuid",
  "data": [{
    "id": "UUID-transaction",
    "description": "Daily Cash Deposit - Cash USD",
    "amount": 1000.00,
    "origAmount": 1000.00,
    "type": "deposit",
    "reasonId": "UUID-glitem",
    "paymentMethodId": "UUID-payment",
    "user": "John Doe",
    "userId": "UUID-user",
    "creationDate": "2026-02-12T10:30:00.000Z",
    "timezoneOffset": -120,
    "isocode": "USD",
    "glItem": "UUID-glitem",
    "cashup_id": "UUID-cashup",
    "posTerminal": "UUID-terminal",
    "defaultProcess": "Y",
    "cashUpReportInformation": { /* cashup completo para actualizar reporte */ }
  }]
}
```

---

## 5. Flujo Completo de Datos: Ticket → Cashup → Backend

```
                    FRONTEND                                      BACKEND
                    ========                                      =======

1. Ticket completado
   │
   ▼
2. countTicketInCashup()
   ├── netSales += ticket.lines.net
   ├── grossSales += ticket.lines.gross
   ├── totalSales[payment] += payment.amount
   └── cashTaxInfo[tax] += tax.amount
   │
   ▼
3. updateCashupFromTicket()
   ├── ticket.obposAppCashup = cashup.id
   └── ticket.cashUpReportInformation = filtered_cashup
   │
   ▼                                                    
4. Message 'OBPOS_Order'     ─────────────────────────▶  OrderLoader.saveRecord()
   (con cashup info embebida)                            ├── Guarda orden
                                                         └── UpdateCashup.getAndUpdateCashUp()
                                                              ├── Actualiza netSales, grossSales...
                                                              └── Actualiza PaymentMethodCashup

5. Cash Management creado
   │
   ▼
6. createCashManagement() → isDraft=true
   │
   ▼
7. processCashManagements()
   ├── isDraft → false
   ├── totalDeposits/totalDrops += amount
   └── Crea Message 'OBPOS_CashManagment'
       │
       ▼                                                
8. Message ─────────────────────────────────────────▶  ProcessCashMgmt.saveRecord()
                                                       ├── UpdateCashup
                                                       ├── Crea FIN_FinaccTransaction
                                                       ├── Actualiza Financial Account balance
                                                       └── Guarda PaymentCashupEvent

9. Cash Up iniciado (wizard)
   │
   ▼
10. completeCashupAndCreateNew()
    ├── Marca isprocessed = 'Y'
    ├── Adjunta closeCashupInfo (counted, expected, difference, amountToKeep)
    ├── Crea Message 'OBPOS_CashUp'
    ├── Crea nuevo Cashup (startingCash = amountToKeep)
    │
    ▼                                                  
11. Message ─────────────────────────────────────────▶ ProcessCashClose.saveRecord()
                                                       ├── UpdateCashup (final)
                                                       ├── Manejo Safe Box
                                                       ├── Guardar aprobaciones
                                                       └── CashCloseProcessor.processCashClose()
                                                            ├── Crear transacción de diferencia
                                                            ├── Crear reconciliación
                                                            ├── Crear transferencia a cuenta destino
                                                            ├── Reconciliar safe box
                                                            ├── Ejecutar CashupHooks
                                                            └── processedbo = true
```

---

## 6. Permisos y Configuración

### 6.1. Permisos Relevantes

| Permiso | Descripción |
|---------|-------------|
| `OBPOS_retail.cashup` | Acceso a la funcionalidad de Cash Up |
| `OBPOS_print.cashup` | Permiso para imprimir reporte de cashup |
| `OBPOS_HideCountInformation` | Oculta montos esperados durante el conteo |
| `OBPOS_retail.cashupGroupExpectedPayment` | Agrupa payment methods en el expected |
| `OBPOS_retail.cashupRemoveUnusedPayment` | Elimina payment methods sin movimientos del reporte |
| `OBPOS_timeAllowedDrawerCount` | Tiempo permitido para conteo del cajón |
| `OBPOS_CashupCountDiff` | Tipo de aprobación para diferencias en conteo |

### 6.2. Configuración del Payment Method

| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `iscash` | Boolean | Si es un método de pago en efectivo |
| `countcash` | Boolean | Si permite conteo por denominación (billetes/monedas) |
| `countpaymentincashup` | Boolean | Si se incluye en el cashup |
| `automatemovementtoother` | Boolean | Si activa el paso "Cash to Keep" |
| `allowmoveeverything` | Boolean | Opción: mover todo al back-office |
| `allowdontmove` | Boolean | Opción: no mover nada |
| `keepfixedamount` | Boolean | Opción: mantener monto fijo |
| `allowvariableamount` | Boolean | Opción: monto variable (input del usuario) |
| `amount` | Decimal | Monto fijo a mantener (si keepfixedamount) |
| `isshared` | Boolean | Si es compartido entre master/slave |
| `issafebox` | Boolean | Si está definido en safe box |
| `allowopendrawer` | Boolean | Si abre el cajón al procesar Cash Mgmt |
| `cashManagementProvider` | String | Proveedor externo de Cash Management |
| `isRounding` | Boolean | Si es un método de redondeo |
| `countPerAmount` | Boolean | Si soporta conteo por denominación |

### 6.3. Configuración del Terminal

| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `ismaster` | Boolean | Si es terminal master |
| `isslave` | Boolean | Si es terminal slave | 
| `masterterminal` | FK | Referencia al terminal master |
| `currency` | FK | Moneda del terminal |
| `countLayawayAsSales` | Boolean | Si cuenta layaways como ventas |

---

## 7. Diagrama de Secuencia: Cash Up Completo

```
Operador          POS Frontend              Backend
   │                    │                      │
   │  Inicia Cash Up    │                      │
   │───────────────────▶│                      │
   │                    │                      │
   │                    │  Verifica tickets     │
   │                    │  pendientes           │
   │                    │                      │
   │  Paso 1: Pending   │                      │
   │  Orders             │                      │
   │  (resuelve/elimina)│                      │
   │───────────────────▶│                      │
   │                    │                      │
   │  Paso 2: Master    │  ProcessCashClose    │
   │  (si es master)    │  Slave               │
   │                    │─────────────────────▶│
   │                    │  ← estado slaves     │
   │                    │◀─────────────────────│
   │                    │                      │
   │  Paso 3: Cash      │                      │
   │  Payments           │                      │
   │  (conteo monedas)  │                      │
   │───────────────────▶│                      │
   │                    │                      │
   │  Paso 4: Payment   │                      │
   │  Methods            │                      │
   │  (conteo total)    │                      │
   │───────────────────▶│                      │
   │                    │                      │
   │  Paso 5: Cash      │                      │
   │  To Keep            │                      │
   │  (monto a dejar)   │                      │
   │───────────────────▶│                      │
   │                    │                      │
   │  Paso 6: Confirm   │                      │
   │───────────────────▶│                      │
   │                    │                      │
   │                    │  completeCashup      │
   │                    │  AndCreateNew()      │
   │                    │                      │
   │                    │  Message OBPOS_CashUp│
   │                    │─────────────────────▶│
   │                    │                      │ ProcessCashClose
   │                    │                      │ ├── UpdateCashup
   │                    │                      │ ├── SafeBox
   │                    │                      │ ├── Reconciliation
   │                    │                      │ ├── Invoices
   │                    │                      │ └── processedbo=true
   │                    │                      │
   │                    │  Nuevo Cashup local  │
   │                    │  (startingCash =     │
   │                    │   amountToKeep)      │
   │                    │                      │
   │  Imprime reporte   │                      │
   │◀───────────────────│                      │
   │                    │                      │
   │  Navega a POS      │                      │
   │  o Logout          │                      │
   │◀───────────────────│                      │
```
