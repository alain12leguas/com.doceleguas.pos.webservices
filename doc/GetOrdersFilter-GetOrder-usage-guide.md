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

### Columnas Equivalentes a PaidReceiptsFilterProperties

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

### Ejemplo de selectList

```
selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.dateordered as "orderDate", bp.name as "businessPartnerName", ord.grandtotal as "totalamount", ord.docstatus as "documentStatus", org.name as "organizationName"
```

---

## Ejemplo Completo de Request

### Request
```http
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter
  ?client=39363B0921BB4293B48383844325E84C
  &organization=D270A5AC50874F8BA67A88EE977F8E3B
  &selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.dateordered as "orderDate", bp.c_bpartner_id as "businessPartner", bp.name as "businessPartnerName", ord.grandtotal as "totalamount", ord.docstatus as "documentStatus", ord.iscancelled as "iscancelled", org.ad_org_id as "organization", org.name as "organizationName", ord.delivered as "isdelivered"
  &f.documentno=VBS2
  &f.c_bpartner_id=EDC5DBD82C3B4E3896B3955E041B242C
  &f.datefrom=2026-02-01
  &f.ordertype=ORD
  &limit=50
  &offset=0
  &orderBy=ord.created DESC
```

### Response
```json
{
  "success": true,
  "data": [
    {
      "id": "F5B00821E0F32EAFF8BD0792B1B68BE0",
      "documentNo": "VBS2/0000122",
      "orderDate": "2026-02-02",
      "businessPartner": "EDC5DBD82C3B4E3896B3955E041B242C",
      "businessPartnerName": "Arturo 333 Montoro",
      "totalamount": 384.4,
      "documentStatus": "CO",
      "iscancelled": false,
      "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
      "organizationName": "Vall Blanca Store",
      "isdelivered": true
    }
  ],
  "totalRows": 1
}
```

### Ejemplo curl
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter?client=39363B0921BB4293B48383844325E84C&organization=D270A5AC50874F8BA67A88EE977F8E3B&selectList=ord.c_order_id+as+%22id%22%2C+ord.documentno+as+%22documentNo%22%2C+ord.grandtotal+as+%22totalamount%22&f.documentno=VBS2&f.ordertype=ORD&limit=50"
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

## Diferencias Clave vs PaidReceiptsFilter/PaidReceipts

| Aspecto | PaidReceiptsFilter/PaidReceipts | GetOrdersFilter/GetOrder |
|---------|--------------------------------|--------------------------|
| **Líneas de orden** | Incluidas en `receiptLines` | No incluidas (solo cabecera) |
| **Pagos** | Incluidos en `receiptPayments` | No incluidos |
| **Impuestos** | Incluidos en `receiptTaxes` | No incluidos |
| **Formato de fechas** | ISO con timezone | Fecha simple |
| **Extensiones CDI** | Soportadas | No aplica |

> **Nota**: Si necesitas líneas de orden, pagos o impuestos, deberás hacer llamadas adicionales o extender los Models para incluirlos.

---

*Documentación actualizada: 2025-02-04*
*Versión: 1.0*
