# Guía de Uso: WebServices GetOrdersFilter y GetOrder

## Introducción

Este documento describe cómo utilizar los WebServices `GetOrdersFilter` y `GetOrder` del módulo `com.doceleguas.pos.webservices`. Estos servicios son equivalentes funcionales de `PaidReceiptsFilter` y `PaidReceipts` respectivamente, pero utilizan **Native SQL queries** en lugar de HQL, ofreciendo mayor flexibilidad en la selección de columnas y filtros.

### Comparación con PaidReceiptsFilter/PaidReceipts

| Característica | PaidReceiptsFilter / PaidReceipts | GetOrdersFilter / GetOrder |
|----------------|-----------------------------------|---------------------------|
| Tipo de Query | HQL (ProcessHQLQueryValidated) | Native SQL |
| Columnas | Fijas (via CDI Extensions) | Dinámicas (via `selectList`) |
| Filtros | JSON `remoteFilters` con operadores | Simples: `f.{columna}={valor}` |
| Extensibilidad | CDI ModelExtension | Modificar `selectList` |

---

# GetOrdersFilter WebService

## Descripción
Consulta **múltiples órdenes** aplicando filtros dinámicos, con soporte para paginación y ordenamiento. Equivalente a `PaidReceiptsFilter`.

## Endpoint
```
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter
```

## Parámetros Requeridos

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `client` | UUID | ID del cliente Openbravo (AD_Client_ID) |
| `organization` | UUID | ID de la organización (AD_Org_ID) |
| `selectList` | String | Columnas SQL a devolver (URL-encoded) |

## Parámetros Opcionales

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `limit` | Integer | 1000 | Máximo de filas a devolver |
| `offset` | Integer | 0 | Filas a saltar (paginación) |
| `orderBy` | String | `ord.created DESC` | Cláusula ORDER BY |

---

## Paginación y Lazy Loading

El WebService soporta paginación completa para implementar lazy loading en el cliente. La respuesta incluye información de paginación que permite al consumidor (OCRE-POS) saber cuántas páginas hay en total y si debe seguir cargando más datos.

### Campos de Respuesta para Paginación

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `totalRows` | Integer | **Total de registros** que coinciden con los filtros (sin limit) |
| `returnedRows` | Integer | Número de filas devueltas en esta respuesta |
| `limit` | Integer | El limit aplicado en esta request |
| `offset` | Integer | El offset aplicado en esta request |
| `hasMore` | Boolean | `true` si hay más páginas disponibles |

### Ejemplo de Flujo de Paginación

**Request 1** - Primera página:
```
GET /ws/GetOrdersFilter?...&limit=100&offset=0
```
```json
{
  "success": true,
  "data": [...100 items...],
  "totalRows": 350,
  "returnedRows": 100,
  "limit": 100,
  "offset": 0,
  "hasMore": true
}
```

**Request 2** - Segunda página:
```
GET /ws/GetOrdersFilter?...&limit=100&offset=100
```
```json
{
  "success": true,
  "data": [...100 items...],
  "totalRows": 350,
  "returnedRows": 100,
  "limit": 100,
  "offset": 100,
  "hasMore": true
}
```

**Request 4** - Última página:
```
GET /ws/GetOrdersFilter?...&limit=100&offset=300
```
```json
{
  "success": true,
  "data": [...50 items...],
  "totalRows": 350,
  "returnedRows": 50,
  "limit": 100,
  "offset": 300,
  "hasMore": false
}
```

### Implementación en el Cliente (Pseudocódigo)

```javascript
async function loadAllOrders(filters) {
  const limit = 100;
  let offset = 0;
  let allOrders = [];
  let hasMore = true;
  
  while (hasMore) {
    const response = await fetch(`/ws/GetOrdersFilter?...&limit=${limit}&offset=${offset}`);
    const json = await response.json();
    
    allOrders = allOrders.concat(json.data);
    hasMore = json.hasMore;
    offset += json.returnedRows;
    
    // Opcional: actualizar progreso en UI
    updateProgress(allOrders.length, json.totalRows);
  }
  
  return allOrders;
}
```

---

## Filtros Soportados

Los filtros se envían como parámetros URL con el prefijo `f.`:
```
f.{columna}={valor}
```

### Tabla de Filtros Disponibles

