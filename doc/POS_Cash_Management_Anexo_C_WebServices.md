# Anexo C: WebServices Directos para Cash Management y Cash Up

> **Referencia para:** [Análisis Principal](POS_Cash_Management_Analysis.md) | [Anexo B: APIs](POS_Cash_Management_Anexo_B_APIs.md)

---

## 1. Introducción

Este módulo implementa **7 WebServices directos** en el paquete `com.doceleguas.pos.webservices.cashup` que replican la funcionalidad de los endpoints backend que el POS consulta a través del framework Mobile Service Request de Openbravo. Estos WebServices permiten invocar la misma lógica sin depender de la infraestructura de mensajería/sincronización de Mobile Core.

### 1.1. Motivación

Los Mobile Service Requests de Openbravo procesan las peticiones a través de una cola de Import Entries con procesamiento asíncrono. Esto añade complejidad y latencia. Los WebServices directos:

- Eliminan la dependencia del framework Mobile Core para estos flujos
- Permiten llamadas HTTP directas (sincrónicas) desde cualquier cliente
- Simplifican la integración con sistemas externos
- Mantienen la misma lógica de negocio, hooks y validaciones

### 1.2. Patrón de Implementación

Todos los WebServices implementan `org.openbravo.service.web.WebService` y se registran en:

```
config/com.doceleguas.pos.webservices-provider-config.xml
```

Las URLs resultantes siguen el patrón:

```
https://{host}/openbravo/ws/{BeanName}
```

#### Acceso a Datos: Native SQL

Siguiendo el mismo patrón de los WebServices ya existentes en el módulo (como `GetOrder`, `GetOrdersFilter` y `SaveBusinessPartner`), todos los queries de lectura (SELECT) utilizan **native SQL con nombres reales de columnas de la BD**:

- **Endpoints GET**: `PreparedStatement` sobre `OBDal.getInstance().getConnection()` con parámetros posicionales (`?`)
- **Endpoints POST (lecturas)**: `PreparedStatement` para lookups y validaciones
- **Endpoints POST (escrituras)**: DAL (`OBProvider` + `OBDal.save()`) únicamente donde las entidades creadas son requeridas por hooks de Openbravo o utilidades como `UpdateCashup`, `CashCloseProcessor`, `TransactionsDao`

**No se utiliza HQL ni OBCriteria** en la lógica interna de estos WebServices. Los `OBDal.get(Entity.class, id)` se usan puntualmente para cargar entidades por PK que son necesarias para invocar utilidades de Openbravo que requieren objetos DAL.

### 1.3. Funcionalidades Excluidas

Por decisión de diseño, se excluyeron:

- **SafeBox**: Lógica de caja fuerte (`safeboxTransaction` en `ProcessCashClose`)
- **Gift Cards**: Hook `ProcessCashCloseGiftCardHook`

---

## 2. Tabla Resumen de Endpoints

| # | WebService | Método | URL | Clase Original Openbravo |
|---|-----------|--------|-----|--------------------------|
| 1 | `GetCashup` | GET | `/ws/GetCashup` | `o.o.r.posterminal.master.Cashup` |
| 2 | `ProcessCashManagement` | POST | `/ws/ProcessCashManagement` | `o.o.r.posterminal.ProcessCashMgmt` |
| 3 | `ProcessCashClose` | POST | `/ws/ProcessCashClose` | `o.o.r.posterminal.ProcessCashClose` |
| 4 | `CashCloseMaster` | GET | `/ws/CashCloseMaster` | `o.o.r.posterminal.ProcessCashCloseMaster` |
| 5 | `CashCloseSlave` | GET | `/ws/CashCloseSlave` | `o.o.r.posterminal.ProcessCashCloseSlave` |
| 6 | `CashMgmtMaster` | GET | `/ws/CashMgmtMaster` | `o.o.r.posterminal.ProcessCashMgmtMaster` |
| 7 | `GetCashMgmtEvents` | GET | `/ws/GetCashMgmtEvents` | `o.o.r.posterminal.term.CashMgmtDepositEvents` / `CashMgmtDropEvents` |

