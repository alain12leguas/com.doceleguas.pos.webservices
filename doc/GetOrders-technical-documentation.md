# GetOrder / GetOrdersFilter WebServices - Documentación Técnica

## Resumen

Este módulo proporciona dos WebServices para consultar órdenes del sistema POS:

| WebService | Equivalente | Propósito |
|------------|-------------|-----------|
| **GetOrdersFilter** | PaidReceiptsFilter | Consultar **múltiples órdenes** con filtros dinámicos |
| **GetOrder** | PaidReceipts | Obtener **una sola orden** por ID o documentNo |

Ambos utilizan **Native SQL queries** con soporte para selección dinámica de columnas, siguiendo el patrón arquitectónico de **MasterDataWebService + Model**.

---

## Arquitectura del Código

La implementación consiste en **4 clases** principales:

```
com.doceleguas.pos.webservices/
├── GetOrdersFilter.java                    (~367 líneas) - WebService para múltiples órdenes
├── GetOrder.java                           (~275 líneas) - WebService para una orden
└── orders/
    ├── OrdersFilterModel.java              (~267 líneas) - Model para GetOrdersFilter
    └── OrderModel.java                     (~138 líneas) - Model para GetOrder
```

### Descripción de cada clase

| Clase | Equivalente | Responsabilidad |
|-------|-------------|-----------------|
| `GetOrdersFilter` | PaidReceiptsFilter | WebService HTTP para consultar múltiples órdenes con filtros |
| `GetOrder` | PaidReceipts | WebService HTTP para obtener una sola orden por ID |
| `OrdersFilterModel` | - | Construye NativeQuery con filtros dinámicos para múltiples órdenes |
| `OrderModel` | - | Construye NativeQuery para una sola orden |

### Comparación con PaidReceipts/PaidReceiptsFilter

| Aspecto | PaidReceiptsFilter / PaidReceipts | GetOrdersFilter / GetOrder |
|---------|-----------------------------------|---------------------------|
| **Query** | HQL (ProcessHQLQueryValidated) | Native SQL |
| **Columnas** | Fijas via CDI Extensions | Dinámicas via selectList |
| **Filtros** | remoteFilters JSON | Simples: f.{col}={val} |
| **Extensibilidad** | CDI/ModelExtension | Modificar selectList |

---

## Diagrama de Arquitectura

```
                          ┌─────────────────────────────────────┐
                          │          HTTP Requests               │
                          └─────────────────────────────────────┘
                                    │                 │
                    ┌───────────────┘                 └───────────────┐
                    ▼                                                 ▼
         ┌────────────────────┐                          ┌────────────────────┐
         │  GetOrdersFilter   │                          │     GetOrder       │
         │  (múltiples orders)│                          │   (single order)   │
         └────────────────────┘                          └────────────────────┘
                    │                                                 │
                    ▼                                                 ▼
         ┌────────────────────┐                          ┌────────────────────┐
         │ OrdersFilterModel  │                          │    OrderModel      │
         │ (filtros, paginac.)│                          │ (orderId/docNo)    │
         └────────────────────┘                          └────────────────────┘
                    │                                                 │
                    └─────────────────┬───────────────────────────────┘
                                      ▼
                          ┌─────────────────────────────────────┐
                          │           C_Order (table)            │
                          │      + JOINs (bp, org, doctype...)   │
                          └─────────────────────────────────────┘
```

---

# GetOrdersFilter WebService

## Endpoint
```
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter
```

## Propósito
Consultar **múltiples órdenes** aplicando filtros dinámicos, con soporte para paginación y ordenamiento.

## Parámetros Requeridos

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `client` | UUID | ID del cliente Openbravo |
| `organization` | UUID | ID de la organización |
| `selectList` | String | Columnas SQL a devolver (URL-encoded) |

## Parámetros Opcionales

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `limit` | Integer | 1000 | Máximo de filas a devolver |
| `offset` | Integer | 0 | Filas a saltar (paginación) |
| `orderBy` | String | ord.created DESC | Cláusula ORDER BY |

## Sintaxis de Filtros

Los filtros usan el prefijo `f.` seguido del nombre de columna SQL:

```
f.{columna}={valor}
```

### Comportamiento de Operadores

| Columna | Operador | SQL Generado |
|---------|----------|--------------|
| `documentno` | ILIKE (contains) | `UPPER(ord.documentno) LIKE UPPER('%valor%')` |
| Todas las demás | = (equals) | `ord.columna = 'valor'` |

### Filtros Especiales

| Filtro | Descripción |
|--------|-------------|
| `f.datefrom` | Inicio de rango de fechas |
| `f.dateto` | Fin de rango de fechas |
| `f.ordertype` | Tipo de orden: ORD, RET, LAY, verifiedReturns, payOpenTickets |

## Ejemplo de Request

```http
GET /ws/com.doceleguas.pos.webservices.GetOrdersFilter
  ?client=757D621ABD1948F5BCBAD91F19BB70AC
  &organization=594C60A9C1154300AEB808C117437D7F
  &selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.grandtotal as "total"
  &f.documentno=VBS2
  &f.ordertype=ORD
  &limit=50
```

## Respuesta