| Filtro | Columna SQL | Operador | Descripción | Equivalente en PaidReceiptsFilter |
|--------|-------------|----------|-------------|-----------------------------------|
| `f.documentno` | `ord.documentno` | ILIKE (contains) | Número de documento | `documentNo` con `operator: "contains"` |
| `f.c_bpartner_id` | `ord.c_bpartner_id` | = (equals) | ID del Business Partner | `businessPartner` con `operator: "equals", isId: true` |
| `f.ad_org_id` | `ord.ad_org_id` | = (equals) | ID de la organización | `organization` |
| `f.dateordered` | `ord.dateordered` | = (equals) | Fecha exacta de orden (YYYY-MM-DD) | `orderDate` con `operator: "equals"` |
| `f.datefrom` | `ord.dateordered` | >= (mayor o igual) | Inicio del rango de fechas | `orderDate` con `operator: "filter", params[0]` |
| `f.dateto` | `ord.dateordered` | <= (menor o igual) | Fin del rango de fechas | `orderDate` con `operator: "filter", params[1]` |
| `f.grandtotal` | `ord.grandtotal` | = (equals) | Monto total exacto | `totalAmount` |
| `f.docstatus` | `ord.docstatus` | = (equals) | Estado del documento (CO, CL, etc.) | `documentStatus` |
| `f.iscancelled` | `ord.iscancelled` | = (equals) | Si está cancelada (Y/N) | `iscancelled` |
| `f.delivered` | `ord.delivered` | = (equals) | Si está entregada (Y/N) | `isdelivered` |
| `f.ordertype` | (especial) | (ver tabla abajo) | Tipo de orden | `orderType` |
| `f.em_obpos_applications_id` | `ord.em_obpos_applications_id` | = (equals) | Terminal POS | `currentTerminal` |

### Filtros de Propiedades Calculadas

Las propiedades calculadas (computed properties) también pueden usarse como filtros. Se usan **sin el prefijo `@`**:

| Filtro | Propiedad Calculada | Tipo | Valores de Ejemplo |
|--------|---------------------|------|--------------------|
| `f.status` | `@status` | String | `Paid`, `Partially Paid`, `UnPaid`, `Cancelled`, `Refunded`, `Under Evaluation` |
| `f.paidamount` | `@paidAmount` | Decimal | Valor numérico |
| `f.invoicecreated` | `@invoiceCreated` | Boolean | `true`, `false` |
| `f.hasverifiedreturn` | `@hasVerifiedReturn` | Boolean | `true`, `false` |
| `f.hasnegativelines` | `@hasNegativeLines` | Boolean | `true`, `false` |
| `f.isquotation` | `@isQuotation` | Boolean | `true`, `false` |
| `f.deliverymode` | `@deliveryMode` | String | `PickAndCarry`, etc. |
| `f.deliverydate` | `@deliveryDate` | Timestamp | Valor de fecha/hora |

**Ejemplo de uso:**
```
f.status=Partially Paid
f.invoicecreated=true
f.deliverymode=PickAndCarry
```

> **Nota**: Los nombres de filtros son case-insensitive. `f.status`, `f.Status` y `f.STATUS` funcionan igual.

### Filtro Especial: `f.ordertype`

El filtro `ordertype` tiene manejo especial para clasificar tipos de orden:

| Valor | Descripción | SQL Generado |
|-------|-------------|--------------|
| `ORD` | Órdenes regulares | `doctype.isreturn='N' AND docsubtypeso<>'OB' AND islayaway='N'` |
| `RET` | Devoluciones | `doctype.isreturn='Y'` |
| `LAY` | Layaways | `ord.em_obpos_islayaway='Y'` |
| `verifiedReturns` | Retornos verificados | ORD + `cancelledorder_id IS NULL` |
| `payOpenTickets` | Tickets abiertos | `grandtotal>0 AND docstatus<>'CL'` |

### Comparación de Sintaxis de Filtros

**PaidReceiptsFilter (remoteFilters JSON):**
```json
{
  "remoteFilters": [
    {
      "value": "EDC5DBD82C3B4E3896B3955E041B242C",
      "columns": ["businessPartner"],
      "operator": "equals",
      "isId": true
    },
    {
      "value": "VBS2/0000",
      "columns": ["documentNo"],
      "operator": "contains",
      "isId": false
    },
    {
      "value": "OrderDate",
      "columns": ["orderDate"],
      "operator": "filter",
      "params": ["2025-02-03", null],
      "isId": false
    }
  ]
}
```

**GetOrdersFilter (parámetros URL):**
```
f.c_bpartner_id=EDC5DBD82C3B4E3896B3955E041B242C
f.documentno=VBS2/0000
f.datefrom=2025-02-03
```

---

## Columnas del Response (selectList)

El parámetro `selectList` define las columnas a devolver. Debe usar nombres de columnas SQL con alias para el frontend.

### Columnas Directas (tablas base)