> **Nota**: Las URLs completas se forman con el prefijo `https://{host}/openbravo` seguido de la ruta indicada.

---

## 3. Detalle de Endpoints

### 3.1. GetCashup — Consulta de Cashup

**Equivale a:** `org.openbravo.retail.posterminal.master.Cashup` (extiende `JSONProcessSimple`)

**Método:** `GET`

**URL:** `/ws/GetCashup?pos={terminalId}&isprocessed={Y|N}`

#### Parámetros

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `pos` | String (UUID) | Sí | ID del terminal POS (`OBPOS_Applications`) |
| `isprocessed` | String | Sí | `Y` para obtener el último procesado, `N` para el actual |
| `isprocessedbo` | String | No | Filtro adicional por estado de procesamiento BackOffice |
| `client` | String (UUID) | No | ID del cliente (se usa para setear `OBContext`) |
| `organization` | String (UUID) | No | ID de la organización |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": [{
    "id": "UUID-cashup",
    "searchKey": "CASHUP/0001",
    "netSales": 1500.00,
    "grossSales": 1725.00,
    "netReturns": -100.00,
    "grossReturns": -115.00,
    "totalRetailTransactions": 25,
    "creationDate": "2024-01-15T08:00:00",
    "isprocessed": false,
    "isprocessedbo": false,
    "cashUpDate": "2024-01-15",
    "obposApplications": "UUID-terminal",
    "obposAppCashupCashManagementList": [...],
    "obposAppCashupPaymentmethodcashupList": [
      {
        "id": "UUID",
        "paymentMethodId": "UUID-pm",
        "name": "Efectivo",
        "startingCash": 50000.00,
        "totalSales": 120000.00,
        "totalReturns": -5000.00,
        "rate": 1.0,
        "isocode": "ZAR",
        "countPerAmount": [
          {"coinValue": 0.10, "numberOfCoins": 50},
          {"coinValue": 0.50, "numberOfCoins": 30}
        ]
      }
    ],
    "obposAppCashupTaxcashupList": [
      {
        "id": "UUID",
        "name": "VAT 15%",
        "orderAmount": 225.00,
        "returnAmount": -15.00
      }
    ]
  }]
}
```

#### Lógica Interna

1. Ejecuta native SQL sobre `OBPOS_App_Cashup` filtrando por `Obpos_Applications_ID`, `Isprocessed` y opcionalmente `Isprocessedbo`, `ORDER BY Created DESC LIMIT 1`
2. Verifica errores del terminal con query nativo sobre `OBPOS_Errors` y `C_Import_Entry`
3. Enriquece el resultado con:
   - **Payments**: native SQL con JOIN entre `OBPOS_paymentmethodcashup`, `OBPOS_APP_PAYMENT` y `OBPOS_App_Payment_Type` (para `Line` y `Countperamount`). Si `Countperamount = 'Y'`, carga denominaciones de `obpos_pmcashup_amntcnt`
   - **Taxes**: native SQL sobre `OBPOS_taxcashup`
   - **Cash Management**: native SQL con JOIN entre `FIN_Finacc_Transaction`, `OBPOS_APP_PAYMENT`, `FIN_Financial_Account`, `C_Currency` y `AD_User`. Filtra por GL Items recolectados via UNION de las 5 columnas FK de `OBPOS_App_Payment_Type` (`C_Glitem_Dropdep_ID`, `C_Glitem_Writeoff_ID`, `C_Glitem_Diff_ID`, `C_Glitem_Deposits_ID`, `C_Glitem_Drops_ID`)

#### Nota sobre GL Items

La versión anterior utilizaba `ExtendsCashManagementPaymentTypeHook` para extender queries HQL con paths de relaciones adicionales. En la implementación con native SQL, los GL Items se resuelven directamente consultando las 5 columnas FK de `OBPOS_App_Payment_Type`, lo que elimina la dependencia del hook HQL.

---

### 3.2. ProcessCashManagement — Procesar Movimiento de Caja

**Equivale a:** `org.openbravo.retail.posterminal.ProcessCashMgmt`

**Método:** `POST`

**URL:** `/ws/ProcessCashManagement`

#### Request Body (JSON)

```json
{
  "id": "UUID-transaccion",
  "cashUpId": "UUID-cashup",
  "paymentMethodId": "UUID-paymentMethod",
  "amount": 5000.00,
  "origAmount": 5000.00,
  "type": "drop",
  "description": "Retiro diario",
  "reasonId": "UUID-glItem",
  "destinationPaymentMethodId": "UUID-destPM",
  "date": "2024-01-15",
  "timezoneOffset": 120,
  "user": "UUID-usuario",
  "organization": "UUID-org"
}
```

#### Campos del Request

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `id` | String (UUID) | Sí | ID de la transacción |
| `cashUpId` | String (UUID) | Sí | ID del cashup activo |
| `paymentMethodId` | String (UUID) | Sí | Método de pago origen |
| `amount` | Number | Sí | Monto de la transacción |
| `origAmount` | Number | Sí | Monto original (antes de conversión) |
| `type` | String | Sí | Tipo: `"drop"` (retiro) o `"deposit"` (depósito) |
| `description` | String | No | Descripción del movimiento |
| `reasonId` | String (UUID) | Sí | GL Item (razón del movimiento) |
| `destinationPaymentMethodId` | String (UUID) | No | Método de pago destino (para transferencias) |
| `date` | String | Sí | Fecha de la transacción |
| `timezoneOffset` | Number | No | Offset de timezone en minutos |
| `user` | String (UUID) | No | ID del usuario que realiza la operación |
| `organization` | String (UUID) | No | ID de la organización |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": {
    "id": "UUID-transaccion",
    "message": "Cash management processed successfully"
  }
}
```

