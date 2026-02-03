# Documentación Técnica y Funcional: Flujo de Búsqueda Remota y Selección de Órdenes en Openbravo POS

## Índice

1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Arquitectura General del Flujo](#2-arquitectura-general-del-flujo)
3. [PaidReceiptsFilter - Búsqueda/Filtrado de Órdenes](#3-paidreceiptsfilter---búsquedafiltrado-de-órdenes)
4. [PaidReceipts - Detalle Completo de una Orden](#4-paidreceipts---detalle-completo-de-una-orden)
5. [Sistema de Extensiones (ModelExtension)](#5-sistema-de-extensiones-modelextension)
6. [Sistema de Hooks](#6-sistema-de-hooks)
7. [Validación de Filtros y Ordenamiento](#7-validación-de-filtros-y-ordenamiento)
8. [Arquitectura Multi-Server](#8-arquitectura-multi-server)
9. [Diagramas de Secuencia](#9-diagramas-de-secuencia)
10. [Estructuras de Datos](#10-estructuras-de-datos)
11. [Puntos de Extensión](#11-puntos-de-extensión)
12. [Consideraciones de Rendimiento](#12-consideraciones-de-rendimiento)

---

## 1. Resumen Ejecutivo

El módulo `org.openbravo.retail.posterminal` implementa el flujo de búsqueda y selección de órdenes (receipts) en el POS de Openbravo mediante dos servicios principales:

| Servicio | Propósito | Endpoint |
|----------|-----------|----------|
| **PaidReceiptsFilter** | Búsqueda y filtrado de órdenes con paginación | `/org.openbravo.mobile.core.service.jsonrest/org.openbravo.retail.posterminal.PaidReceiptsFilter` |
| **PaidReceipts** | Obtención del detalle completo de una orden seleccionada | `/org.openbravo.mobile.core.service.jsonrest/org.openbravo.retail.posterminal.PaidReceipts` |

El flujo típico es:
1. El usuario abre la ventana de búsqueda de órdenes en el POS
2. Se envía una request a `PaidReceiptsFilter` con los filtros deseados
3. El POS muestra una lista resumida de órdenes
4. El usuario selecciona una orden específica
5. Se envía una request a `PaidReceipts` para obtener el detalle completo
6. El POS carga la orden completa con líneas, pagos, impuestos, etc.

---

## 2. Arquitectura General del Flujo

### 2.1 Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              POS CLIENT (Frontend)                           │
│  ┌─────────────────────────┐         ┌─────────────────────────────────┐    │
│  │   Orders Search View    │────────▶│   Order Detail View             │    │
│  └─────────────────────────┘         └─────────────────────────────────┘    │
└─────────────────┬───────────────────────────────┬───────────────────────────┘
                  │ POST (filtros)                │ POST (orderid)
                  ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          MOBILE CORE SERVICE LAYER                           │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │        org.openbravo.mobile.core.service.jsonrest                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────┬───────────────────────────────┬───────────────────────────┘
                  │                               │
                  ▼                               ▼
┌─────────────────────────────────┐   ┌───────────────────────────────────────┐
│     PaidReceiptsFilter          │   │          PaidReceipts                 │
│  extends ProcessHQLQueryValidated│   │     extends JSONProcessSimple        │
│                                 │   │                                       │
│  • Genera query HQL dinámico    │   │  • Carga orden por ID/documentNo      │
│  • Aplica filtros remotos       │   │  • Recupera líneas de orden           │
│  • Paginación (_limit/_offset)  │   │  • Recupera pagos asociados           │
│  • Ordenamiento dinámico        │   │  • Recupera impuestos                 │
│  • Validación de filtros        │   │  • Recupera promociones               │
│                                 │   │  • Ejecuta hooks de extensión         │
└─────────────────┬───────────────┘   └─────────────────┬─────────────────────┘
                  │                                     │
                  ▼                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        EXTENSIONES (ModelExtension)                          │
│  ┌─────────────────────────┐         ┌─────────────────────────────────┐    │
│  │ PaidReceiptsFilter-     │         │ PaidReceiptProperties           │    │
│  │ Properties              │         │ PaidReceiptLinesProperties      │    │
│  │ @Qualifier("PaidReceipts│         │ PaidReceiptsPaymentsProperties  │    │
│  │ Filter_Extension")      │         │ @Qualifier("PRExtension*")      │    │
│  └─────────────────────────┘         └─────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                  │                                     │
                  ▼                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           HIBERNATE / OBDal                                  │
│                              (Base de Datos)                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Jerarquía de Clases

```
SecuredJSONProcess (org.openbravo.mobile.core)
    │
    ├── ProcessHQLQuery
    │       │
    │       └── ProcessHQLQueryValidated (org.openbravo.mobile.core)
    │               │
    │               └── ProcessHQLQueryValidated (org.openbravo.retail.posterminal)
    │                       │
    │                       └── PaidReceiptsFilter ◄── Búsqueda de órdenes
    │
    └── JSONProcessSimple
            │
            └── PaidReceipts ◄── Detalle de orden
```

---

## 3. PaidReceiptsFilter - Búsqueda/Filtrado de Órdenes

### 3.1 Descripción Funcional

`PaidReceiptsFilter` es el servicio responsable de buscar y filtrar órdenes pagadas (receipts) en el sistema. Permite:

- **Filtrar por tipo de orden**: Ventas (ORD), Devoluciones (RET), Layaways (LAY), Cotizaciones (QT)
- **Filtrar por fecha**: Rango de fechas de orden
- **Filtrar por documento**: Número de documento exacto o parcial
- **Filtrar por cliente**: Business Partner
- **Filtrar por organización/tienda**: Cross-store search
- **Paginación**: Límite y offset para resultados
- **Ordenamiento dinámico**: Por cualquier columna permitida

### 3.2 Estructura de la Clase

```java
public class PaidReceiptsFilter extends ProcessHQLQueryValidated {
    
    // Qualifier para inyección de extensiones
    public static final String paidReceiptsFilterPropertyExtension = "PaidReceiptsFilter_Extension";
    
    @Inject @Any @Qualifier(paidReceiptsFilterPropertyExtension)
    private Instance<ModelExtension> extensions;
    
    // Métodos principales
    protected List<HQLPropertyList> getHqlProperties(JSONObject jsonsent);
    protected String getFilterEntity();
    protected List<String> getQueryValidated(JSONObject jsonsent);
    public void exec(Writer w, JSONObject jsonsent);
}
```

### 3.3 Generación de Query HQL

El método `getQueryValidated()` construye la consulta HQL dinámicamente:

```java
String hqlPaidReceipts = "select " 
    + receiptsHQLProperties.getHqlSelect()  // Columnas desde ModelExtension
    + " from Order as ord"
    + " where $filtersCriteria"              // Filtros remotos (reemplazado por el framework)
    + " and $hqlCriteria"                    // Criterios HQL adicionales
    + orderTypeHql                           // Filtro por tipo de orden
    + " and ord.client.id = $clientId"       // Filtro de cliente
    + getOganizationFilter(jsonsent)         // Filtro de organización
    + " and ord.obposIsDeleted = false"      // Excluir eliminados
    + " and ord.obposApplications is not null" // Solo órdenes POS
    + " and ord.documentStatus not in ('CJ', 'CA', 'NC', 'AE', 'ME')"; // Estados válidos
```

### 3.4 Filtros por Tipo de Orden

```java
switch (orderTypeFilter) {
    case "RET":  // Devoluciones
        orderTypeHql = "and ord.documentType.return = true";
        break;
    case "LAY":  // Layaways
        orderTypeHql = "and ord.obposIslayaway = true";
        break;
    case "ORD":  // Ventas normales
        orderTypeHql = "and ord.documentType.return = false " +
                       "and ord.documentType.sOSubType <> 'OB' " +
                       "and ord.obposIslayaway = false";
        break;
    case "verifiedReturns":  // Devoluciones verificadas
        orderTypeHql = "and ord.documentType.return = false " +
                       "and ord.documentType.sOSubType <> 'OB' " +
                       "and ord.obposIslayaway = false " +
                       "and cancelledorder is null";
        break;
    case "payOpenTickets":  // Tickets abiertos por pagar
        orderTypeHql = "and ord.grandTotalAmount > 0 " +
                       "and ord.documentType.sOSubType <> 'OB' " +
                       "and ord.documentStatus <> 'CL'";
        break;
}
```

### 3.5 Estructura del Request (Payload)

```json
{
    "csrfToken": "C6ECF81ECCB4468B8A9DB11ED379491F",
    "appName": "POS2",
    "client": "39363B0921BB4293B48383844325E84C",
    "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
    "pos": "1C9CB2318D17467BA0A76DB6CF309213",
    "terminalName": "VBS-2",
    "timeout": 15000,
    "parameters": {
        "terminalTime": "2026-02-03T00:14:15.120Z",
        "terminalTimeOffset": { "value": 180 }
    },
    "remoteFilters": [
        {
            "value": "OrderDate",
            "columns": ["orderDate"],
            "operator": "filter",
            "params": ["2026-01-23", null],
            "isId": false
        }
    ],
    "orderByProperties": null,
    "orderByClause": "creationDate desc, documentNo desc",
    "_limit": 50,
    "_offset": 0
}
```

### 3.6 Estructura del Response

```json
{
    "response": {
        "requestStreamConnectionTime": 1770077655123,
        "requestDataLoadedTime": 1770077655123,
        "responseDataSentTime": 1770077655123,
        "data": [
            {
                "cfisFiscalized": false,
                "orderType": "RET",
                "id": "436446427363E2FF240DFCF6A9EB847D",
                "documentTypeId": "B0745E66713C49199CE719BF5B88AF5C",
                "documentStatus": "CO",
                "documentNo": "VBS2/0000123",
                "creationDate": "2026-02-02T14:13:04-03:00",
                "orderDate": "2026-02-02T00:00:00-03:00",
                "businessPartner": "EDC5DBD82C3B4E3896B3955E041B242C",
                "businessPartnerName": "Arturo 333 Montoro",
                "totalamount": -384.4,
                "iscancelled": false,
                "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
                "organizationName": "Vall Blanca Store",
                "isLayaway": false,
                "status": "Refunded"
                // ... más propiedades
            }
        ],
        "startRow": 0,
        "endRow": 4,
        "status": 0,
        "lastUpdated": 1770077655131,
        "queryIndex": 0
    }
}
```

---

## 4. PaidReceipts - Detalle Completo de una Orden

### 4.1 Descripción Funcional

`PaidReceipts` recupera el detalle completo de una orden específica, incluyendo:

- **Cabecera de la orden**: Datos generales, cliente, fechas, totales
- **Líneas de la orden**: Productos, cantidades, precios, impuestos por línea
- **Líneas de envío (shipmentlines)**: Información de entregas
- **Líneas de factura (invoicelines)**: Información de facturación
- **Pagos (receiptPayments)**: Métodos de pago, montos, datos de pago
- **Impuestos (receiptTaxes)**: Desglose de impuestos del ticket
- **Promociones**: Descuentos aplicados a cada línea
- **Líneas relacionadas**: Para servicios vinculados a productos
- **Aprobaciones**: Lista de aprobaciones para layaways

### 4.2 Estructura de la Clase

```java
public class PaidReceipts extends JSONProcessSimple {
    
    // Qualifiers para inyección de extensiones
    public static final String paidReceiptsPropertyExtension = "PRExtension";
    public static final String paidReceiptsLinesPropertyExtension = "PRExtensionLines";
    public static final String paidReceiptsShipLinesPropertyExtension = "PRExtensionShipLines";
    public static final String paidReceiptsInvoiceLinesPropertyExtension = "PRExtensionInvoiceLines";
    public static final String paidReceiptsRelatedLinesPropertyExtension = "PRExtensionRelatedLines";
    public static final String paidReceiptsPaymentsPropertyExtension = "PRExtensionPayments";
    
    // Inyección de extensiones
    @Inject @Any @Qualifier(paidReceiptsPropertyExtension)
    private Instance<ModelExtension> extensions;
    
    @Inject @Any @Qualifier(paidReceiptsLinesPropertyExtension)
    private Instance<ModelExtension> extensionsLines;
    
    // ... más extensiones
    
    // Inyección de hooks
    @Inject @Any
    private Instance<PaidReceiptsHook> paidReceiptsHooks;
    
    @Inject @Any
    private Instance<PaidReceiptsPaymentsTypeTerminalHook> paymentsTypeInTerminalProcesses;
    
    // Método principal
    public JSONObject exec(JSONObject jsonsent) throws JSONException, ServletException;
}
```

### 4.3 Flujo de Ejecución del Método `exec()`

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PaidReceipts.exec()                          │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │ 1. Verificar Multi-Server   │
                    │    (¿Es Store Server?)      │
                    └─────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │ 2. getOrder(jsonsent)       │
                    │    - Por documentNo         │
                    │    - Por orderid            │
                    └─────────────────────────────┘
                                  │
              ┌───────────────────┴────────────────────┐
              │ Si order == null                       │
              ▼                                        ▼
┌──────────────────────────┐            ┌─────────────────────────────┐
│ Retornar error:          │            │ 3. Obtener PriceListVersion │
│ "OBPOS_PaidReceiptNotFound"│           └─────────────────────────────┘
└──────────────────────────┘                          │
                                                      ▼
                                        ┌─────────────────────────────┐
                                        │ 4. Query: Cabecera de Orden │
                                        │    (extensions PRExtension) │
                                        └─────────────────────────────┘
                                                      │
                                                      ▼
                                        ┌─────────────────────────────┐
                                        │ 5. Query: Líneas de Orden   │
                                        │    (extensions PRExtension- │
                                        │     Lines)                  │
                                        └─────────────────────────────┘
                                                      │
                              ┌────────────────────────┼────────────────────────┐
                              ▼                        ▼                        ▼
               ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
               │ 6a. Shipment Lines   │  │ 6b. Invoice Lines    │  │ 6c. Taxes por línea  │
               │     (PRExtension-    │  │     (PRExtension-    │  │     (OrderLineTax)   │
               │      ShipLines)      │  │      InvoiceLines)   │  │                      │
               └──────────────────────┘  └──────────────────────┘  └──────────────────────┘
                              │                        │                        │
                              └────────────────────────┼────────────────────────┘
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 7. Promociones por línea    │
                                        │    (OrderLineOffer)         │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 8. Líneas relacionadas      │
                                        │    (OrderlineService-       │
                                        │     Relation)               │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 9. Query: Pagos de la Orden │
                                        │    (FIN_Payment_Schedule-   │
                                        │     Detail)                 │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 10. Tipos de pago del       │
                                        │     terminal                │
                                        │     (OBPOS_App_Payment)     │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 11. Query: Impuestos de     │
                                        │     la Orden (OrderTax)     │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 12. Aprobaciones (si es     │
                                        │     Layaway)                │
                                        │     (OBPOS_Order_Approval)  │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 13. Ejecutar PaidReceipts-  │
                                        │     Hooks                   │
                                        │     (ej: LoadCoupons)       │
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 14. Verificar errores en    │
                                        │     ImportEntry             │
                                        │     (checkOrderInErrorEntry)│
                                        └─────────────────────────────┘
                                                       │
                                                       ▼
                                        ┌─────────────────────────────┐
                                        │ 15. Retornar JSON Response  │
                                        └─────────────────────────────┘
```

### 4.4 Estructura del Request (Payload)

```json
{
    "csrfToken": "C6ECF81ECCB4468B8A9DB11ED379491F",
    "appName": "POS2",
    "client": "39363B0921BB4293B48383844325E84C",
    "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
    "pos": "1C9CB2318D17467BA0A76DB6CF309213",
    "terminalName": "VBS-2",
    "timeout": 15000,
    "parameters": {
        "terminalTime": "2026-02-03T00:15:57.146Z",
        "terminalTimeOffset": { "value": 180, "type": "long" }
    },
    "orderid": "F5B00821E0F32EAFF8BD0792B1B68BE0",
    "isScanEvent": false,
    "forceRelatedVerifiedReturns": false,
    "crossStore": false
}
```

### 4.5 Estructura del Response (Simplificada)

```json
{
    "response": {
        "data": [
            {
                // === CABECERA DE LA ORDEN ===
                "orderid": "F5B00821E0F32EAFF8BD0792B1B68BE0",
                "documentNo": "VBS2/0000122",
                "orderDate": "2026-02-02T00:00:00",
                "totalamount": 384.4,
                "bp": "EDC5DBD82C3B4E3896B3955E041B242C",
                "businessPartner$_identifier": "Arturo 333 Montoro",
                "documentType": "511A9371A0F74195AA3F6D66C722729D",
                "organization": "D270A5AC50874F8BA67A88EE977F8E3B",
                "posTerminal": "1C9CB2318D17467BA0A76DB6CF309213",
                "warehouse": "CE7AB151695342FF879938F837F12E6E",
                "priceListVersionId": "9E4C6F91C917450AA8DF6590E3D350A5",
                "isLayaway": false,
                "iscancelled": false,
                "priceIncludesTax": true,
                
                // === LÍNEAS DE LA ORDEN ===
                "receiptLines": [
                    {
                        "id": "934E7D7587EC4C7A9E9FF58F0382D450",
                        "lineId": "06313915F967A5B962EF8A5CAAEE1980",
                        "orderId": "F5B00821E0F32EAFF8BD0792B1B68BE0",
                        "lineNo": 10,
                        "name": "Avalanche transceiver",
                        "productSearchKey": "WVG/M0019",
                        "quantity": 1,
                        "grossUnitPrice": 150.5,
                        "lineGrossAmount": 150.5,
                        "tax": "5235D8E99A2749EFA17A5C92A52AEFC6",
                        
                        // Líneas de envío
                        "shipmentlines": [
                            {
                                "shipLineId": "503EFE00AF85478C88B0AB51A62B6A3D",
                                "shipmentlineNo": 10,
                                "shipment": "VBS1000053",
                                "qty": 1,
                                "remainingQty": 0
                            }
                        ],
                        
                        // Líneas de factura
                        "invoicelines": [],
                        
                        // Impuestos por línea
                        "taxes": [
                            {
                                "taxId": "5235D8E99A2749EFA17A5C92A52AEFC6",
                                "identifier": "Entregas IVA 21%",
                                "taxableAmount": 124.38,
                                "taxAmount": 26.12,
                                "taxRate": 21
                            }
                        ],
                        
                        // Promociones aplicadas
                        "promotions": []
                    }
                ],
                
                // === PAGOS ===
                "receiptPayments": [
                    {
                        "paymentId": "C5D8FE8E6E17FCAC9B765C78086EEF57",
                        "amount": 384.4,
                        "paymentAmount": 384.4,
                        "paymentDate": "2026-02-02T00:00:00",
                        "name": "Cash",
                        "kind": "OBPOS_payment.cash",
                        "isocode": "EUR",
                        "isPrePayment": true,
                        "paymentData": {
                            "provider": {
                                "_identifier": "Cash",
                                "provider": "OBPOS2_PaymentProvider"
                            }
                        }
                    }
                ],
                
                // === IMPUESTOS TOTALES ===
                "receiptTaxes": [
                    {
                        "taxid": "5235D8E99A2749EFA17A5C92A52AEFC6",
                        "name": "Entregas IVA 21%",
                        "rate": 21,
                        "net": 317.69,
                        "amount": 66.71,
                        "gross": 384.4
                    }
                ],
                
                // === APROBACIONES (para layaways) ===
                "approvedList": [],
                
                // === CUPONES Y OTROS ===
                "obcpotfCoupons": [],
                "obrlpcoCoupons": [],
                
                // === FLAGS ===
                "recordInImportEntry": false
            }
        ],
        "status": 0
    }
}
```

---

## 5. Sistema de Extensiones (ModelExtension)

### 5.1 Concepto

El sistema `ModelExtension` permite definir qué propiedades/columnas se incluyen en los resultados de las consultas HQL. Esto facilita la extensibilidad del sistema sin modificar el código base.

### 5.2 Arquitectura de Extensiones

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ModelExtension (Interface)                         │
│    └── List<HQLProperty> getHQLProperties(Object params)                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ▲
          ┌───────────────────────────┼───────────────────────────┐
          │                           │                           │
┌─────────────────────────┐ ┌─────────────────────────┐ ┌─────────────────────────┐
│ PaidReceiptsFilter-     │ │ PaidReceiptProperties   │ │ PaidReceiptsLines-      │
│ Properties              │ │                         │ │ Properties              │
│ @Qualifier("PaidReceipts│ │ @Qualifier("PRExtension")│ │ @Qualifier("PRExtension │
│ Filter_Extension")      │ │                         │ │ Lines")                 │
└─────────────────────────┘ └─────────────────────────┘ └─────────────────────────┘
```

### 5.3 Ejemplo: PaidReceiptsFilterProperties

```java
@Qualifier(PaidReceiptsFilter.paidReceiptsFilterPropertyExtension)
public class PaidReceiptsFilterProperties extends ModelExtension {

    @Override
    public List<HQLProperty> getHQLProperties(final Object params) {
        ArrayList<HQLProperty> list = new ArrayList<HQLProperty>() {{
            add(new HQLProperty("ord.id", "id"));
            add(new HQLProperty("ord.documentType.id", "documentTypeId"));
            add(new HQLProperty("ord.documentStatus", "documentStatus"));
            add(new HQLProperty("ord.documentNo", "documentNo"));
            add(new HQLProperty("ord.creationDate", "creationDate"));
            add(new HQLProperty("ord.orderDate", "orderDate"));
            add(new HQLProperty("ord.businessPartner.id", "businessPartner"));
            add(new HQLProperty("ord.businessPartner.name", "businessPartnerName"));
            add(new HQLProperty("ord.grandTotalAmount", "totalamount"));
            add(new HQLProperty("ord.iscancelled", "iscancelled"));
            add(new HQLProperty("ord.organization.id", "organization"));
            add(new HQLProperty("ord.organization.name", "organizationName"));
            // Propiedades dinámicas basadas en tipo de orden
            String orderTypeFilter = PaidReceiptsFilter.getOrderTypeFilter((JSONObject) params);
            switch (orderTypeFilter) {
                case "ORD":
                    add(new HQLProperty("to_char('ORD')", "orderType"));
                    break;
                case "RET":
                    add(new HQLProperty("to_char('RET')", "orderType"));
                    break;
                // ...
                default:
                    add(new HQLProperty(
                        "(case when ord.documentType.return = true then 'RET'" +
                        " when ord.documentType.sOSubType = 'OB' then 'QT'" +
                        " when ord.obposIslayaway = true then 'LAY' else 'ORD' end)", 
                        "orderType"));
            }
        }};
        return list;
    }
}
```

### 5.4 Extensiones de PaidReceipts

| Qualifier | Propósito | Clase |
|-----------|-----------|-------|
| `PRExtension` | Cabecera de la orden | `PaidReceiptProperties` |
| `PRExtensionLines` | Líneas de la orden | Definido en el módulo |
| `PRExtensionShipLines` | Líneas de envío | Definido en el módulo |
| `PRExtensionInvoiceLines` | Líneas de factura | Definido en el módulo |
| `PRExtensionRelatedLines` | Líneas relacionadas (servicios) | Definido en el módulo |
| `PRExtensionPayments` | Pagos de la orden | `PaidReceiptsPaymentsProperties` |

---

## 6. Sistema de Hooks

### 6.1 Concepto

Los hooks permiten inyectar lógica adicional en puntos específicos del procesamiento sin modificar el código base. Siguen el patrón de diseño Strategy/Observer.

### 6.2 Hooks Disponibles en PaidReceipts

| Interface | Propósito | Ejemplo de Uso |
|-----------|-----------|----------------|
| `PaidReceiptsHook` | Procesar la orden completa al final | Cargar cupones (`LoadCoupons`) |
| `PaidReceiptsPaymentsTypeHook` | Procesar tipos de pago | Añadir información de tipos de pago |
| `PaidReceiptsPaymentsTypeTerminalHook` | Procesar tipos de pago por terminal | Pagos específicos del terminal |
| `PaidReceiptsPaymentsInHook` | Procesar cada pago individual | Añadir datos a cada pago |

### 6.3 Implementación de un Hook

```java
public interface PaidReceiptsHook {
    void exec(String orderId, JSONObject paidReceipt) throws Exception;
    default int getPriority() { return 100; }  // Menor = se ejecuta primero
}

// Ejemplo de implementación
public class LoadCoupons implements PaidReceiptsHook {
    
    @Override
    public void exec(String orderId, JSONObject paidReceipt) throws Exception {
        // Cargar cupones asociados a la orden
        JSONArray coupons = loadCouponsForOrder(orderId);
        paidReceipt.put("obcpotfCoupons", coupons);
    }
    
    @Override
    public int getPriority() {
        return 50;  // Se ejecuta antes que hooks con prioridad 100
    }
}
```

### 6.4 Ejecución de Hooks

```java
private void executePaidReceiptsHooks(final String orderId, final JSONObject paidReceipt) 
        throws Exception {
    List<PaidReceiptsHook> unsortedHooks = new ArrayList<>();
    
    for (final PaidReceiptsHook paidReceiptsHook : paidReceiptsHooks) {
        unsortedHooks.add(paidReceiptsHook);
    }
    
    // Ordenar por prioridad (menor prioridad = se ejecuta primero)
    List<PaidReceiptsHook> sortedHooks = unsortedHooks.stream()
        .sorted(Comparator.comparing(PaidReceiptsHook::getPriority))
        .collect(Collectors.toList());
    
    for (final PaidReceiptsHook paidReceiptsHook : sortedHooks) {
        paidReceiptsHook.exec(orderId, paidReceipt);
    }
}
```

---

## 7. Validación de Filtros y Ordenamiento

### 7.1 ProcessHQLQueryValidated

Esta clase base proporciona validación de filtros y ordenamiento para prevenir inyección HQL y restringir operaciones no permitidas.

### 7.2 Validación de Ordenamiento

```java
protected void validateSorting(JSONObject jsonsent) throws JSONException {
    if (jsonsent.has("orderByProperties") && jsonsent.get("orderByProperties") != JSONObject.NULL) {
        JSONArray orderByProperties = jsonsent.getJSONArray("orderByProperties");
        for (int i = 0; i < orderByProperties.length(); i++) {
            JSONObject orderByProperty = orderByProperties.getJSONObject(i);
            if (orderByProperty.has("property")) {
                String column = orderByProperty.getString("property");
                String sortingProperty = getFullSortingPreferenceProperty(column);
                if (sortingProperty != null) {
                    validateProperty(column, sortingProperty, sortingErrors);
                }
            }
        }
    }
}
```

### 7.3 Validación de Filtros

```java
protected void validateFiltering(JSONObject jsonsent) throws JSONException {
    if (jsonsent.has("remoteFilters")) {
        JSONArray remoteFilters = jsonsent.getJSONArray("remoteFilters");
        for (int i = 0; i < remoteFilters.length(); i++) {
            JSONObject remoteFilter = remoteFilters.getJSONObject(i);
            JSONArray columns = remoteFilter.getJSONArray("columns");
            String value = remoteFilter.getString("value");
            if (!value.equals("")) {
                for (int j = 0; j < columns.length(); j++) {
                    String column = (String) columns.get(j);
                    String filteringProperty = getFullFilteringPreferenceProperty(column);
                    if (filteringProperty != null) {
                        validateProperty(column, filteringProperty, filterErrors);
                    }
                }
            }
        }
    }
}
```

### 7.4 Preferencias de Validación

Las preferencias se buscan en `ADPreference` con el formato:
- Ordenamiento: `%EntitySelector_OrderFilter_{column}_s`
- Filtrado: `%EntitySelector_OrderFilter_{column}_f`

---

## 8. Arquitectura Multi-Server

### 8.1 Concepto

Openbravo POS soporta arquitectura multi-servidor donde hay:
- **Central Server**: Servidor central con todos los datos
- **Store Servers**: Servidores locales en cada tienda

### 8.2 Manejo en PaidReceiptsFilter

```java
@Override
public void exec(Writer w, JSONObject jsonsent) throws IOException, ServletException {
    Writer temporal = new StringWriter();
    super.exec(temporal, jsonsent);
    String data = temporal.toString();
    try {
        JSONObject result = new JSONObject("{" + w.toString() + "}");
        // Si es Store Server y está escaneando y no hay resultados locales
        if (MobileServerController.getInstance().isThisAStoreServer() 
                && isScanning(jsonsent)
                && result.optLong("totalRows") == 0) {
            // Buscar en el servidor central
            JSONObject centralResult = MobileServerRequestExecutor.getInstance()
                .executeCentralRequest(
                    MobileServerUtils.OBWSPATH + PaidReceiptsFilter.class.getName(),
                    jsonsent);
            data = centralResult.toString().substring(1, centralResult.toString().length() - 1);
        }
    } catch (JSONException e) {
        // Do nothing
    }
    w.write(data);
}
```

### 8.3 Manejo en PaidReceipts

```java
@Override
public JSONObject exec(JSONObject jsonsent) throws JSONException, ServletException {
    if (MobileServerController.getInstance().getCentralServer() != null) {
        final String ORIGIN_CENTRAL = MobileServerController.getInstance()
            .getCentralServer()
            .getName();
        // Si es Store Server y la request viene del Central
        if (MobileServerController.getInstance().isThisAStoreServer()
                && ORIGIN_CENTRAL.equals(jsonsent.optString("originServer"))) {
            // Ejecutar en el servidor central
            return MobileServerRequestExecutor.getInstance()
                .executeCentralRequest(
                    MobileServerUtils.OBWSPATH + PaidReceipts.class.getName(),
                    jsonsent);
        }
    }
    // ... resto del procesamiento
}
```

---

## 9. Diagramas de Secuencia

### 9.1 Flujo Completo de Búsqueda y Selección

```
┌──────────┐     ┌─────────────────┐     ┌─────────────────────┐     ┌──────────────┐
│   POS    │     │ PaidReceipts-   │     │    PaidReceipts     │     │   Database   │
│  Client  │     │    Filter       │     │                     │     │              │
└────┬─────┘     └────────┬────────┘     └──────────┬──────────┘     └──────┬───────┘
     │                    │                         │                       │
     │ 1. Abrir búsqueda  │                         │                       │
     │    de órdenes      │                         │                       │
     │────────────────────▶                         │                       │
     │                    │                         │                       │
     │ 2. POST /PaidReceiptsFilter                  │                       │
     │    {remoteFilters, _limit, _offset}          │                       │
     │────────────────────▶                         │                       │
     │                    │                         │                       │
     │                    │ 3. Validar filtros      │                       │
     │                    │────────┐                │                       │
     │                    │        │                │                       │
     │                    │◀───────┘                │                       │
     │                    │                         │                       │
     │                    │ 4. Construir query HQL  │                       │
     │                    │────────┐                │                       │
     │                    │        │                │                       │
     │                    │◀───────┘                │                       │
     │                    │                         │                       │
     │                    │ 5. SELECT órdenes       │                       │
     │                    │─────────────────────────────────────────────────▶
     │                    │                         │                       │
     │                    │                         │     6. ResultSet      │
     │                    │◀─────────────────────────────────────────────────
     │                    │                         │                       │
     │ 7. JSON Response   │                         │                       │
     │    {data: [...]}   │                         │                       │
     │◀────────────────────                         │                       │
     │                    │                         │                       │
     │ 8. Mostrar lista   │                         │                       │
     │    de órdenes      │                         │                       │
     │────────┐           │                         │                       │
     │        │           │                         │                       │
     │◀───────┘           │                         │                       │
     │                    │                         │                       │
     │ 9. Usuario selecciona orden                  │                       │
     │────────┐           │                         │                       │
     │        │           │                         │                       │
     │◀───────┘           │                         │                       │
     │                    │                         │                       │
     │ 10. POST /PaidReceipts                       │                       │
     │     {orderid: "..."}                         │                       │
     │──────────────────────────────────────────────▶                       │
     │                    │                         │                       │
     │                    │                         │ 11. getOrder()        │
     │                    │                         │──────────────────────▶│
     │                    │                         │◀──────────────────────│
     │                    │                         │                       │
     │                    │                         │ 12. Query cabecera    │
     │                    │                         │──────────────────────▶│
     │                    │                         │◀──────────────────────│
     │                    │                         │                       │
     │                    │                         │ 13. Query líneas      │
     │                    │                         │──────────────────────▶│
     │                    │                         │◀──────────────────────│
     │                    │                         │                       │
     │                    │                         │ 14. Query pagos       │
     │                    │                         │──────────────────────▶│
     │                    │                         │◀──────────────────────│
     │                    │                         │                       │
     │                    │                         │ 15. Query impuestos   │
     │                    │                         │──────────────────────▶│
     │                    │                         │◀──────────────────────│
     │                    │                         │                       │
     │                    │                         │ 16. Ejecutar hooks    │
     │                    │                         │────────┐              │
     │                    │                         │        │              │
     │                    │                         │◀───────┘              │
     │                    │                         │                       │
     │ 17. JSON Response completo                   │                       │
     │     {orderid, receiptLines, receiptPayments, │                       │
     │      receiptTaxes, ...}                      │                       │
     │◀──────────────────────────────────────────────                       │
     │                    │                         │                       │
     │ 18. Cargar orden   │                         │                       │
     │     en POS         │                         │                       │
     │────────┐           │                         │                       │
     │        │           │                         │                       │
     │◀───────┘           │                         │                       │
```

---

## 10. Estructuras de Datos

### 10.1 Entidades de Base de Datos Involucradas

| Entidad | Tabla | Descripción |
|---------|-------|-------------|
| `Order` | `C_ORDER` | Cabecera de la orden |
| `OrderLine` | `C_ORDERLINE` | Líneas de la orden |
| `OrderTax` | `C_ORDERTAX` | Impuestos de la orden |
| `OrderLineTax` | `C_ORDERLINETAX` | Impuestos por línea |
| `OrderLineOffer` | `C_ORDERLINE_OFFER` | Promociones por línea |
| `FIN_Payment` | `FIN_PAYMENT` | Pagos |
| `FIN_Payment_Schedule` | `FIN_PAYMENT_SCHEDULE` | Programación de pagos |
| `FIN_Payment_ScheduleDetail` | `FIN_PAYMENT_SCHEDULEDETAIL` | Detalle de pagos programados |
| `MaterialMgmtShipmentInOutLine` | `M_INOUTLINE` | Líneas de envío |
| `Invoice` | `C_INVOICE` | Facturas |
| `InvoiceLine` | `C_INVOICELINE` | Líneas de factura |
| `OBPOS_App_Payment` | `OBPOS_APP_PAYMENT` | Métodos de pago del terminal |
| `OBPOSApplications` | `OBPOS_APPLICATIONS` | Terminal POS |

### 10.2 Relaciones Principales

```
Order (1) ─────────────────────────────── (N) OrderLine
   │                                           │
   │                                           │
   └──── (1) ────── (N) OrderTax               └──── (1) ────── (N) OrderLineTax
   │                                           │
   │                                           │
   └──── (1) ────── (N) FIN_Payment_Schedule   └──── (1) ────── (N) OrderLineOffer
                         │
                         │
                         └──── (1) ────── (N) FIN_Payment_ScheduleDetail
                                                │
                                                │
                                                └──── (N) ────── (1) FIN_Payment
```

---

## 11. Puntos de Extensión

### 11.1 Añadir Propiedades a PaidReceiptsFilter

Crear una nueva clase que extienda `ModelExtension` con el qualifier `PaidReceiptsFilter_Extension`:

```java
@Qualifier(PaidReceiptsFilter.paidReceiptsFilterPropertyExtension)
public class CustomPaidReceiptsFilterProperties extends ModelExtension {
    
    @Override
    public List<HQLProperty> getHQLProperties(final Object params) {
        ArrayList<HQLProperty> list = new ArrayList<>();
        // Añadir propiedades personalizadas
        list.add(new HQLProperty("ord.customField", "customField"));
        return list;
    }
}
```

### 11.2 Añadir Propiedades a PaidReceipts

Crear clases con los qualifiers correspondientes:

```java
// Para cabecera de orden
@Qualifier(PaidReceipts.paidReceiptsPropertyExtension)
public class CustomPaidReceiptProperties extends ModelExtension { ... }

// Para líneas de orden
@Qualifier(PaidReceipts.paidReceiptsLinesPropertyExtension)
public class CustomPaidReceiptLinesProperties extends ModelExtension { ... }

// Para pagos
@Qualifier(PaidReceipts.paidReceiptsPaymentsPropertyExtension)
public class CustomPaidReceiptPaymentsProperties extends ModelExtension { ... }
```

### 11.3 Implementar un Hook

```java
public class CustomPaidReceiptsHook implements PaidReceiptsHook {
    
    @Override
    public void exec(String orderId, JSONObject paidReceipt) throws Exception {
        // Lógica personalizada
        paidReceipt.put("customData", loadCustomData(orderId));
    }
    
    @Override
    public int getPriority() {
        return 200;  // Se ejecuta después de los hooks estándar
    }
}
```

### 11.4 Extender PaidReceiptsFilter

```java
public class CustomPaidReceiptsFilter extends PaidReceiptsFilter {
    
    @Override
    protected String addWhereClause(JSONObject jsonsent) {
        // Añadir cláusulas WHERE adicionales
        return " AND ord.customCondition = true";
    }
}
```

---

## 12. Consideraciones de Rendimiento

### 12.1 Optimizaciones Implementadas

1. **Paginación**: Uso de `_limit` y `_offset` para limitar resultados
2. **ScrollableResults**: `ProcessHQLQuery` usa `ScrollableResults` para streaming eficiente
3. **Lazy Loading**: Las extensiones se cargan bajo demanda
4. **Admin Mode**: `OBContext.setAdminMode()` para bypass de seguridad cuando es necesario

### 12.2 Recomendaciones

1. **Filtros significativos**: Siempre incluir filtros para limitar resultados
2. **Índices**: Asegurar índices en columnas frecuentemente filtradas:
   - `C_ORDER.DOCUMENTNO`
   - `C_ORDER.ORDERDATE`
   - `C_ORDER.DOCUMENTSTATUS`
   - `C_ORDER.AD_ORG_ID`
   - `C_ORDER.OBPOS_APPLICATIONS_ID`
3. **Limit razonable**: Usar `_limit` de 50-100 registros
4. **Cross-store**: Evitar búsquedas cross-store innecesarias

### 12.3 Queries Críticos

```sql
-- Query principal de PaidReceiptsFilter (simplificado)
SELECT ord.id, ord.documentNo, ord.orderDate, ord.grandTotalAmount, ...
FROM C_ORDER ord
WHERE ord.AD_CLIENT_ID = :clientId
  AND ord.AD_ORG_ID IN (:orgIds)
  AND ord.OBPOS_ISDELETED = 'N'
  AND ord.OBPOS_APPLICATIONS_ID IS NOT NULL
  AND ord.DOCUMENTSTATUS NOT IN ('CJ', 'CA', 'NC', 'AE', 'ME')
  -- + filtros dinámicos
ORDER BY ord.CREATED DESC, ord.DOCUMENTNO DESC
LIMIT 50 OFFSET 0;

-- Query de cabecera en PaidReceipts
SELECT ord.*, pos.*, salesRep.*, replacedOrder.*
FROM C_ORDER ord
LEFT JOIN OBPOS_APPLICATIONS pos ON ord.OBPOS_APPLICATIONS_ID = pos.OBPOS_APPLICATIONS_ID
LEFT JOIN AD_USER salesRep ON ord.SALESREP_ID = salesRep.AD_USER_ID
LEFT JOIN C_ORDER replacedOrder ON ord.REPLACEDORDER_ID = replacedOrder.C_ORDER_ID
WHERE ord.C_ORDER_ID = :orderId;
```

---

## Anexo A: Propiedades de PaidReceiptProperties

```java
// Propiedades de cabecera de orden (PRExtension)
"ord.documentNo" -> "documentNo"
"ord.obposSequencename" -> "obposSequencename"
"ord.obposSequencenumber" -> "obposSequencenumber"
"ord.orderDate" -> "orderDate"
"ord.creationDate" -> "creationDate"
"ord.createdBy.id" -> "createdBy"
"ord.updatedBy.id" -> "updatedBy"
"ord.businessPartner.id" -> "bp"
"ord.partnerAddress.id" -> "bpLocId"
"ord.invoiceAddress.id" -> "bpBillLocId"
"ord.grandTotalAmount" -> "totalamount"
"salesRepresentative.name" -> "salesRepresentative$_identifier"
"ord.documentType.id" -> "documentType"
"ord.organization.obposCDoctyperet.id" -> "documentTypeReturnId"
"ord.warehouse.id" -> "warehouse"
"ord.description" -> "description"
"ord.currency.iSOCode" -> "currency$_identifier"
"pos.id" -> "posTerminal"
"pos.name" -> "posTerminal$_identifier"
"ord.businessPartner.name" -> "businessPartner$_identifier"
"ord.currency.id" -> "currency"
"ord.priceList.id" -> "priceList"
"salesRepresentative.id" -> "salesRepresentative"
"ord.organization.id" -> "organization"
"ord.organization.name" -> "organization$_identifier"
"ord.client.id" -> "client"
"ord.obposAppCashup" -> "obposAppCashup"
"(case when ord.documentType.sOSubType = 'OB' then true else false end)" -> "isQuotation"
"ord.summedLineAmount" -> "totalNetAmount"
"ord.obposIslayaway" -> "isLayaway"
"ord.priceList.priceIncludesTax" -> "priceIncludesTax"
"replacedOrder.documentNo" -> "replacedorder_documentNo"
"replacedOrder.id" -> "replacedorder"
"ord.iscancelled" -> "iscancelled"
"'false'" -> "isModified"
"ord.updated" -> "loaded"
"ord.cashVAT" -> "cashVAT"
"ord.invoiceTerms" -> "invoiceTerms"
"ord.externalBusinessPartnerReference" -> "externalBusinessPartnerReference"
```

---

## Anexo B: Propiedades de PaidReceiptsFilterProperties

```java
// Propiedades para filtrado de órdenes (PaidReceiptsFilter_Extension)
"ord.id" -> "id"
"ord.documentType.id" -> "documentTypeId"
"ord.documentStatus" -> "documentStatus"
"ord.documentNo" -> "documentNo"
"ord.creationDate" -> "creationDate"
"ord.orderDate" -> "orderDate"
"ord.orderDate" -> "orderDateFrom"
"ord.orderDate" -> "orderDateTo"
"ord.businessPartner.id" -> "businessPartner"
"ord.businessPartner.name" -> "businessPartnerName"
"ord.grandTotalAmount" -> "totalamount"
"ord.grandTotalAmount" -> "totalamountFrom"
"ord.grandTotalAmount" -> "totalamountTo"
"ord.iscancelled" -> "iscancelled"
"ord.organization.id" -> "organization"
"ord.organization.name" -> "organizationName"
"ord.obposApplications.organization.id" -> "trxOrganization"
"ord.externalBusinessPartnerReference" -> "externalBusinessPartnerReference"
// orderType es calculado dinámicamente según el filtro
```

---

*Documentación generada el 2026-02-02*
*Módulo: org.openbravo.retail.posterminal*
*Versión: Openbravo POS 2.x*