| Columna SQL | Alias Recomendado | Descripción | Origen en PaidReceiptsFilterProperties |
|-------------|-------------------|-------------|----------------------------------------|
| `ord.c_order_id` | `id` | UUID de la orden | `ord.id` |
| `doctype.c_doctype_id` | `documentTypeId` | ID del tipo de documento | `docType.id` |
| `ord.docstatus` | `documentStatus` | Estado del documento | `ord.documentStatus` |
| `ord.documentno` | `documentNo` | Número de documento | `ord.documentNo` |
| `ord.created` | `creationDate` | Fecha de creación | `ord.creationDate` |
| `ord.dateordered` | `orderDate` | Fecha de orden | `ord.orderDate` |
| `bp.c_bpartner_id` | `businessPartner` | ID del cliente | `bp.id` |
| `bp.name` | `businessPartnerName` | Nombre del cliente | `bp.name` |
| `ord.grandtotal` | `totalamount` | Monto total | `ord.grandTotalAmount` |
| `ord.iscancelled` | `iscancelled` | Está cancelada | `ord.iscancelled` |
| `org.ad_org_id` | `organization` | ID de organización | `org.id` |
| `org.value` | `organizationSearchKey` | Clave de búsqueda org | `org.searchKey` |
| `org.name` | `organizationName` | Nombre de organización | `org.name` |
| `trxorg.ad_org_id` | `trxOrganization` | ID org transacción | `trxOrg.id` |
| `trxorg.name` | `trxOrganizationName` | Nombre org transacción | `trxOrg.name` |
| `ord.delivered` | `isdelivered` | Está entregada | `ord.delivered` |
| `ord.em_obpos_islayaway` | `isLayaway` | Es layaway | `ord.obposIslayaway` |
| `ord.invoiceterms` | `invoiceTerms` | Términos de factura | `ord.invoiceTerms` |
| `obpos.obpos_applications_id` | `currentTerminal` | Terminal actual | - |

---

### ⚠️ Propiedades Calculadas (Computed Properties)

Algunas propiedades de `PaidReceiptsFilterProperties` **NO son columnas directas** sino valores calculados mediante subqueries o expresiones CASE. Para solicitarlas, usa los alias especiales con prefijo `@`:

| Alias Especial | Alias de Salida | Tipo | Descripción | Equivalente en PaidReceiptsFilterProperties |
|----------------|-----------------|------|-------------|----------------------------------------------|
| `@orderType` | `orderType` | String | Tipo de orden: 'ORD', 'RET', 'LAY', 'QT' | `getOrderType()` CASE expression |
| `@deliveryMode` | `deliveryMode` | String | Modo de entrega de las líneas (default: 'PickAndCarry') | Subquery OrderLine.obrdmDeliveryMode |
| `@deliveryDate` | `deliveryDate` | Timestamp | Fecha/hora mínima de entrega | Subquery OrderLine.obrdmDeliveryDate/Time |
| `@paidAmount` | `paidAmount` | Decimal | Suma de montos pagados | Subquery FIN_Payment_Schedule.paidAmount |
| `@status` | `status` | String | Estado de la orden (ver tabla abajo) | CASE expression con múltiples condiciones |
| `@invoiceCreated` | `invoiceCreated` | Boolean | Si existe factura simplificada | EXISTS sobre InvoiceLine |
| `@hasVerifiedReturn` | `hasVerifiedReturn` | Boolean | Si tiene devoluciones verificadas | Subquery OrderLine → ShipmentLine |
| `@hasNegativeLines` | `hasNegativeLines` | Boolean | Si tiene líneas con cantidad negativa | EXISTS OrderLine.qtyordered < 0 |
| `@isQuotation` | `isQuotation` | Boolean | Si es cotización (tiene quotation_id) | CASE ord.quotation_id IS NOT NULL |

#### Valores de `@status`

| Valor | Condición |
|-------|-----------|
| `Refunded` | Tipo de documento es devolución (isreturn = 'Y') |
| `UnderEvaluation` | Tipo de documento es cotización (sOSubType = 'OB') |
| `Cancelled` | Orden cancelada (iscancelled = 'Y') |
| `UnPaid` | grandTotal > 0 y paidAmount = 0 |
| `PartiallyPaid` | grandTotal > 0 y paidAmount < grandTotal |
| `Paid` | Otros casos (totalmente pagado) |

#### Cómo Funcionan

Estas propiedades son **alias especiales** que el backend reemplaza automáticamente por sus expresiones SQL correspondientes:

**`@orderType`** se convierte en:
```sql
(CASE 
  WHEN doctype.isreturn = 'Y' THEN 'RET' 
  WHEN doctype.docsubtypeso = 'OB' THEN 'QT' 
  WHEN COALESCE(ord.em_obpos_islayaway, 'N') = 'Y' THEN 'LAY' 
  ELSE 'ORD' 
END)
```

**`@paidAmount`** se convierte en:
```sql
(SELECT COALESCE(SUM(fps.paidamt), 0) 
 FROM fin_payment_schedule fps 
 WHERE fps.c_order_id = ord.c_order_id)
```