#### Lógica Interna

1. **Actualización de Cashup**: invoca `UpdateCashup.getAndUpdateCashUp()` con bloqueo `SELECT FOR UPDATE`
2. **Lookup de PaymentMethodCashup**: native SQL sobre `OBPOS_paymentmethodcashup WHERE Obpos_App_Payment_ID = ? AND Obpos_App_Cashup_ID = ?`
3. **Lookup de Financial Account**: native SQL sobre `OBPOS_APP_PAYMENT JOIN FIN_Financial_Account` para obtener IDs
4. **Lookup de GL Item**: native SQL sobre `OBPOS_APP_PAYMENT JOIN OBPOS_App_Payment_Type` para obtener `C_Glitem_Dropdep_ID`
5. **Carga de entidades**: `OBDal.get()` por PK para `OBPOSAppPayment`, `FIN_FinancialAccount`, `OBPOSPaymentMethodCashup` (requeridos por hooks y `TransactionsDao`)
6. **Transacción Primaria**: crea `FIN_FinaccTransaction` via DAL (OBProvider) con los campos: Account, Currency, Line (TransactionsDao.getTransactionMaxLineNo), GLItem, DateAcct, Status, Amounts, TransactionType
   - Para **drop** (retiro): tipo `BPW`, paymentAmount = amount
   - Para **deposit** (depósito): tipo `BPD`, depositAmount = amount
7. **Evento de Cashup**: crea registro `OBPOSPaymentcashupEvents` via DAL vinculando al cashup, payment method, y transacción
8. **Conversión de Moneda**: si `foreignCurrencyId` está presente, crea registro `ConversionRateDoc` via DAL
9. Ejecuta todos los `ProcessCashMgmtHook` via CDI programático
10. Hace `OBDal.getInstance().flush()` para persistir

#### Hooks Ejecutados

| Hook | Descripción |
|------|-------------|
| `ProcessCashMgmtHook` | Se ejecuta después de procesar cada transacción de cash management. Recibe `cashUpId` y `jsonRecord` |