```json
{
  "success": true,
  "data": [
    {"id": "AC2661DED5E1EEA353FD72885A7EA1AC", "documentNo": "VBS2-0000039", "total": 81.11},
    {"id": "BC3772EFD6F2FFB464GE83996B8FB2BD", "documentNo": "VBS2-0000040", "total": 125.50}
  ],
  "totalRows": 2
}
```

---

# GetOrder WebService

## Endpoint
```
GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrder
```

## Propósito
Obtener **una sola orden** por su ID o número de documento. Equivalente a `PaidReceipts`.

## Parámetros Requeridos

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `client` | UUID | ID del cliente Openbravo |
| `organization` | UUID | ID de la organización |
| `selectList` | String | Columnas SQL a devolver (URL-encoded) |
| `orderId` o `documentNo` | String | Identificador de la orden (uno de los dos es requerido) |

## Ejemplo de Request (por orderId)

```http
GET /ws/com.doceleguas.pos.webservices.GetOrder
  ?client=757D621ABD1948F5BCBAD91F19BB70AC
  &organization=594C60A9C1154300AEB808C117437D7F
  &orderId=AC2661DED5E1EEA353FD72885A7EA1AC
  &selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.grandtotal as "total"
```

## Ejemplo de Request (por documentNo)

```http
GET /ws/com.doceleguas.pos.webservices.GetOrder
  ?client=757D621ABD1948F5BCBAD91F19BB70AC
  &organization=594C60A9C1154300AEB808C117437D7F
  &documentNo=VBS2-0000039
  &selectList=ord.c_order_id as "id", ord.documentno as "documentNo", ord.grandtotal as "total"
```

## Respuesta Exitosa

```json
{
  "success": true,
  "data": {
    "id": "AC2661DED5E1EEA353FD72885A7EA1AC",
    "documentNo": "VBS2-0000039",
    "total": 81.11
  }
}
```

## Respuesta - Orden No Encontrada

```json
{
  "success": false,
  "error": true,
  "message": "Order not found"
}
```

---

## Columnas Disponibles (selectList)

El parámetro `selectList` acepta cualquier expresión SQL válida. La query principal usa el alias `ord` para C_Order.

### Tabla Principal: C_Order (alias: ord)

| Columna SQL | Descripción |
|-------------|-------------|
| `ord.c_order_id` | UUID de la orden |
| `ord.documentno` | Número de documento |
| `ord.dateordered` | Fecha de orden |
| `ord.created` | Fecha de creación |
| `ord.docstatus` | Estado del documento |
| `ord.grandtotal` | Monto total |
| `ord.iscancelled` | Está cancelada |
| `ord.ad_org_id` | ID de organización |
| `ord.c_bpartner_id` | ID de cliente |

### JOINs Disponibles

| Alias | Tabla | JOIN Condition |
|-------|-------|----------------|
| `obpos` | obpos_applications | `ord.em_obpos_applications_id = obpos.obpos_applications_id` |
| `org` | ad_org | `ord.ad_org_id = org.ad_org_id` |
| `trxorg` | ad_org | `obpos.ad_org_id = trxorg.ad_org_id` |
| `bp` | c_bpartner | `ord.c_bpartner_id = bp.c_bpartner_id` |
| `salesrep` | ad_user | `ord.salesrep_id = salesrep.ad_user_id` |
| `doctype` | c_doctype | `ord.c_doctypetarget_id = doctype.c_doctype_id` |

---

## Respuestas de Error

### Parámetro Faltante

```json
{
  "success": false,
  "error": true,
  "message": "Missing required parameter: 'selectList'",
  "statusCode": 400
}
```

### Error de Query

```json
{
  "success": false,
  "error": true,
  "message": "Error executing query: <detalle>",
  "statusCode": 500
}
```

---

## Seguridad

### Prevención de SQL Injection

1. **selectList**: Se eliminan keywords peligrosos (UPDATE, DELETE, DROP, etc.)
2. **orderBy**: Solo se permiten caracteres alfanuméricos, puntos, guiones bajos
3. **Filtros**: Se usan parámetros preparados (`:param`)
4. **client/organization**: Aplicados en WHERE obligatoriamente

---

## Ejemplos curl

### GetOrdersFilter - Búsqueda por número de documento
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter?client=757D621ABD1948F5BCBAD91F19BB70AC&organization=594C60A9C1154300AEB808C117437D7F&selectList=ord.c_order_id+as+%22id%22%2C+ord.documentno+as+%22documentNo%22&f.documentno=VBS2&limit=10"
```

### GetOrder - Obtener orden por ID
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrder?client=757D621ABD1948F5BCBAD91F19BB70AC&organization=594C60A9C1154300AEB808C117437D7F&orderId=AC2661DED5E1EEA353FD72885A7EA1AC&selectList=ord.c_order_id+as+%22id%22%2C+ord.documentno+as+%22documentNo%22%2C+ord.grandtotal+as+%22total%22"
```

### GetOrder - Obtener orden por documentNo
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrder?client=757D621ABD1948F5BCBAD91F19BB70AC&organization=594C60A9C1154300AEB808C117437D7F&documentNo=VBS2-0000039&selectList=ord.c_order_id+as+%22id%22%2C+ord.documentno+as+%22documentNo%22%2C+ord.grandtotal+as+%22total%22"
```

---

*Documentación actualizada: 2025-02-04*
*Versión: 4.0 - GetOrder + GetOrdersFilter con NativeQuery*