**`@status`** se convierte en:
```sql
(CASE 
  WHEN doctype.isreturn = 'Y' THEN 'Refunded' 
  WHEN doctype.docsubtypeso = 'OB' THEN 'UnderEvaluation' 
  WHEN ord.iscancelled = 'Y' THEN 'Cancelled' 
  WHEN ord.grandtotal > 0 AND (SELECT COALESCE(SUM(fps.paidamt), 0) FROM fin_payment_schedule fps WHERE fps.c_order_id = ord.c_order_id) = 0 THEN 'UnPaid' 
  WHEN ord.grandtotal > 0 AND (SELECT COALESCE(SUM(fps.paidamt), 0) FROM fin_payment_schedule fps WHERE fps.c_order_id = ord.c_order_id) < ord.grandtotal THEN 'PartiallyPaid' 
  ELSE 'Paid' 
END)
```

**`@invoiceCreated`** se convierte en:
```sql
(EXISTS(SELECT 1 FROM c_invoiceline il 
  JOIN c_invoice i ON il.c_invoice_id = i.c_invoice_id 
  JOIN c_orderline ol ON il.c_orderline_id = ol.c_orderline_id 
  WHERE ol.c_order_id = ord.c_order_id 
  AND i.em_obpos_sequencename = 'simplifiedinvoiceslastassignednum'))
```

**`@hasNegativeLines`** se convierte en:
```sql
(CASE WHEN EXISTS (SELECT 1 FROM c_orderline ordLine 
  WHERE ordLine.c_order_id = ord.c_order_id 
  AND ordLine.qtyordered < 0) THEN true ELSE false END)
```

**`@isQuotation`** se convierte en:
```sql
(CASE WHEN ord.quotation_id IS NOT NULL THEN true ELSE false END)
```

#### Ejemplo de Uso de Propiedades Calculadas

```
selectList=ord.c_order_id as "id", ord.documentno as "documentNo", @orderType as "orderType", @status as "status", @paidAmount as "paidAmount", @invoiceCreated as "invoiceCreated"
```

#### Response con Propiedades Calculadas

```json
{
  "success": true,
  "data": [
    {
      "id": "F5B00821E0F32EAFF8BD0792B1B68BE0",
      "documentNo": "VBS2/0000122",
      "orderType": "ORD",
      "status": "Paid",
      "paidAmount": 384.40,
      "invoiceCreated": true
    }
  ],
  "totalRows": 1
}
```

> **Nota Importante**: Todos los alias con prefijo `@` son case-insensitive. Se pueden usar como `@Status`, `@STATUS`, `@paidamount`, etc.

---

### Ejemplo de selectList (columnas directas)

```
selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.dateordered as "orderDate", bp.name as "businessPartnerName", ord.grandtotal as "totalamount", ord.docstatus as "documentStatus", org.name as "organizationName"
```

### Ejemplo de selectList (con propiedades calculadas)

```
selectList=ord.c_order_id as "id", ord.documentno as "documentNo", @orderType as "orderType", @deliveryMode as "deliveryMode", @deliveryDate as "deliveryDate", ord.grandtotal as "totalamount"
```

---

## Ejemplo Completo de Request

### Request (con propiedades calculadas)
```http
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter
  ?client=39363B0921BB4293B48383844325E84C
  &organization=D270A5AC50874F8BA67A88EE977F8E3B
  &selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.dateordered as "orderDate", bp.name as "businessPartnerName", ord.grandtotal as "totalamount", @orderType as "orderType", @deliveryMode as "deliveryMode", @deliveryDate as "deliveryDate"
  &f.documentno=VBS2
  &f.ordertype=ORD
  &limit=50
  &offset=0
  &orderBy=ord.created DESC
```

### Response (con propiedades calculadas y paginación)
```json
{
  "success": true,
  "data": [
    {
      "id": "F5B00821E0F32EAFF8BD0792B1B68BE0",
      "documentNo": "VBS2/0000122",
      "orderDate": "2026-02-02",
      "businessPartnerName": "Arturo 333 Montoro",
      "totalamount": 384.4,
      "orderType": "ORD",
      "deliveryMode": "HomeDelivery",
      "deliveryDate": "2026-02-05T14:30:00"
    }
  ],
  "totalRows": 1
}
```