---

### 3.3. ProcessCashClose — Cierre de Caja

**Equivale a:** `org.openbravo.retail.posterminal.ProcessCashClose`

**Método:** `POST`

**URL:** `/ws/ProcessCashClose`

#### Request Body (JSON)

```json
{
  "id": "UUID-cashup",
  "cashUpDate": "2024-01-15",
  "timezoneOffset": 120,
  "approvals": [
    {
      "approvalType": "OBPOS_approval.cashupaliases.close",
      "userId": "UUID-supervisor"
    }
  ],
  "cashCloseInfo": [
    {
      "paymentMethodId": "UUID-pm",
      "expected": 175000.00,
      "counted": 174500.00,
      "difference": -500.00,
      "qtyToKeep": 50000.00,
      "qtyToDeposit": 124500.00
    }
  ],
  "cashMgmtIds": ["UUID-1", "UUID-2"],
  "isMaster": false,
  "isSlave": false,
  "user": "UUID-usuario",
  "organization": "UUID-org"
}
```

#### Campos del Request

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `id` | String (UUID) | Sí | ID del cashup a cerrar |
| `cashUpDate` | String | Sí | Fecha de cierre |
| `timezoneOffset` | Number | No | Offset de timezone en minutos |
| `approvals` | Array | No | Aprobaciones de supervisor para el cierre |
| `cashCloseInfo` | Array | No | Detalle de conteo por método de pago |
| `cashMgmtIds` | Array | No | IDs de transacciones de cash management asociadas |
| `isMaster` | Boolean | No | Si es terminal maestro (default `false`) |
| `isSlave` | Boolean | No | Si es terminal esclavo (default `false`) |
| `user` | String (UUID) | No | ID del usuario |
| `organization` | String (UUID) | No | ID de la organización |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": {
    "id": "UUID-cashup",
    "message": "Cash close processed successfully"
  }
}
```

#### Lógica Interna

1. **Verificación de reconciliaciones**: native SQL con JOIN entre `FIN_Reconciliation`, `FIN_Financial_Account`, `OBPOS_APP_PAYMENT` y `OBPOS_paymentmethodcashup` verificando `Docstatus = 'DR'`
2. **Terminal lookup**: native SQL sobre `OBPOS_App_Cashup` para obtener `Obpos_Applications_ID`
3. **Carga de entidades**: `OBDal.get()` por PK para `OBPOSApplications` y `OBPOSAppCashup` (requeridos por `CashCloseProcessor` y `OrderGroupingProcessor`)
4. **Actualización de Cashup**: `UpdateCashup.getAndUpdateCashUp()` con bloqueo
5. **Aprobaciones**: native SQL INSERT en `OBPOS_Cashup_Approval` con batch de `PreparedStatement`
6. **Master terminal check**: native SQL sobre `OBPOS_APPLICATIONS` para verificar `Ismaster = 'Y'`
7. **Acumulación de Payment Methods** (solo master): native SQL con `SUM`/`GROUP BY` sobre `OBPOS_paymentmethodcashup` para cashups slaves, y `UPDATE` batch sobre los registros del master
8. **Agrupamiento de Pedidos**: `OrderGroupingProcessor.groupOrders()` (requiere entity objects)
9. **Hooks CashupHook**: ejecutados via CDI programático
10. **Cierre de caja**: `CashCloseProcessor.processCashClose()` con la lista de slave cashup IDs
11. `OBDal.getInstance().flush()`

#### Hooks Ejecutados

| Hook | Descripción |
|------|-------------|
| `CashupHook` | Se ejecuta al inicio del procesamiento de cash close. Recibe el `cashUpId` y el `JSONObject` completo |

#### Nota sobre SafeBox

La lógica de SafeBox (`safeboxTransaction`) del ProcessCashClose original fue **excluida** de esta implementación. Si se requiere en el futuro, se debe agregar la creación de `FIN_FinaccTransaction` adicionales con tipo `BPW`/`BPD` hacia la cuenta de la SafeBox.

---

### 3.4. CashCloseMaster — Estado del Cierre Master

**Equivale a:** `org.openbravo.retail.posterminal.ProcessCashCloseMaster`

**Método:** `GET`

**URL:** `/ws/CashCloseMaster?pos={masterTerminalId}`

#### Parámetros

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `pos` | String (UUID) | Sí | ID del terminal master |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": {
    "allSlavesFinished": true,
    "totalTransactions": 150,
    "slaves": [
      {
        "terminalId": "UUID-slave1",
        "terminalName": "POS-002",
        "isProcessed": true,
        "isProcessedBo": true,
        "cashUpId": "UUID-slave-cashup"
      }
    ],
    "sharedPaymentMethods": [
      {
        "paymentMethodId": "UUID-pm",
        "name": "Efectivo",
        "isocode": "ZAR",
        "totalSales": 350000.00,
        "totalReturns": -15000.00
      }
    ]
  }
}
```

