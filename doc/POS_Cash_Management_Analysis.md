# Análisis Completo: Gestión de Caja POS en Openbravo

> **Proyecto:** Openbravo POS  
> **Módulo principal:** `org.openbravo.retail.posterminal`  
> **Fecha de análisis:** 12 de febrero de 2026  
> **Autor:** GitHub Copilot

---

## Tabla de Contenidos

1. [Mapa Conceptual General](#1-mapa-conceptual-general)
2. [Arquitectura del Sistema](#2-arquitectura-del-sistema)
3. [Open Till (Apertura de Caja)](#3-open-till-apertura-de-caja)
4. [Initial Count (Conteo Inicial)](#4-initial-count-conteo-inicial)
5. [Cash Management (Gestión de Efectivo)](#5-cash-management-gestión-de-efectivo)
6. [Cash Up (Cierre de Caja / Arqueo)](#6-cash-up-cierre-de-caja--arqueo)
7. [Close Till (Cierre Final de Terminal)](#7-close-till-cierre-final-de-terminal)
8. [Modelo de Datos](#8-modelo-de-datos)
9. [Arquitectura Master/Slave](#9-arquitectura-masterslave)
10. [Personalizaciones Halsteds](#10-personalizaciones-halsteds)
11. [Índice de Archivos](#11-índice-de-archivos)

---

## 1. Mapa Conceptual General

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CICLO DE VIDA DE CAJA POS                              │
│                                                                             │
│   ┌──────────┐    ┌───────────────┐    ┌────────────────┐    ┌───────────┐ │
│   │ OPEN TILL│───▶│ INITIAL COUNT │───▶│ OPERACIONES    │───▶│  CASH UP  │ │
│   │(Apertura)│    │(Conteo Inic.) │    │ DIARIAS        │    │ (Arqueo)  │ │
│   └──────────┘    └───────────────┘    │                │    └─────┬─────┘ │
│        │                               │ ┌────────────┐ │          │       │
│        │          ┌────────────────────▶│ │   VENTAS   │ │          │       │
│        │          │                    │ └────────────┘ │          │       │
│        │          │                    │ ┌────────────┐ │          ▼       │
│        │          │                    │ │DEVOLUCIONES│ │   ┌───────────┐  │
│        │          │                    │ └────────────┘ │   │CLOSE TILL │  │
│        │          │                    │ ┌────────────┐ │   │(Cierre)   │  │
│        │          │   CASH MANAGEMENT◀─┤ │CASH MGMT  │ │   └───────────┘  │
│        │          │   (Depósitos/     │ │(Dep/Retiro)│ │        │         │
│        │          │    Retiros)        │ └────────────┘ │        │         │
│        │          │                    └────────────────┘        │         │
│        │          │                                              │         │
│        └──────────┴──── NUEVO CICLO ◀────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Flujo Principal

```
USUARIO INICIA SESIÓN ──▶ initCashup() ──▶ [local | backend | scratch]
                              │
                    ┌─────────┼──────────┐
                    ▼         ▼          ▼
              Desde Local  Desde BD   Desde Cero
              (continua)   (recupera) (nuevo cashup)
                    │         │          │
                    └─────────┼──────────┘
                              ▼
                     TERMINAL OPERATIVO
                    (Ventas, Devoluciones)
                              │
                    Cada ticket ──▶ countTicketInCashup()
                              │
                    Cash Management ──▶ createCashManagement()
                                   ──▶ processCashManagements()
                              │
                    Iniciar Cash Up ──▶ Wizard 6 pasos
                              │
                    completeCashupAndCreateNew()
                              │
                    ┌─────────┼──────────┐
                    ▼                    ▼
              Backend:              Frontend:
              ProcessCashClose      Nuevo Cashup
              + Reconciliación      + Reset stats
              + Invoices
```

---

## 2. Arquitectura del Sistema

### 2.1. Capas de la Aplicación

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (POS Web)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐ │
│  │   Enyo Views  │  │  Backbone    │  │  App State API    │ │
│  │   (Legacy UI) │  │  Models      │  │  (Nueva Arq.)     │ │
│  └──────────────┘  └──────────────┘  └───────────────────┘ │
│         │                │                    │             │
│         └────────────────┼────────────────────┘             │
│                          ▼                                  │
│               State Management (Redux-like)                 │
│                     OB.App.State                           │
├─────────────────────────────────────────────────────────────┤
│                  COMMUNICATION LAYER                         │
│           mobileServiceRequest / Messages API               │
├─────────────────────────────────────────────────────────────┤
│                    BACKEND (Java)                            │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐ │
│  │  Processors   │  │  Master      │  │  Import Entry     │ │
│  │  (CashClose,  │  │  Services    │  │  Processors       │ │
│  │   CashMgmt)   │  │  (Cashup..)  │  │  (Async Queue)    │ │
│  └──────────────┘  └──────────────┘  └───────────────────┘ │
│         │                │                    │             │
│         └────────────────┼────────────────────┘             │
│                          ▼                                  │
│                   DAL / Hibernate                           │
├─────────────────────────────────────────────────────────────┤
│                    DATABASE                                  │
│  OBPOS_APP_CASHUP │ OBPOS_PAYMENTMETHODCASHUP │ OBPOS_TAXCASHUP │
│  FIN_FINACC_TRANSACTION │ FIN_RECONCILIATION   │ OBPOS_SAFEBOX   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2. Patrón de Comunicación POS ↔ Backend

El POS utiliza un sistema de **Messages** para sincronizar datos con el backend de forma asíncrona:

1. **Frontend** crea un `Message` con tipo y clase destino
2. El mensaje se encola en `state.Messages`
3. El framework de sincronización envía el mensaje como `ImportEntry`
4. El **ImportEntryProcessor** correspondiente procesa el registro
5. El **DataSynchronizationProcess** ejecuta la lógica de negocio

```javascript
// Ejemplo: Envío de CashUp al backend
const newMessage = OB.App.State.Messages.Utils.createNewMessage(
  'OBPOS_CashUp',                                          // Tipo
  'org.openbravo.retail.posterminal.ProcessCashClose',      // Clase Java destino
  newMessagePayload,                                         // Datos
  { name: 'OBPOS_CashUp' }                                 // Props extra
);
```

**Ver:** [Anexo A - Detalle de Archivos](POS_Cash_Management_Anexo_A_Archivos.md)

---

## 3. Open Till (Apertura de Caja)

### 3.1. Concepto

En Openbravo POS no existe un proceso explícito de "Open Till" como botón separado. La apertura de caja se realiza **automáticamente** al iniciar sesión en el terminal POS, mediante el proceso `initCashup`. Este proceso determina si existe un cashup activo previo o si debe crear uno nuevo.

### 3.2. Flujo Funcional

```
INICIO DE SESIÓN
       │
       ▼
┌──────────────────────┐
│   initCashup (Action │
│   Preparation)       │
│                      │
│ 1. ¿Cashup local    │──── SÍ ──▶ initCashupFrom = 'local'
│    válido?           │                  │
│                      │                  ▼
│ 2. ¿Cashup en       │          addNewPaymentMethodsToCurrentCashup()
│    backend sin       │──── SÍ ──▶ initCashupFrom = 'backend'
│    procesar?         │                  │
│                      │                  ▼
│ 3. Ninguno           │          createNewCashupFromBackend()
│    disponible        │──── SÍ ──▶ initCashupFrom = 'scratch'
│                      │                  │
└──────────────────────┘                  ▼
                                  createNewCashupFromScratch()
                                          │
                                          ▼
                                  initializePaymentMethodCashup()
                                  (Establece startingCash por
                                   método de pago)
```

### 3.3. Detalle Técnico

**Archivo principal:** `app/model/business-object/cashup/actions/InitCashup.js`

#### 3.3.1. Action Preparation (Pre-acción)

```javascript
OB.App.StateAPI.Global.initCashup.addActionPreparation(
  async (state, payload) => {
    if (OB.App.State.Cashup.Utils.isValidTheLocalCashup(state.Cashup)) {
      newPayload.initCashupFrom = 'local';
    } else {
      const backendCashupResponse = await OB.App.State.Cashup.Utils
        .requestNoProcessedCashupFromBackend();
      if (!backendCashupResponse.error && isValidTheBackendCashup(...)) {
        newPayload.initCashupFrom = 'backend';
        newPayload.currentCashupFromBackend = backendCashupResponse.data[0];
      } else {
        newPayload.initCashupFrom = 'scratch';
        // Cargar pagos del último cashup procesado para obtener startingCash
        const lastBackendCashupResponse = await OB.App.State.Cashup.Utils
          .requestProcessedCashupFromBackend();
        newPayload.lastCashUpPayments = lastBackendCashupResponse.data[0]
          ?.cashPaymentMethodInfo;
      }
    }
  }
);
```

#### 3.3.2. Tres escenarios de inicialización

| Escenario | Condición | Comportamiento |
|-----------|-----------|----------------|
| **Local** | `cashup.id != null` | Usa el cashup del estado local, solo agrega nuevos métodos de pago |
| **Backend** | Existe cashup no procesado en BD | Recupera datos del backend, reconstruye estructura local |
| **Scratch** | No hay cashup previo | Crea cashup nuevo con UUID, inicializa a cero, calcula `startingCash` del último cierre |

#### 3.3.3. Inicialización del Starting Cash

```javascript
// En CashupUtils.js → initializePaymentMethodCashup()
// Para cada método de pago del terminal:
// - startingCash = último cashup.cashCloseInfo[paymentMethod].amountToKeep (lo que se dejó en caja)
// - Si es terminal slave con pago compartido → startingCash = 0
// - totalSales = 0, totalReturns = 0, totalDeposits = 0, totalDrops = 0
```

### 3.4. Backend: Servicio Cashup Master

**Archivo:** `master/Cashup.java`

El servicio `org.openbravo.retail.posterminal.master.Cashup` es consultado por el frontend para obtener el cashup actual o el último procesado:

```java
// HQL para buscar cashup
"from OBPOS_App_Cashup c where c.isProcessed=:isprocessed 
 and c.pOSTerminal.id=:terminal order by c.creationDate desc"
```

Retorna: id, netSales, grossSales, netReturns, grossReturns, totalRetailTransactions, cashPaymentMethodInfo, cashTaxInfo, cashMgmInfo.

---

## 4. Initial Count (Conteo Inicial)

### 4.1. Concepto

El "Initial Count" o conteo inicial establece el saldo de apertura (`startingCash`) de cada método de pago en la caja. En Openbravo POS, este proceso está **integrado dentro de la inicialización del cashup** y no es un paso interactivo separado para el operador.

### 4.2. Cómo se Calcula el Starting Cash

```
┌─────────────────────────────────────────────┐
│        CÁLCULO DE STARTING CASH             │
│                                             │
│  CASO 1: Desde Scratch (primer uso o       │
│           post-cierre)                      │
│                                             │
│  startingCash[payment] =                   │
│    lastCashUp.cashCloseInfo[payment]        │
│      .amountToKeep                          │
│                                             │
│  (= lo que el operador decidió dejar       │
│     en caja en el último Cash Up)          │
│                                             │
│  CASO 2: Desde Backend                     │
│                                             │
│  startingCash[payment] =                   │
│    backendCashup.cashPaymentMethodInfo      │
│      [payment].startingcash                │
│                                             │
│  CASO 3: Desde Local                       │
│                                             │
│  startingCash[payment] = sin cambios       │
│  (ya se tiene el valor correcto)           │
└─────────────────────────────────────────────┘
```

### 4.3. Relación con el Cierre

El valor de `startingCash` se determina durante el cierre anterior:

1. Durante **Cash Up**, el operador cuenta cada método de pago (`counted`)
2. Decide cuánto mantener en caja (`qtyToKeep`) via el paso "Cash to Keep"
3. El `amountToKeep` se almacena en `cashCloseInfo`
4. Al crear el siguiente cashup (`initCashupFrom = 'scratch'`), ese `amountToKeep` se convierte en el nuevo `startingCash`

### 4.4. Estructura de Datos del Payment Method Cashup

```javascript
cashPaymentMethodInfo: [
  {
    paymentMethodCashupId: "uuid",
    paymentMethodId: "uuid",         // ID del método de pago
    searchKey: "POSCUR_payment.CASH", // Clave de búsqueda
    name: "Cash USD",                // Nombre visible
    startingCash: 500.00,            // ← CONTEO INICIAL
    totalSales: 0.00,                // Acumulado ventas
    totalReturns: 0.00,              // Acumulado devoluciones
    totalDeposits: 0.00,             // Depósitos (Cash Management)
    totalDrops: 0.00,                // Retiros (Cash Management)
    rate: 1,                         // Tasa de cambio
    isocode: "USD",                  // Moneda
    cashManagements: [],             // Movimientos de caja
    newPaymentMethod: false          // Si es nuevo en este cashup
  },
  // ... más métodos de pago
]
```

---

## 5. Cash Management (Gestión de Efectivo)

### 5.1. Concepto

Cash Management permite realizar **depósitos** (deposits) y **retiros** (drops) de efectivo entre la caja del terminal y la oficina trasera (back-office) o caja fuerte (safe box) durante la jornada operativa, sin necesidad de cerrar la caja.

### 5.2. Tipos de Operaciones

| Tipo | Descripción | Efecto en Cuenta Financiera |
|------|-------------|----------------------------|
| **Deposit** | Ingreso de efectivo a la caja | `+amount` en Financial Account |
| **Drop** | Retiro de efectivo de la caja | `-amount` de Financial Account |

### 5.3. Flujo Funcional

```
┌─────────────────┐
│ Operador abre   │
│ Cash Management │
│ desde el menú   │
└────────┬────────┘
         ▼
┌─────────────────┐     ┌────────────────────┐
│ Selecciona      │     │ Lista de eventos   │
│ método de pago  │────▶│ (GL Items) según   │
│ y tipo          │     │ configuración del  │
│ (deposit/drop)  │     │ terminal           │
└────────┬────────┘     └────────────────────┘
         ▼
┌─────────────────┐
│ Ingresa monto   │
│ y razón         │
└────────┬────────┘
         ▼
┌─────────────────────────────────────────┐
│ createCashManagement (State Action)      │
│ → Agrega al cashup en modo DRAFT        │
│ → cashManagement.isDraft = true          │
└────────┬────────────────────────────────┘
         ▼
┌──────────────────┐     ┌──────────────────┐
│ ¿Más operaciones?│─SÍ─▶│ Repetir proceso  │
└────────┬─────────┘     └──────────────────┘
         │ NO
         ▼
┌─────────────────────────────────────────┐
│    Clic "Done" (Finalizar)              │
│                                          │
│ ┌─ processCashManagements() ──────────┐ │
│ │ 1. Eliminar isDraft de cada CM       │ │
│ │ 2. Actualizar totalDeposits/Drops    │ │
│ │ 3. Crear Message por cada CM         │ │
│ │    tipo: OBPOS_CashManagment         │ │
│ │    dest: ProcessCashMgmt             │ │
│ └──────────────────────────────────────┘ │
└────────┬────────────────────────────────┘
         ▼
┌────────────────────────────────┐
│  Clic "Cancel" (alternativa)   │
│                                │
│  cancelCashManagements()       │
│  → Elimina CMs con isDraft     │
│  → No modifica totales          │
└────────────────────────────────┘
```

### 5.4. Detalle Técnico Frontend

#### 5.4.1. Modelo de Cash Management

**Archivo:** `js/cashmgmt/model/cashmgmt-model.js`

```javascript
OB.OBPOSCashMgmt.Model.CashManagement = OB.Model.TerminalWindowModel.extend({
  // Escucha evento 'paymentDone' para crear Cash Managements
  // Validaciones:
  //   - Drop no puede exceder el total disponible
  //   - Monto debe ser > 0
  //   - Crea un objeto CashManagement con todos los datos necesarios
  // Escucha 'makeDeposits' para procesar todos los drafts
});
```

#### 5.4.2. Creación de Cash Management (State Action)

**Archivo:** `app/model/business-object/cashup/actions/CreateCashManagement.js`

```javascript
OB.App.StateAPI.Cashup.registerActions({
  createCashManagement(cashup, payload) {
    // 1. Marca cashManagement como isDraft = true
    // 2. Lo agrega al array cashManagements del payment method correspondiente
    // 3. Retorna el cashup actualizado (inmutable)
  }
});
```

#### 5.4.3. Procesamiento (State Action Global)

**Archivo:** `app/model/business-object/cashup/actions/ProcessCashManagements.js`

```javascript
OB.App.StateAPI.Global.registerActions({
  processCashManagements(state, payload) {
    // Para cada paymentMethod con cashManagements:
    //   - Elimina isDraft de los CMs pendientes
    //   - Actualiza totalDeposits o totalDrops
    //   - Crea un Message para sincronizar con backend
    //     tipo: 'OBPOS_CashManagment'
    //     destino: 'org.openbravo.retail.posterminal.ProcessCashMgmt'
  }
});
```

### 5.5. Detalle Técnico Backend

#### 5.5.1. ProcessCashMgmt.java

**Archivo:** `src/org/openbravo/retail/posterminal/ProcessCashMgmt.java`

Este es el procesador principal del backend para operaciones de Cash Management:

```java
@DataSynchronization(entity = "FIN_Finacc_Transaction")
public class ProcessCashMgmt extends POSDataSynchronizationProcess {
  
  @Override
  public JSONObject saveRecord(JSONObject jsonsent) throws Exception {
    // 1. Actualiza el reporte de cashup (UpdateCashup.getAndUpdateCashUp)
    // 2. Crea FIN_FinaccTransaction en la cuenta financiera
    //    - Type 'drop' → setPaymentAmount, tipo "BPW", status "PWNC"
    //    - Type 'deposit' → setDepositAmount, tipo "BPD", status "RDNC"
    // 3. Actualiza balance de la cuenta financiera
    // 4. Guarda evento en OBPOS_PAYMENTCASHUP_EVENTS
    // 5. Ejecuta hooks (ProcessCashMgmtHook)
    // 6. Si hay moneda extranjera → crea ConversionRateDoc
  }
}
```

#### 5.5.2. Flujo de Procesamiento en Backend

```
ImportEntry (JSON) ──▶ CashManagementImportEntryProcessor
                              │
                              ▼
                      ProcessCashMgmt.saveRecord()
                              │
                    ┌─────────┼──────────┐
                    ▼         ▼          ▼
            Update       Create        Save
            Cashup    FIN_Finacc     Payment
            Report    Transaction    Cashup Event
                              │
                              ▼
                    Execute ProcessCashMgmtHook
                    (para extensiones como Gift Cards)
```

#### 5.5.3. GL Items (Razones de Movimiento)

Los GL Items (General Ledger Items) definen las razones disponibles para depósitos y retiros. Se configuran en el tipo de método de pago del terminal:

- `GLItemForDeposits` — razones para depósitos
- `GLItemForDrops` — razones para retiros
- `GLItemDropDep` — razones compartidas deposit/drop
- `GLItemWriteoff` — razones de ajuste
- `CashDifferences` — diferencias de caja

---

## 6. Cash Up (Cierre de Caja / Arqueo)

### 6.1. Concepto

El Cash Up es el proceso de **cierre y arqueo de caja**, donde el operador:
1. Resuelve tickets pendientes
2. Cuenta el efectivo físico por método de pago
3. Compara con el esperado del sistema
4. Decide cuánto mantener en caja
5. Envía la información al backend para reconciliación

### 6.2. Wizard de 6 Pasos

```
┌─────────────────────────────────────────────────────────────┐
│                    CASH UP WIZARD                            │
│                                                              │
│  PASO 1: Pending Orders (Tickets Pendientes)                │
│  ├── Muestra tickets sin finalizar                          │
│  ├── El operador debe procesarlos o eliminarlos             │
│  └── No permite avanzar si hay pendientes                   │
│                                                              │
│  PASO 2: Master Terminal (Solo para Master)                 │
│  ├── Muestra estado de terminales slave                     │
│  ├── Espera que todos los slaves completen su cashup        │
│  └── Activo solo si terminal.ismaster = true                │
│                                                              │
│  PASO 3: Cash Payments (Conteo de Monedas/Billetes)        │
│  ├── Solo para métodos de pago con iscash && countcash      │
│  ├── Permite contar por denominación (countPerAmount)       │
│  └── Sub-pasos: uno por método de pago cash                │
│                                                              │
│  PASO 4: Payment Methods (Conteo General)                   │
│  ├── Lista todos los métodos de pago                        │
│  ├── Muestra Expected (esperado del sistema)                │
│  ├── Operador ingresa Counted (contado real)                │
│  ├── Calcula Difference automáticamente                     │
│  └── Requiere que todos estén contados para avanzar         │
│                                                              │
│  PASO 5: Cash to Keep (Efectivo a Mantener)                 │
│  ├── Solo para pagos con automatemovementtoother            │
│  ├── Opciones:                                              │
│  │   ├── Mover todo (qtyToKeep = 0)                        │
│  │   ├── No mover nada (qtyToKeep = foreignCounted)        │
│  │   ├── Monto fijo (qtyToKeep = paymentMethod.amount)     │
│  │   └── Monto variable (input del operador)               │
│  └── Sub-pasos: uno por método de pago habilitado          │
│                                                              │
│  PASO 6: Post Print & Close (Impresión y Cierre)           │
│  ├── Muestra resumen completo del cierre                   │
│  ├── Imprime ticket de cierre si tiene permiso             │
│  └── Ejecuta completeCashupAndCreateNew()                  │
└─────────────────────────────────────────────────────────────┘
```

### 6.3. Modelo de Datos del Wizard

**Archivo:** `js/closecash/model/cashup/cashup-model.js`

```javascript
OB.OBPOSCashUp.Model.CashUp = OB.OBPOSCloseCash.Model.CloseCash.extend({
  stepsDefinition: [
    { name: 'OB.CashUp.StepPendingOrders', active: true },
    { name: 'OB.CashUp.Master', active: false },     // Solo si ismaster
    { name: 'OB.CloseCash.CashPayments', active: true },
    { name: 'OB.CloseCash.PaymentMethods', active: true },
    { name: 'OB.CloseCash.CashToKeep', active: true },
    { name: 'OB.CloseCash.PostPrintAndClose', active: true }
  ]
});
```

### 6.4. Cálculos del Cash Up

#### 6.4.1. Expected (Esperado)

```
Expected = startingCash + totalSales - totalReturns + totalDeposits - totalDrops
```

Cada componente es acumulado durante la jornada por `countTicketInCashup()` (ventas/devoluciones) y `processCashManagements()` (depósitos/retiros).

#### 6.4.2. Difference (Diferencia)

```
Difference = Counted - Expected
```

Si la diferencia supera un umbral configurable, puede requerirse aprobación de un supervisor.

#### 6.4.3. Close Cash Info (Información de Cierre)

```javascript
cashCloseInfo = [{
  id: "uuid",
  paymentTypeId: "payment_method_id",
  expected: 1500.00,
  foreignExpected: 1500.00,
  difference: -25.00,
  foreignDifference: -25.00,
  paymentMethod: {
    amountToKeep: 500.00     // ← Se convierte en startingCash del siguiente cashup
  }
}]
```

### 6.5. Acción `completeCashupAndCreateNew`

**Archivo:** `app/model/business-object/cashup/actions/CompleteCashupAndCreateNew.js`

Este es el proceso central del cierre:

```javascript
completeCashupAndCreateNew(state, payload) {
  // 1. Marca cashup actual como isprocessed = 'Y'
  // 2. Filtra métodos de pago relevantes
  // 3. Crea Message con tipo 'OBPOS_CashUp' → ProcessCashClose
  //    Incluye: cashup completo + closeCashupInfo + estadísticas
  // 4. Crea nuevo cashup desde scratch
  // 5. Calcula startingCash del nuevo cashup desde cashCloseInfo.amountToKeep
  // 6. Si es master/slave → envía message adicional del nuevo cashup
  // 7. Resetea estadísticas del terminal
}
```

### 6.6. Acumulación de Ticket en el Cashup

**Archivo:** `app/model/business-object/cashup/CashupUtils.js` → `countTicketInCashup()`

Cada vez que se completa un ticket (venta o devolución), se acumula en el cashup:

```javascript
countTicketInCashup(cashup, ticket, countLayawayAsSales, terminalPayments) {
  // 1. Calcula netSales, grossSales, netReturns, grossReturns por línea
  // 2. Si hay moneda extranjera → convierte usando rate
  // 3. Acumula en cashup.netSales, grossSales, etc.
  // 4. Agrupa y acumula impuestos en cashTaxInfo
  // 5. Acumula pagos en cashPaymentMethodInfo:
  //    - totalSales para pagos positivos
  //    - totalReturns para pagos negativos
  // 6. Guarda initialTicketDate (fecha del primer ticket)
}
```

### 6.7. Partial Cash Up

Existe un modo **Cash Up Parcial** que solo imprime el reporte sin cerrar la caja:

```javascript
OB.OBPOSCashUp.Model.CashUpPartial = OB.OBPOSCashUp.Model.CashUp.extend({
  initialStep: 6,  // Salta directamente al último paso
  isPartialCashup: true,
  processAndFinish: function() {
    // Solo imprime, no cierra
    this.printCloseCash.print(report, summary, false, callback);
  }
});
```

---

## 7. Close Till (Cierre Final de Terminal)

### 7.1. Concepto

El "Close Till" es la fase de **procesamiento backend** que ocurre después de que el operador completa el Cash Up. Aunque el término no existe explícitamente en el código como una funcionalidad separada, las operaciones de cierre final son ejecutadas por el backend al recibir el mensaje del cashup.

### 7.2. Backend: ProcessCashClose

**Archivo:** `src/org/openbravo/retail/posterminal/ProcessCashClose.java`

```java
@DataSynchronization(entity = "OBPOS_App_Cashup")
public class ProcessCashClose extends POSDataSynchronizationProcess {
  
  public JSONObject saveRecord(JSONObject jsonCashup) throws Exception {
    // 1. Obtener y actualizar el registro de cashup en BD
    OBPOSAppCashup cashUp = UpdateCashup.getAndUpdateCashUp(cashUpId, jsonCashup, cashUpDate);
    
    // 2. Si está procesado pero no por el back-office:
    if (cashUp.isProcessed() && !cashUp.isProcessedbo()) {
      
      // 3. Manejar Safe Box (si aplica)
      //    - Actualizar historial de safe box
      //    - Crear transacciones de safe box
      
      // 4. Guardar aprobaciones (si hubo diferencias aprobadas)
      
      // 5. Guardar timestamp del último cashup completado
      
      // 6. Según tipo de terminal:
      //    SLAVE → Solo marcar como processedbo
      //    MASTER → Reconciliar slaves + master
      //    STANDALONE → Reconciliar solo este terminal
      
      // 7. doReconciliationAndInvoices()
    }
  }
}
```

### 7.3. CashCloseProcessor: Reconciliación

**Archivo:** `src/org/openbravo/retail/posterminal/CashCloseProcessor.java`

El procesador de cierre ejecuta las siguientes operaciones financieras:

```
┌─────────────────────────────────────────────────────────┐
│              RECONCILIATION PROCESS                      │
│                                                          │
│  Para cada método de pago en cashCloseInfo:              │
│                                                          │
│  1. DIFERENCIA                                           │
│     Si difference ≠ 0:                                   │
│     → Crear FIN_FinaccTransaction (tipo diferencia)      │
│     → GLItem: CashDifferences del payment method         │
│                                                          │
│  2. RECONCILIATION                                       │
│     → Crear FIN_Reconciliation                          │
│     → Asociar todas las transacciones del período        │
│                                                          │
│  3. CASH RECONCIL                                        │
│     → Crear OBPOS_APP_CASH_RECONCIL                     │
│     → Enlace cashup ↔ reconciliation ↔ payment method   │
│                                                          │
│  4. TRANSFER (si hay monto a transferir)                 │
│     → total = foreignExpected + foreignDifference        │
│          - amountToKeep                                  │
│     → Si total ≠ 0:                                      │
│       a. Transacción PAGO (retiro de Financial Account)  │
│       b. Transacción DEPÓSITO (ingreso en cuenta destino)│
│                                                          │
│  5. SAFE BOX (métodos definidos en safe box)             │
│     → Reconciliación separada para pagos de safe box     │
│                                                          │
│  6. DOCUMENT NUMBERS                                     │
│     → Asignar DocumentNo a cada reconciliación           │
│                                                          │
│  7. HOOKS                                                │
│     → Ejecutar CashupHook implementations                │
│     → Marcar cashup como processedbo = true              │
└─────────────────────────────────────────────────────────┘
```

### 7.4. UpdateCashup: Persistencia

**Archivo:** `src/org/openbravo/retail/posterminal/UpdateCashup.java`

Actualiza o crea el registro de cashup en la base de datos:

```java
public static OBPOSAppCashup getAndUpdateCashUp(String cashUpId, 
    JSONObject jsonCashup, Date cashUpDate) {
  // 1. Lock FOR UPDATE (evita procesamiento duplicado)
  // 2. Si no existe → crear nuevo OBPOSAppCashup
  // 3. Si existe → actualizar campos:
  //    - netSales, grossSales, netReturns, grossReturns
  //    - totalRetailTransactions, isProcessed
  //    - cashUpDate, lastCashUpReportDate
  // 4. Crear/actualizar PaymentMethodCashup por cada método de pago
  // 5. Crear/actualizar TaxCashup por cada impuesto
  // 6. Asociar master/slave si corresponde
  // 7. Actualizar Terminal Status History
}
```

### 7.5. Acciones Post-Cierre en Frontend

Después de que `completeCashupAndCreateNew` se ejecuta exitosamente:

```javascript
callbackFinishedSuccess = () => {
  // 1. Resetear estadísticas
  OB.App.State.Cashup.Utils.resetStatisticsIncludedInCashup();
  
  // 2. Imprimir reporte (si tiene permiso OBPOS_print.cashup)
  this.printCloseCash.print(cashUpReport, countCashSummary, true, null);
  
  // 3. Limpiar safe box local
  OB.UTIL.localStorage.removeItem('currentSafeBox');
  
  // 4. Si es safe box mode → logout
  // 5. Si no → navegar a POS
};
```

---

## 8. Modelo de Datos

### 8.1. Diagrama Entidad-Relación

```
┌──────────────────────┐     ┌──────────────────────────┐
│  OBPOS_APPLICATIONS  │     │    OBPOS_APP_CASHUP      │
│  (Terminal POS)      │     │    (Cashup/Arqueo)       │
│──────────────────────│     │──────────────────────────│
│  id                  │◄───┐│  id                      │
│  searchKey           │    ││  pOSTerminal (FK)        │───▶ OBPOS_APPLICATIONS
│  name                │    ││  userContact (FK)        │───▶ AD_USER
│  ismaster            │    ││  cashUpDate              │
│  masterterminal (FK) │    ││  creationDate            │
│  organization (FK)   │    ││  netSales                │
│  currency (FK)       │    ││  grossSales              │
└──────────────────────┘    ││  netReturns              │
                            ││  grossReturns            │
                            ││  totalRetailTransactions │
                            ││  isProcessed             │
                            ││  isProcessedbo           │
                            ││  jsonCashup              │
                            ││  obposParentCashup (FK)  │
                            ││  lastCashUpReportDate    │
                            │└──────────────────────────┘
                            │            │
                            │            │ 1:N
                            │            ▼
                            │ ┌─────────────────────────────┐
                            │ │ OBPOS_PAYMENTMETHODCASHUP   │
                            │ │ (Método Pago x Cashup)      │
                            │ │─────────────────────────────│
                            │ │  id                         │
                            │ │  cashUp (FK)                │───▶ OBPOS_APP_CASHUP
                            │ │  paymentType (FK)           │───▶ OBPOS_APP_PAYMENT
                            │ │  searchKey                  │
                            │ │  name                       │
                            │ │  startingCash               │
                            │ │  totalSales                 │
                            │ │  totalReturns               │
                            │ │  totalDeposits              │
                            │ │  totalDrops                 │
                            │ │  rate                       │
                            │ │  amountToKeep               │
                            │ └─────────────────────────────┘
                            │            │
                            │            │ 1:N
                            │            ▼
                            │ ┌─────────────────────────────┐
                            │ │ OBPOS_PAYMENTCASHUP_EVENTS  │
                            │ │ (Eventos Cash Management)   │
                            │ │─────────────────────────────│
                            │ │  id                         │
                            │ │  paymentMethodCashup (FK)   │
                            │ │  name (description)         │
                            │ │  amount                     │
                            │ │  type (deposit/drop)        │
                            │ │  currency                   │
                            │ │  FINFinaccTransaction (FK)  │
                            │ └─────────────────────────────┘
                            │
   ┌────────────────────────┘
   │
   │  1:N
   ▼
┌──────────────────────────┐
│    OBPOS_TAXCASHUP       │
│    (Impuestos x Cashup)  │
│──────────────────────────│
│  id                      │
│  cashUp (FK)             │───▶ OBPOS_APP_CASHUP
│  name                    │
│  amount                  │
│  orderType (0=sale,1=ret)│
└──────────────────────────┘

┌──────────────────────────┐
│  OBPOS_CASHUP_APPROVAL   │
│  (Aprobaciones Cashup)   │
│──────────────────────────│
│  id                      │
│  cashUp (FK)             │───▶ OBPOS_APP_CASHUP
│  approvalType            │
│  approvalMessage         │
│  approvalReason (FK)     │
│  supervisor (FK)         │───▶ AD_USER
└──────────────────────────┘

┌──────────────────────────┐     ┌──────────────────────────┐
│ OBPOS_APP_CASH_RECONCIL  │     │  FIN_RECONCILIATION      │
│ (Enlace Cashup↔Reconc.)  │     │  (Reconciliación Bancaria│
│──────────────────────────│     │──────────────────────────│
│  id                      │     │  id                      │
│  cashUp (FK)             │     │  account (FK)            │
│  reconciliation (FK)     │────▶│  documentNo              │
│  paymentMethod (FK)      │     │  startingBalance         │
│  terminal (FK)           │     │  endingBalance           │
└──────────────────────────┘     │  transactionDate         │
                                 └──────────────────────────┘
                                          │ 1:N
                                          ▼
                                 ┌──────────────────────────┐
                                 │ FIN_FINACC_TRANSACTION   │
                                 │ (Transacciones Financ.)  │
                                 │──────────────────────────│
                                 │  id                      │
                                 │  account (FK)            │
                                 │  obposAppCashup (FK)     │
                                 │  reconciliation (FK)     │
                                 │  glItem (FK)             │
                                 │  paymentAmount           │
                                 │  depositAmount           │
                                 │  transactionType         │
                                 │    (BPW=retiro,BPD=dep)  │
                                 │  status                  │
                                 │  description             │
                                 └──────────────────────────┘
```

### 8.2. Safe Box (Caja Fuerte)

```
┌──────────────────────────┐
│     OBPOS_SAFEBOX        │
│──────────────────────────│     ┌────────────────────────────┐
│  id                      │     │  OBPOS_SAFEBOX_TOUCHPOINT  │
│  searchKey               │     │────────────────────────────│
│  name                    │◄────│  safeBox (FK)              │
│  organization (FK)       │     │  terminal (FK)             │
└──────────────────────────┘     └────────────────────────────┘
         │ 1:N
         ▼
┌──────────────────────────────┐
│  OBPOS_SAFEBOX_PAYMENTMETHOD │
│──────────────────────────────│
│  id                          │
│  safeBox (FK)                │
│  paymentMethod (FK)          │
│  financialAccount (FK)       │
└──────────────────────────────┘
         │ 1:N
         ▼
┌──────────────────────────┐
│   OBPOS_SAFEBOX_COUNT    │
│──────────────────────────│     ┌───────────────────────────┐
│  id                      │     │  OBPOS_SAFEBOX_COUNT_PM   │
│  safeBox (FK)            │     │───────────────────────────│
│  cashUp (FK)             │     │  id                       │
│  countDate               │◄────│  safeboxCount (FK)        │
└──────────────────────────┘     │  paymentMethod (FK)       │
                                 │  amount                   │
                                 └───────────────────────────┘
```

---

## 9. Arquitectura Master/Slave

### 9.1. Concepto

Openbravo POS soporta una arquitectura donde un terminal **Master** gestiona uno o más terminales **Slave**. Los métodos de pago pueden ser **compartidos** (shared) entre terminales.

### 9.2. Flujo de Cash Up en Master/Slave

```
SLAVE TERMINAL 1                SLAVE TERMINAL 2
      │                               │
 Cash Up Parcial              Cash Up Parcial
      │                               │
 ProcessCashCloseSlave         ProcessCashCloseSlave
      │                               │
 Marcar cashup como            Marcar cashup como
 processed + processedbo       processed + processedbo
      │                               │
      └──────────┬────────────────────┘
                 │
                 ▼
         MASTER TERMINAL
                 │
         Cash Up Step 2:
         "Master Terminal"
         (Espera slaves)
                 │
         completeCashupAndCreateNew()
                 │
         ProcessCashClose (Backend):
         1. Procesar slaves (reconciliación)
         2. Procesar master (reconciliación)
         3. Acumular PaymentMethodCashup compartidos
```

### 9.3. Métodos de Pago Compartidos

Cuando un método de pago es `isshared = true`:

- El **startingCash** del slave es 0 (no cuenta saldo compartido)
- Solo el **master** acumula y reconcilia los pagos compartidos
- `ProcessCashCloseMaster` agrega la información acumulada de todos los slaves

### 9.4. Asociación de Cashups

```java
// En UpdateCashup.associateMasterSlave():
// Si el terminal es master → busca slaves sin cashup padre
// Si el terminal es slave → busca el cashup del master
// Establece la relación obposParentCashup
```

---

## 10. Personalizaciones Halsteds

### 10.1. Multi-moneda (ZWL/USD)

En `cashup-model.js`, se observa una personalización específica para Halsteds que maneja la tasa de cambio ZWL (Dólar de Zimbabwe) a USD:

```javascript
let rateZWLToUSD = OB.POS.modelterminal.get('rates').filter(function(rate) {
  return rate.isoCode === 'ZWL' && rate.toIsoCode === 'USD';
});
OB.App.State.getState().Cashup.cashPaymentMethodInfo.forEach(cashPayment => {
  if (cashPayment.searchKey === 'POSCUR_payment.CASH_ZWL') {
    cashPayment.rate = rateZWLToUSD[0].multRate;
  }
});
```

### 10.2. Conversión de Moneda en Ventas

En `CashupUtils.js` → `countTicketInCashup()`, hay lógica personalizada para convertir montos cuando la moneda del ticket difiere de la del terminal:

```javascript
if (currency !== OB.MobileApp.model.get('terminal').currency) {
  rate = _.find(OB.POS.modelterminal.get('rates'), function(line) {
    return line.currId === currency && 
           line.toCurrId === OB.MobileApp.model.get('terminal').currency;
  });
  round = 20;
  netSales = OB.DEC.mul(netSales, rate.multRate, round);
  // ... conversión de grossSales, netReturns, grossReturns
}
```

### 10.3. EcoCash (Pagos Móviles)

El módulo `com.doceleguas.halsteds.webpos.customization` agrega soporte para pagos EcoCash:

- **ECO_CHANGE_USD**: Deshabilitado en el conteo de caja (`tabcountcash.js`)
- **Transacciones EcoCash**: Modelo y sincronización separados
- **Integration hooks**: `ecocash_hook.js` para intercepción de pagos

### 10.4. Módulo Change Funds (SMF)

El módulo `com.smf.pos.change.funds` extiende la funcionalidad de cambio al cliente:
- Gestión de cambio por Business Partner
- Hooks de pago personalizados
- Utilidades adicionales para cashup

---

## 11. Índice de Archivos

**Ver:** [Anexo A - Índice Completo de Archivos](POS_Cash_Management_Anexo_A_Archivos.md)

**Ver:** [Anexo B - Referencia Técnica de APIs](POS_Cash_Management_Anexo_B_APIs.md)

---

## Glosario

| Término | Descripción |
|---------|-------------|
| **Cashup** | Registro que agrupa toda la actividad financiera de un turno de caja |
| **Cash Close** | Proceso backend de cierre y reconciliación |
| **Cash Management** | Depósitos y retiros durante la jornada |
| **Starting Cash** | Saldo inicial de un método de pago al abrir caja |
| **Expected** | Monto esperado = Starting + Sales - Returns + Deposits - Drops |
| **Counted** | Monto contado físicamente por el operador |
| **Difference** | Counted - Expected (diferencia de caja) |
| **Amount to Keep** | Monto que el operador decide dejar en caja para el siguiente turno |
| **GL Item** | Concepto contable asociado a movimientos de Cash Management |
| **Payment Method Cashup** | Registro detallado por método de pago dentro de un cashup |
| **Import Entry** | Registro de sincronización entre POS y backend (cola asíncrona) |
| **Reconciliation** | Proceso de conciliación bancaria del Financial Account |
| **Safe Box** | Caja fuerte donde se depositan fondos al final del turno |
| **Master Terminal** | Terminal principal que gestiona terminales slave |
| **Slave Terminal** | Terminal secundario vinculado a un master |
| **Shared Payment** | Método de pago compartido entre master y slaves |
| **Draft** | Estado temporal de un Cash Management antes de ser procesado |
| **processedbo** | Flag que indica procesamiento completo por el back-office |