### Ejemplo curl (con propiedades calculadas)
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter?client=39363B0921BB4293B48383844325E84C&organization=D270A5AC50874F8BA67A88EE977F8E3B&selectList=ord.c_order_id+as+%22id%22%2C+ord.documentno+as+%22documentNo%22%2C+%40orderType+as+%22orderType%22%2C+%40deliveryMode+as+%22deliveryMode%22&f.documentno=VBS2&f.ordertype=ORD&limit=50"
```

---

# GetOrder WebService

## Descripción
Obtiene **una sola orden** por su ID o número de documento, incluyendo información detallada. Equivalente a `PaidReceipts`.

## Endpoint
```
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrder
```

## Parámetros Requeridos

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `client` | UUID | ID del cliente Openbravo |
| `organization` | UUID | ID de la organización |
| `selectList` | String | Columnas SQL a devolver (URL-encoded) |
| `orderId` **o** `documentNo` | String | Identificador de la orden (uno de los dos es requerido) |

## Propiedades de Arrays (Incluidas Automáticamente)

El response de GetOrder **siempre incluye** las siguientes propiedades de arrays, equivalentes a PaidReceipts:

| Propiedad | Descripción |
|-----------|-------------|
| `receiptLines` | Líneas de la orden con `taxes` y `promotions` anidados |
| `receiptPayments` | Pagos asociados a la orden |
| `receiptTaxes` | Resumen de impuestos de la orden |

> **Nota**: Estas propiedades se incluyen automáticamente en cada respuesta. Las líneas siempre incluyen sus arrays `taxes` y `promotions` (pueden estar vacíos si no aplican).

---

## Columnas del Response (selectList)

### Columnas Equivalentes a PaidReceiptProperties

| Columna SQL | Alias Recomendado | Descripción | Origen en PaidReceiptProperties |
|-------------|-------------------|-------------|--------------------------------|
| `ord.c_order_id` | `orderid` | UUID de la orden | `ord.id` |
| `ord.documentno` | `documentNo` | Número de documento | `ord.documentNo` |
| `ord.em_obpos_sequencename` | `obposSequencename` | Nombre de secuencia | `ord.obposSequencename` |
| `ord.em_obpos_sequencenumber` | `obposSequencenumber` | Número de secuencia | `ord.obposSequencenumber` |
| `ord.dateordered` | `orderDate` | Fecha de orden | `ord.orderDate` |
| `ord.created` | `creationDate` | Fecha de creación | `ord.creationDate` |
| `ord.createdby` | `createdBy` | ID usuario creador | `ord.createdBy.id` |
| `ord.updatedby` | `updatedBy` | ID usuario actualizador | `ord.updatedBy.id` |
| `bp.c_bpartner_id` | `bp` | ID del cliente | `ord.businessPartner.id` |
| `ord.c_bpartner_location_id` | `bpLocId` | ID ubicación cliente | `ord.partnerAddress.id` |
| `ord.billto_id` | `bpBillLocId` | ID ubicación facturación | `ord.invoiceAddress.id` |
| `ord.grandtotal` | `totalamount` | Monto total | `ord.grandTotalAmount` |
| `salesrep.name` | `salesRepresentative$_identifier` | Nombre vendedor | `salesRepresentative.name` |
| `salesrep.ad_user_id` | `salesRepresentative` | ID vendedor | `salesRepresentative.id` |
| `ord.c_doctypetarget_id` | `documentType` | ID tipo documento | `ord.documentType.id` |
| `ord.m_warehouse_id` | `warehouse` | ID almacén | `ord.warehouse.id` |
| `ord.description` | `description` | Descripción | `ord.description` |
| `obpos.obpos_applications_id` | `posTerminal` | ID terminal POS | `pos.id` |
| `obpos.name` | `posTerminal$_identifier` | Nombre terminal | `pos.name` |
| `bp.name` | `businessPartner$_identifier` | Nombre cliente | `ord.businessPartner.name` |
| `ord.c_currency_id` | `currency` | ID moneda | `ord.currency.id` |
| `ord.m_pricelist_id` | `priceList` | ID lista de precios | `ord.priceList.id` |
| `org.ad_org_id` | `organization` | ID organización | `ord.organization.id` |
| `org.name` | `organization$_identifier` | Nombre organización | `ord.organization.name` |
| `ord.ad_client_id` | `client` | ID cliente | `ord.client.id` |
| `ord.em_obpos_appcashup` | `obposAppCashup` | ID cashup | `ord.obposAppCashup` |
| `ord.totallines` | `totalNetAmount` | Monto neto total | `ord.summedLineAmount` |
| `ord.em_obpos_islayaway` | `isLayaway` | Es layaway | `ord.obposIslayaway` |
| `ord.iscancelled` | `iscancelled` | Está cancelada | `ord.iscancelled` |
| `ord.updated` | `loaded` | Última actualización | `ord.updated` |
| `ord.iscashvat` | `cashVAT` | Es cash VAT | `ord.cashVAT` |
| `ord.invoiceterms` | `invoiceTerms` | Términos factura | `ord.invoiceTerms` |

### Ejemplo de selectList para GetOrder

```
selectList=ord.c_order_id as "orderid", ord.documentno as "documentNo", ord.dateordered as "orderDate", ord.created as "creationDate", bp.c_bpartner_id as "bp", bp.name as "businessPartner$_identifier", ord.grandtotal as "totalamount", salesrep.name as "salesRepresentative$_identifier", ord.c_doctypetarget_id as "documentType", ord.description as "description", obpos.obpos_applications_id as "posTerminal", obpos.name as "posTerminal$_identifier", org.ad_org_id as "organization", org.name as "organization$_identifier", ord.iscancelled as "iscancelled", ord.em_obpos_islayaway as "isLayaway"
```

### Propiedades Calculadas en GetOrder

GetOrder también soporta las mismas propiedades calculadas que GetOrdersFilter:

| Alias Especial | Descripción |
|----------------|-------------|
| `@orderType` | Tipo de orden: 'ORD', 'RET', 'LAY', 'QT' |
| `@deliveryMode` | Modo de entrega de las líneas |
| `@deliveryDate` | Fecha/hora de entrega de las líneas |

Ejemplo:
```
selectList=ord.documentno as "documentNo", @orderType as "orderType", @deliveryMode as "deliveryMode", @deliveryDate as "deliveryDate"
```

---

## Ejemplo Completo de Request (por orderId)

### Request
```http
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrder
  ?client=39363B0921BB4293B48383844325E84C
  &organization=D270A5AC50874F8BA67A88EE977F8E3B
  &orderId=F5B00821E0F32EAFF8BD0792B1B68BE0
  &selectList=ord.c_order_id as "orderid", ord.documentno as "documentNo", ord.dateordered as "orderDate", ord.created as "creationDate", bp.c_bpartner_id as "bp", bp.name as "businessPartner$_identifier", ord.grandtotal as "totalamount", salesrep.name as "salesRepresentative$_identifier", ord.c_doctypetarget_id as "documentType", ord.description as "description", obpos.obpos_applications_id as "posTerminal", obpos.name as "posTerminal$_identifier", org.ad_org_id as "organization", org.name as "organization$_identifier", ord.iscancelled as "iscancelled", ord.em_obpos_islayaway as "isLayaway"