#### Lógica Interna

1. **Verificación Master**: native SQL sobre `OBPOS_APPLICATIONS WHERE Ismaster = 'Y'`
2. **Cashup del Master**: native SQL sobre `OBPOS_App_Cashup WHERE Isprocessed = 'N' ORDER BY Created DESC LIMIT 1`
3. **Terminales Slave**: native SQL sobre `OBPOS_APPLICATIONS WHERE Masterterminal_ID = ?`
4. **Asociación Master-Slave**: `UpdateCashup.associateMasterSlave()` (requiere entities via `OBDal.get()`)
5. **Transacciones Pendientes**: native SQL `COUNT(*) FROM FIN_Finacc_Transaction WHERE Processed = 'N'`
6. **Agregación de Payment Methods**: native SQL con `SUM`/`GROUP BY` sobre `OBPOS_paymentmethodcashup` por slave

---

### 3.5. CashCloseSlave — Registro de Slave

**Equivale a:** `org.openbravo.retail.posterminal.ProcessCashCloseSlave`

**Método:** `GET`

**URL:** `/ws/CashCloseSlave?pos={terminalId}&cashup={cashupId}`

#### Parámetros

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `pos` | String (UUID) | Sí | ID del terminal slave |
| `cashup` | String (UUID) | Sí | ID del cashup del terminal slave |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": {
    "hasMaster": true
  }
}
```

#### Lógica Interna

1. Verifica existencia del cashup via native SQL sobre `OBPOS_App_Cashup`
2. Carga entidades via `OBDal.get()` (requerido por `UpdateCashup.associateMasterSlave()`)
3. Invoca `UpdateCashup.associateMasterSlave()` para registrar la asociación
4. Verifica si se asignó parent cashup via native SQL `SELECT Obpos_Parent_Cashup_ID`
5. Retorna `hasMaster: true` si el cashup tiene parent asignado

---

### 3.6. CashMgmtMaster — Cash Management Agregado del Master

**Equivale a:** `org.openbravo.retail.posterminal.ProcessCashMgmtMaster`

**Método:** `GET`

**URL:** `/ws/CashMgmtMaster?pos={masterTerminalId}`

#### Parámetros

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `pos` | String (UUID) | Sí | ID del terminal master |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": {
    "sharedPaymentMethods": [
      {
        "paymentMethodId": "UUID-pm",
        "name": "Efectivo",
        "isocode": "ZAR",
        "totalSales": 250000.00,
        "totalReturns": -8000.00,
        "startingCash": 50000.00
      }
    ]
  }
}
```

#### Lógica Interna

1. Obtiene el cashup no procesado del master via native SQL sobre `OBPOS_App_Cashup WHERE Isprocessed = 'N'`
2. Ejecuta native SQL con `SUM`/`GROUP BY` sobre `OBPOS_paymentmethodcashup` JOIN `OBPOS_App_Cashup` (filtrando por `Obpos_Parent_Cashup_ID = ?`) JOIN `OBPOS_App_Payment_Type` (filtrando por `Isshared = 'Y'`)
3. También obtiene los payment methods propios del master con el mismo filtro de `Isshared`
4. Construye la respuesta JSON con totales de slaves y datos propios del master