```

### Response
```json
{
  "success": true,
  "data": {
    "orderid": "F5B00821E0F32EAFF8BD0792B1B68BE0",
    "documentNo": "VBS2/0000122",
    "orderDate": "2026-02-02",
    "creationDate": "2026-02-02T17:12:30.096Z",
    "bp": "EDC5DBD82C3B4E3896B3955E041B242C",
    "businessPartner$_identifier": "Arturo 333 Montoro",
    "totalamount": 384.4,
    "salesRepresentative$_identifier": "Vall Blanca Store User",
    "documentType": "511A9371A0F74195AA3F6D66C722729D",
    "description": "",
    "posTerminal": "1C9CB2318D17467BA0A76DB6CF309213",
    "posTerminal$_identifier": "VBS POS2 Terminal",
    "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
    "organization$_identifier": "Vall Blanca Store",
    "iscancelled": false,
    "isLayaway": false
  }
}
```

### Ejemplo curl
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrder?client=39363B0921BB4293B48383844325E84C&organization=D270A5AC50874F8BA67A88EE977F8E3B&orderId=F5B00821E0F32EAFF8BD0792B1B68BE0&selectList=ord.c_order_id+as+%22orderid%22%2C+ord.documentno+as+%22documentNo%22%2C+ord.grandtotal+as+%22totalamount%22"
```

---

## Ejemplo de Request (por documentNo)

### Request
```http
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrder
  ?client=39363B0921BB4293B48383844325E84C
  &organization=D270A5AC50874F8BA67A88EE977F8E3B
  &documentNo=VBS2/0000122
  &selectList=ord.c_order_id as "orderid", ord.documentno as "documentNo", ord.grandtotal as "totalamount"
```

### Response
```json
{
  "success": true,
  "data": {
    "orderid": "F5B00821E0F32EAFF8BD0792B1B68BE0",
    "documentNo": "VBS2/0000122",
    "totalamount": 384.4
  }
}
```

---

## Respuestas de Error

### Parámetro Faltante (400 Bad Request)
```json
{
  "success": false,
  "error": true,
  "message": "Missing required parameter: 'selectList'",
  "statusCode": 400
}
```

### Orden No Encontrada (404 Not Found) - Solo GetOrder
```json
{
  "success": false,
  "error": true,
  "message": "Order not found"
}
```

### Error de Query (500 Internal Server Error)
```json
{
  "success": false,
  "error": true,
  "message": "Error executing query: <detalle del error>",
  "statusCode": 500
}
```

---

## JOINs Disponibles

Ambos WebServices utilizan los siguientes JOINs que permiten acceder a columnas de tablas relacionadas:

| Alias | Tabla | Descripción | Ejemplo de Columna |
|-------|-------|-------------|-------------------|
| `ord` | c_order | Orden principal | `ord.documentno` |
| `bp` | c_bpartner | Business Partner | `bp.name` |
| `org` | ad_org | Organización de la orden | `org.name` |
| `trxorg` | ad_org | Organización de transacción | `trxorg.name` |
| `obpos` | obpos_applications | Terminal POS | `obpos.name` |
| `salesrep` | ad_user | Vendedor | `salesrep.name` |
| `doctype` | c_doctype | Tipo de documento | `doctype.name` |