---

### 3.7. GetCashMgmtEvents — Eventos de Cash Management

**Equivale a:** `org.openbravo.retail.posterminal.term.CashMgmtDepositEvents` y `CashMgmtDropEvents`

**Método:** `GET`

**URL:** `/ws/GetCashMgmtEvents?pos={terminalId}&type={deposit|drop}`

#### Parámetros

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `pos` | String (UUID) | Sí | ID del terminal POS |
| `type` | String | No | Tipo de evento: `deposit`, `drop` (sin filtro = todos) |

#### Respuesta Exitosa

```json
{
  "status": 0,
  "data": [
    {
      "id": "UUID-event",
      "name": "Depósito a Banco",
      "paymentmethod": "UUID-pm",
      "type": "deposit",
      "isocode": "ZAR"
    },
    {
      "id": "UUID-event2",
      "name": "Retiro para Cambio",
      "paymentmethod": "UUID-pm2",
      "type": "drop",
      "isocode": "ZAR"
    }
  ]
}
```

#### Lógica Interna

1. Native SQL con `SELECT DISTINCT` sobre `OBRETCO_CMEvents` JOIN `OBPOS_APP_PAYMENT` (filtrado por `ap.Obpos_Applications_ID = ?` y `ap.Isactive = 'Y'`) LEFT JOIN `C_Currency`
2. Para `deposit`: filtra `cme.Eventtype LIKE '%IN%'`
3. Para `drop`: filtra `cme.Eventtype LIKE '%OUT%'`
4. Sin filtro de type: retorna todos los eventos activos del terminal
5. Filtra por `cme.Isactive = 'Y'`

---

## 4. Mapeo con Mobile Service Requests Originales

Esta tabla muestra la correspondencia completa entre los Mobile Service Requests (como los invoca el POS frontend) y los nuevos WebServices:

| Mobile Service Request | Tipo de Llamada POS | Nuevo WebService | HTTP |
|------------------------|---------------------|-------------------|------|
| `org.openbravo.retail.posterminal.master.Cashup` | `CashupUtils.requestNoProcessedCashupFromBackend()` | `GET /ws/GetCashup?pos=X&isprocessed=N&isprocessedbo=N` | GET |
| `org.openbravo.retail.posterminal.master.Cashup` | `CashupUtils.requestProcessedCashupFromBackend()` | `GET /ws/GetCashup?pos=X&isprocessed=Y` | GET |
| `org.openbravo.retail.posterminal.ProcessCashMgmt` | `processCashManagements` action | `POST /ws/ProcessCashManagement` + JSON body | POST |
| `org.openbravo.retail.posterminal.ProcessCashClose` | `completeCashupAndCreateNew` action | `POST /ws/ProcessCashClose` + JSON body | POST |
| `org.openbravo.retail.posterminal.ProcessCashCloseSlave` | Registro de slave | `GET /ws/CashCloseSlave?pos=X&cashup=Y` | GET |
| `org.openbravo.retail.posterminal.ProcessCashCloseMaster` | Consulta estado master | `GET /ws/CashCloseMaster?pos=X` | GET |
| `org.openbravo.retail.posterminal.ProcessCashMgmtMaster` | Cash mgmt agregado | `GET /ws/CashMgmtMaster?pos=X` | GET |
| `CashMgmtDepositEvents` / `CashMgmtDropEvents` | Terminal properties | `GET /ws/GetCashMgmtEvents?pos=X&type=deposit` | GET |

---

## 5. Hooks y Extensibilidad

Los WebServices preservan la ejecución de los hooks CDI del backend original, permitiendo que módulos de terceros extiendan la funcionalidad:

### 5.1. Hooks Preservados