---

## Notas de Seguridad

1. **SQL Injection Prevention**: Los keywords peligrosos (UPDATE, DELETE, DROP, etc.) son eliminados del `selectList`
2. **orderBy sanitizado**: Solo caracteres alfanuméricos, puntos, guiones bajos permitidos
3. **Parámetros preparados**: Todos los filtros usan parámetros (`:param`) para prevenir inyección
4. **Client/Organization**: Siempre se aplican en el WHERE para seguridad multi-tenant

---

## Arquitectura Interna

### Estructura de Clases

```
com.doceleguas.pos.webservices/
├── GetOrdersFilter.java          - WebService para múltiples órdenes
├── GetOrder.java                 - WebService para una orden
└── orders/
    ├── OrderQueryHelper.java     - Clase utility (constantes + métodos compartidos)
    ├── OrdersFilterModel.java    - Model para GetOrdersFilter
    └── OrderModel.java           - Model para GetOrder con arrays
```

### OrderQueryHelper (Clase Utility)

Esta clase centraliza código compartido entre `OrdersFilterModel` y `OrderModel`:

| Componente | Tipo | Descripción |
|------------|------|-------------|
| `DELIVERY_MODE_SQL` | Constante | Subquery para modo de entrega |
| `DELIVERY_DATE_SQL` | Constante | Subquery para fecha de entrega (PostgreSQL) |
| `ORDER_TYPE_SQL` | Constante | CASE para tipo de orden |
| `ORDER_BASE_JOINS` | Constante | JOINs comunes (bp, org, doctype, etc.) |
| `sanitizeSelectList()` | Método | Prevención SQL injection en SELECT |
| `sanitizeOrderBy()` | Método | Prevención SQL injection en ORDER BY |
| `replaceComputedProperties()` | Método | Reemplazo de @alias → SQL |
| `rowToJson()` | Método | Conversión Map → JSONObject |

---

## Diferencias Clave vs PaidReceiptsFilter/PaidReceipts

| Aspecto | PaidReceiptsFilter/PaidReceipts | GetOrdersFilter/GetOrder |
|---------|--------------------------------|--------------------------|
| **Líneas de orden** | Incluidas en `receiptLines` | **GetOrder**: Siempre incluidas en `receiptLines` |
| **Pagos** | Incluidos en `receiptPayments` | **GetOrder**: Siempre incluidos en `receiptPayments` |
| **Impuestos** | Incluidos en `receiptTaxes` | **GetOrder**: Siempre incluidos en `receiptTaxes` |
| **Taxes por línea** | Incluidos en cada línea | **GetOrder**: Siempre incluidos (array puede estar vacío) |
| **Promotions por línea** | Incluidos en cada línea | **GetOrder**: Siempre incluidos (array puede estar vacío) |
| **Formato de fechas** | ISO con timezone | Fecha simple |
| **Extensiones CDI** | Soportadas | No aplica |

---

## Propiedades de Arrays Computadas (GetOrder)

GetOrder soporta tres propiedades de arrays computadas equivalentes a PaidReceipts:

### receiptLines (includeLines=true)

Array de líneas de la orden. Cada línea incluye:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `lineId` | UUID | ID de la línea |
| `lineNo` | Number | Número de línea |
| `product` | UUID | ID del producto |
| `product$_identifier` | String | Nombre del producto |
| `productSearchKey` | String | Código del producto |
| `quantity` | Number | Cantidad ordenada |
| `unitPrice` | Number | Precio unitario |
| `listPrice` | Number | Precio de lista |
| `lineNetAmount` | Number | Monto neto de la línea |
| `lineGrossAmount` | Number | Monto bruto de la línea |
| `tax` | UUID | ID del impuesto |
| `tax$_identifier` | String | Nombre del impuesto |
| `taxRate` | Number | Tasa del impuesto |
| `description` | String | Descripción de la línea |
| `deliveryMode` | String | Modo de entrega de la línea |
| `deliveryDate` | Date | Fecha de entrega de la línea |
| `taxes` | Array | **Array de impuestos por línea** |
| `promotions` | Array | **Array de promociones/descuentos** |

#### Estructura de `taxes` (por línea)

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `taxId` | UUID | ID del impuesto |
| `identifier` | String | Nombre del impuesto |
| `taxAmount` | Number | Monto del impuesto |
| `taxableAmount` | Number | Base imponible |
| `taxRate` | Number | Tasa |
| `docTaxAmount` | String | Tipo de impuesto |
| `lineNo` | Number | Número de línea |
| `cascade` | Boolean | Si es impuesto en cascada |
| `isSpecialTax` | Boolean | Si es impuesto especial |