| Hook Interface | Invocado desde | Descripción |
|----------------|----------------|-------------|
| `CashupHook` | `ProcessCashClose` | Se ejecuta al inicio del cierre de caja. Permite validaciones o lógica adicional |
| `ProcessCashMgmtHook` | `ProcessCashManagement` | Se ejecuta tras cada transacción de cash management |

### 5.2. Hooks No Aplicables

| Hook Interface | Razón |
|----------------|-------|
| `ProcessCashCloseGiftCardHook` | Funcionalidad de Gift Cards excluida del alcance |
| `ExtendsCashManagementPaymentTypeHook` | Retornaba paths HQL para extender consultas de GL Items. En native SQL los GL Items se resuelven directamente de las 5 columnas FK de `OBPOS_App_Payment_Type` |

### 5.3. Inyección CDI

Al ser WebServices (no beans CDI), los hooks se obtienen mediante lookup programático:

```java
javax.enterprise.inject.Instance<CashupHook> hooks =
    CDI.current().select(CashupHook.class);
for (CashupHook hook : hooks) {
    hook.exec(posTerminal, cashup, jsonCashup);
}
```

---

## 6. Entidades y Tablas Involucradas

| Entidad DAL | Tabla BD | Uso |
|-------------|----------|-----|
| `OBPOSApplications` | `OBPOS_Applications` | Terminal POS |
| `OBPOSAppCashup` | `OBPOS_App_Cashup` | Registro de cashup |
| `OBPOSPaymentMethodCashup` | `OBPOS_PaymentMethodCashup` | Métodos de pago por cashup |
| `OBPOSTaxCashup` | `OBPOS_TaxCashup` | Impuestos por cashup |
| `OBPOSPaymentcashupEvents` | `OBPOS_Paymentcashup_Events` | Eventos (transacciones) de cash management |
| `OBPOSCashupApproval` | `OBPOS_CashupApproval` | Aprobaciones de supervisor |
| `FIN_FinaccTransaction` | `FIN_Finacc_Transaction` | Transacciones financieras |
| `FIN_Reconciliation` | `FIN_Reconciliation` | Reconciliaciones bancarias |
| `FIN_FinancialAccount` | `FIN_Financial_Account` | Cuentas financieras |
| `ConversionRateDoc` | `C_Conversion_Rate_Document` | Tasas de cambio por documento |

---

## 7. Estados y Tipos Financieros

### 7.1. Estados de Transacción

| Status | Código | Significado |
|--------|--------|-------------|
| Reconciled & Paid | `RPPC` | Transacción reconciliada completamente |
| Payment Withdrawal Not Cleared | `PWNC` | Retiro pendiente de compensación |
| Receipt Deposit Not Cleared | `RDNC` | Depósito pendiente de compensación |

### 7.2. Tipos de Transacción

| Tipo | Código | Significado |
|------|--------|-------------|
| Bank Payment Withdrawal | `BPW` | Retiro (drop) |
| Bank Payment Deposit | `BPD` | Depósito (deposit) |

### 7.3. Lógica de Asignación

```
drop (retiro)    → transactionType = BPW, status = PWNC
deposit (ingreso) → transactionType = BPD, status = RDNC
```

La **contra-transacción** (en la cuenta destino) usa el tipo opuesto:
```
drop → contra-transacción: BPD, status = RDNC  (la cuenta destino recibe)
deposit → contra-transacción: BPW, status = PWNC (la cuenta destino descuenta)
```

---

## 8. Registro en provider-config.xml

Los 7 WebServices están registrados como beans singleton en el archivo de configuración del módulo:

```xml
<!-- Cash Management / Cash Up WebServices -->
<bean>
    <name>GetCashup</name>
    <class>com.doceleguas.pos.webservices.cashup.GetCashup</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>ProcessCashManagement</name>
    <class>com.doceleguas.pos.webservices.cashup.ProcessCashManagement</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>ProcessCashClose</name>
    <class>com.doceleguas.pos.webservices.cashup.ProcessCashClose</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>CashCloseMaster</name>
    <class>com.doceleguas.pos.webservices.cashup.CashCloseMaster</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>CashCloseSlave</name>
    <class>com.doceleguas.pos.webservices.cashup.CashCloseSlave</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>CashMgmtMaster</name>
    <class>com.doceleguas.pos.webservices.cashup.CashMgmtMaster</class>
    <singleton>true</singleton>
</bean>
<bean>
    <name>GetCashMgmtEvents</name>
    <class>com.doceleguas.pos.webservices.cashup.GetCashMgmtEvents</class>
    <singleton>true</singleton>
</bean>
```

---

## 9. Diagrama de Flujo: Cash Close Completo

```
POS Frontend
    │
    ├─[Slave terminals]──► GET /ws/CashCloseSlave?cashUpId=X
    │                       └─ Registra asociación master-slave
    │
    ├─[Process cash mgmt]─► POST /ws/ProcessCashManagement
    │                        └─ Crea FIN_FinaccTransaction + contra-transacción
    │
    ├─[Close cashup]───────► POST /ws/ProcessCashClose
    │                        ├─ Standalone: reconciliación directa
    │                        ├─ Slave: actualiza estado, espera master
    │                        └─ Master: 
    │                            ├─ GET /ws/CashCloseMaster (polling estado slaves)
    │                            ├─ Acumula payment methods compartidos
    │                            └─ OrderGroupingProcessor + CashCloseProcessor
    │
    └─[Query cashup]───────► GET /ws/GetCashup?pos=X&isprocessed=N
                              └─ Retorna cashup actual con payments, taxes, cash mgmt
```

---

## 10. Notas de Implementación

### 10.1. Patrón Native SQL

Todos los queries de lectura utilizan `PreparedStatement` sobre `OBDal.getInstance().getConnection()` con nombres reales de columnas de la BD (no nombres de propiedades HQL). Este patrón es consistente con los otros WebServices del módulo (`GetOrder`, `GetOrdersFilter`, `SaveBusinessPartner`).

**Se usa DAL (OBProvider + save) para creación de entidades** solo cuando el objeto resultante es requerido por:
- Hooks CDI (`ProcessCashMgmtHook`, `CashupHook`) que reciben entidades DAL como parámetros
- Utilidades de Openbravo (`TransactionsDao`, `UpdateCashup`, `CashCloseProcessor`) que operan con entidades

**`OBDal.get(Entity.class, id)`** se usa puntualmente para cargar entidades por PK cuando son requeridas por los puntos anteriores. Esta operación es un `session.get()` directo de Hibernate, no HQL.

### 10.2. Concurrencia

- `UpdateCashup.getAndUpdateCashUp()` utiliza `SELECT FOR UPDATE` en la BD para evitar condiciones de carrera al modificar el mismo cashup desde múltiples terminales
- Las transacciones se ejecutan dentro del contexto de transacción de Openbravo DAL
- Los WebServices con `PreparedStatement` reutilizan la `Connection` subyacente de la sesión Hibernate via `OBDal.getInstance().getConnection()`

### 10.3. Timezone

- Las fechas del POS incluyen `timezoneOffset` en minutos
- Se calcula: `serverDate = posDate + posOffset + serverOffset`
- Se usa `OBMOBCUtils.calculateServerDate()` cuando está disponible

### 10.4. Autenticación

- Los WebServices heredan la autenticación estándar de Openbravo (`WebServiceServlet`)
- Se requiere sesión HTTP válida o autenticación Basic
- El `OBContext` se establece basándose en los parámetros `client`/`organization` o en la sesión actual

### 10.5. Manejo de Errores

Todos los endpoints retornan un JSON de error consistente:

```json
{
  "status": -1,
  "error": true,
  "message": "Descripción del error"
}
```

Con los códigos HTTP apropiados:
- `400` — Parámetros faltantes o inválidos
- `405` — Método HTTP no soportado
- `500` — Error interno del servidor