#### Estructura de `promotions` (por línea)

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `ruleId` | UUID | ID de la promoción |
| `name` | String | Nombre de la promoción |
| `printName` | String | Nombre para impresión |
| `discountType` | UUID | Tipo de descuento |
| `amt` | Number | Monto de descuento |
| `actualAmt` | Number | Monto actual |
| `displayedTotalAmount` | Number | Monto mostrado |
| `qtyOffer` | Number | Cantidad de oferta |
| `identifier` | String | Identificador |
| `lineNo` | Number | Número de línea |
| `hidden` | Boolean | Si está oculto |

### receiptPayments (includePayments=true)

Array de pagos asociados a la orden:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `paymentId` | UUID | ID del pago |
| `documentNo` | String | Número de documento del pago |
| `paymentDate` | Date | Fecha del pago |
| `amount` | Number | Monto del pago |
| `paymentAmount` | Number | Monto total pagado |
| `financialTransactionAmount` | Number | Monto transacción financiera |
| `account` | UUID | ID cuenta financiera |
| `accountName` | String | Nombre de la cuenta |
| `paymentMethod` | String | Nombre método de pago |
| `paymentMethodId` | UUID | ID método de pago |
| `isocode` | String | Código ISO de moneda |
| `cashup` | UUID | ID del cashup |
| `posTerminal` | UUID | ID terminal POS |
| `posTerminalSearchKey` | String | Clave del terminal |
| `comment` | String | Comentario/descripción |
| `paymentData` | Object | Datos adicionales del pago (JSON) |
| `reversedPaymentId` | UUID | ID pago revertido (si aplica) |
| `isReversed` | Boolean | Si está revertido |

### receiptTaxes (includeTaxes=true)

Array resumen de impuestos de la orden:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `taxid` | UUID | ID del impuesto |
| `rate` | Number | Tasa |
| `net` | Number | Base imponible |
| `amount` | Number | Monto del impuesto |
| `name` | String | Nombre del impuesto |
| `gross` | Number | Total (neto + impuesto) |
| `cascade` | Boolean | Si es en cascada |
| `docTaxAmount` | String | Tipo |
| `lineNo` | Number | Número de línea |
| `taxBase` | UUID | ID categoría de impuesto |
| `isSpecialTax` | Boolean | Si es especial |

---

## Ejemplo Completo de Request (GetOrder)

### Request
```http
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrder
  ?client=39363B0921BB4293B48383844325E84C
  &organization=D270A5AC50874F8BA67A88EE977F8E3B
  &orderId=F5B00821E0F32EAFF8BD0792B1B68BE0
  &selectList=ord.c_order_id as "orderid", ord.documentno as "documentNo", ord.grandtotal as "totalamount"
```

### Response
```json
{
  "success": true,
  "data": {
    "orderid": "F5B00821E0F32EAFF8BD0792B1B68BE0",
    "documentNo": "VBS2/0000122",
    "totalamount": 384.4,
    "receiptLines": [
      {
        "lineId": "A1B2C3D4...",
        "lineNo": 10,
        "product": "PR123...",
        "product$_identifier": "Producto de ejemplo",
        "quantity": 2,
        "unitPrice": 160.50,
        "lineNetAmount": 321.00,
        "taxes": [
          {
            "taxId": "TAX123...",
            "identifier": "IVA 21%",
            "taxAmount": 67.41,
            "taxableAmount": 321.00,
            "taxRate": 21.0
          }
        ],
        "promotions": [
          {
            "ruleId": "PROMO123...",
            "name": "Descuento 10%",
            "amt": -32.10,
            "hidden": false
          }
        ]
      }
    ],
    "receiptPayments": [
      {
        "paymentId": "PAY123...",
        "documentNo": "PAY/0001",
        "paymentDate": "2026-02-02",
        "amount": 384.40,
        "paymentMethod": "Efectivo",
        "isocode": "EUR"
      }
    ],
    "receiptTaxes": [
      {
        "taxid": "TAX123...",
        "name": "IVA 21%",
        "rate": 21.0,
        "net": 317.68,
        "amount": 66.72,
        "gross": 384.40
      }
    ]
  }
}
```

> **Nota**: `receiptLines`, `receiptPayments` y `receiptTaxes` siempre se incluyen en el response. Cada línea siempre incluye `taxes` y `promotions` (pueden ser arrays vacíos `[]`).

### Ejemplo curl
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrder?client=39363B0921BB4293B48383844325E84C&organization=D270A5AC50874F8BA67A88EE977F8E3B&orderId=F5B00821E0F32EAFF8BD0792B1B68BE0&selectList=ord.c_order_id+as+%22orderid%22%2C+ord.documentno+as+%22documentNo%22%2C+ord.grandtotal+as+%22totalamount%22"
```

---

*Documentación actualizada: 2026-02-09*
*Versión: 4.0 - Soporte completo para paginación (totalRows, returnedRows, hasMore)*
