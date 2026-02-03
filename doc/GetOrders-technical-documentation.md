# GetOrders WebService - Documentación Técnica

## Resumen

El WebService `GetOrders` permite consultar órdenes del sistema POS utilizando el patrón arquitectónico **ProcessHQLQueryValidated**, el mismo patrón utilizado por `PaidReceiptsFilter` en el módulo `org.openbravo.retail.posterminal`.

---

## Arquitectura del Código

La implementación consiste en **3 clases** principales:

```
com.doceleguas.pos.webservices/
├── GetOrders.java                          (~420 líneas) - WebService wrapper HTTP → JSON
└── orders/
    ├── GetOrdersFilter.java                (~180 líneas) - Extiende ProcessHQLQueryValidated
    └── GetOrdersFilterProperties.java      (~140 líneas) - Define propiedades HQL extensibles
```

### Descripción de cada clase

| Clase | Responsabilidad |
|-------|-----------------|
| `GetOrders` | WebService HTTP que traduce parámetros GET a formato JSON para ProcessHQLQuery |
| `GetOrdersFilter` | Extiende `ProcessHQLQueryValidated`, genera query HQL, ejecuta contra BD |
| `GetOrdersFilterProperties` | Define las propiedades HQL devueltas, extensible via CDI |

### Beneficios de esta arquitectura

- **Acceso directo a BD**: Elimina la latencia HTTP del proxy anterior
- **Extensible via CDI**: Nuevas propiedades sin modificar código base
- **Testeable**: Puede mockearse el DAL para tests unitarios
- **Consistente**: Mismo patrón que otros endpoints Mobile Core

---

## Diagrama de Arquitectura

```
┌──────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│   Cliente    │────▶│      GetOrders       │────▶│   GetOrdersFilter   │
│   (GET)      │     │  (implements         │     │  (extends           │
│              │     │   WebService)        │     │   ProcessHQLQuery   │
└──────────────┘     └──────────────────────┘     │   Validated)        │
                              │                    └─────────────────────┘
                              │                              │
                              ▼                              ▼
                    ┌─────────────────┐           ┌──────────────────────┐
                    │ Traduce params  │           │ Ejecuta HQL directo  │
                    │ HTTP → JSON     │           │ contra la BD         │
                    └─────────────────┘           └──────────────────────┘
                                                            │
                                                            ▼
                                                  ┌──────────────────────┐
                                                  │ GetOrdersFilter-     │
                                                  │ Properties           │
                                                  │ (extensible via CDI) │
                                                  └──────────────────────┘
```

---

## Flujo de Ejecución

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              GetOrders WebService                         │
├──────────────────────────────────────────────────────────────────────────┤
│  1. Recibe request HTTP GET con parámetros de filtro                     │
│                                                                          │
│  2. buildJsonRequest() traduce params HTTP → JSON ProcessHQLQuery:       │
│     - Extrae client/organization de OBContext                            │
│     - Construye remoteFilters según tipo de filtro                       │
│     - Añade _limit, _offset, orderByClause                               │
│                                                                          │
│  3. Obtiene instancia GetOrdersFilter via CDI:                           │
│     CDI.current().select(GetOrdersFilter.class).get()                    │
│                                                                          │
│  4. Ejecuta filter.exec(writer, jsonRequest)                             │
│     - GetOrdersFilter genera HQL desde propiedades                       │
│     - Aplica filtros remotos                                             │
│     - Ejecuta query contra BD                                            │
│                                                                          │
│  5. Retorna respuesta JSON al cliente                                    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Endpoint API

### URL
```
GET /ws/com.doceleguas.pos.webservices.GetOrders
```

### Parámetros de Entrada

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `filter` | String | **Sí** | Tipo de filtro: `byId`, `byDocumentNo`, `byOrgOrderDate`, `byOrgOrderDateRange` |
| `limit` | Integer | No | Límite de resultados (default: sin límite) |
| `offset` | Integer | No | Offset para paginación (default: 0) |
| `orderBy` | String | No | Ordenamiento (default: `ord.creationDate desc`) |

### Parámetros por Tipo de Filtro

#### `byId` - Filtrar por ID de Orden
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byId&id=ABC123
```
| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `id` | Sí | UUID de la orden |

#### `byDocumentNo` - Filtrar por Número de Documento
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byDocumentNo&documentNo=ORD-001
```
| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `documentNo` | Sí | Número de documento (búsqueda parcial, case-insensitive) |

#### `byOrgOrderDate` - Filtrar por Organización y Fecha
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDate&organization=STORE1&orderDate=2025-01-15
```
| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `organization` | Sí | Nombre de organización (búsqueda parcial) |
| `orderDate` | Sí | Fecha de orden (formato: YYYY-MM-DD) |

#### `byOrgOrderDateRange` - Filtrar por Rango de Fechas
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDateRange&organization=STORE1&dateFrom=2025-01-01&dateTo=2025-01-31&limit=100
```
| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `organization` | Sí | Nombre de organización |
| `dateFrom` | Sí | Fecha desde (YYYY-MM-DD) |
| `dateTo` | Sí | Fecha hasta (YYYY-MM-DD) |

---

## Estructura de la Respuesta

### Respuesta Exitosa

```json
{
  "success": true,
  "data": [
    {
      "id": "AC2661DED5E1EEA353FD72885A7EA1AC",
      "documentNo": "AUTBE0504P99-0000039",
      "orderDate": "2025-11-17",
      "creationDate": "2025-11-17 18:14:02",
      "updated": "2025-11-17 18:14:02",
      "grossAmount": 81.11,
      "netAmount": 67.03,
      "documentStatus": "CO",
      "isCancelled": false,
      "isLayaway": false,
      "isSalesTransaction": true,
      "organizationId": "610BDE28B0AF4D4685FC9B475B635591",
      "organization": "AUTO 5 BIERGES",
      "organizationSearchKey": "0504",
      "terminalId": "3D3E84F1127F4FB78084D8C645791E20",
      "terminal": "0504099",
      "terminalName": "Terminal 099",
      "businessPartnerId": "BPID123...",
      "businessPartner": "CUST001",
      "businessPartnerName": "Cliente Ejemplo",
      "documentTypeId": "DTID123...",
      "documentType": "POS Order",
      "isReturn": false,
      "documentSubType": null,
      "currencyId": "102",
      "currency": "EUR",
      "priceIncludesTax": true,
      "priceListId": "PLID123...",
      "priceList": "Tarif Ventes",
      "warehouseId": "WHID123...",
      "warehouse": "AUTO 5 BIERGES",
      "salesRepresentativeId": "USERID123...",
      "salesRepresentative": "POS User",
      "description": null,
      "orderReference": null,
      "externalReference": null
    }
  ],
  "totalRows": 1
}
```

### Respuesta de Error

```json
{
  "success": false,
  "error": true,
  "message": "Missing required parameter: 'filter'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange",
  "statusCode": 400
}
```

---

## Propiedades Devueltas

Las propiedades son definidas en `GetOrdersFilterProperties` y son extensibles via CDI.

### Propiedades Base

| Categoría | Propiedades |
|-----------|-------------|
| **Core** | id, documentNo, orderDate, creationDate, updated |
| **Montos** | grossAmount, netAmount |
| **Estado** | documentStatus, isCancelled, isLayaway, isSalesTransaction |
| **Organización** | organizationId, organization, organizationSearchKey |
| **Terminal** | terminalId, terminal, terminalName |
| **Cliente** | businessPartnerId, businessPartner, businessPartnerName |
| **Documento** | documentTypeId, documentType, isReturn, documentSubType |
| **Moneda** | currencyId, currency, priceIncludesTax, priceListId, priceList |
| **Almacén** | warehouseId, warehouse |
| **Vendedor** | salesRepresentativeId, salesRepresentative |
| **Otros** | description, orderReference, externalReference |

---

## Extensibilidad

### Añadir Nuevas Propiedades

Para añadir propiedades sin modificar el código base, crea una clase con el qualifier `GetOrdersFilter_Extension`:

```java
package com.example.customizations;

import javax.enterprise.context.ApplicationScoped;
import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.mobile.core.model.HQLProperty;
import org.openbravo.mobile.core.model.ModelExtension;
import com.doceleguas.pos.webservices.orders.GetOrdersFilter;

@ApplicationScoped
@Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)
public class CustomOrderProperties extends ModelExtension {

  @Override
  public List<HQLProperty> getHQLProperties(Object params) {
    List<HQLProperty> properties = new ArrayList<>();
    // Propiedades personalizadas
    properties.add(new HQLProperty("ord.myCustomField", "customField"));
    properties.add(new HQLProperty("ord.anotherField", "anotherField"));
    return properties;
  }
  
  @Override
  public int getPriority() {
    return 100; // Se ejecuta después del default (0)
  }
}
```

Las nuevas propiedades serán automáticamente incluidas en la respuesta.

---

## Detalles de Implementación

### GetOrders.java (WebService Wrapper)

**Responsabilidades:**
- Recibir peticiones HTTP GET
- Validar parámetro `filter` obligatorio
- Traducir parámetros HTTP a formato JSON/remoteFilters
- Obtener `GetOrdersFilter` via CDI
- Manejar paginación y ordenamiento
- Formatear respuestas de éxito y error

**Métodos principales:**
- `doGet()`: Entry point del WebService
- `buildJsonRequest()`: Construye JSON desde parámetros HTTP
- `buildRemoteFilter()`: Crea objetos de filtro remoto
- `sanitizeOrderBy()`: Previene SQL injection en ordenamiento
- `sendErrorResponse()`: Respuestas de error estandarizadas

### GetOrdersFilter.java (Filtro HQL)

**Extiende:** `ProcessHQLQueryValidated`

**Responsabilidades:**
- Generar query HQL dinámicamente
- Aplicar filtros remotos
- Validar que existan filtros relevantes
- Manejar parámetros de fecha

**Métodos principales:**
- `getQueryValidated()`: Genera el HQL completo
- `getHqlProperties()`: Obtiene propiedades desde extensiones CDI
- `hasRelevantRemoteFilters()`: Valida filtros obligatorios
- `addCustomWhereClause()`: Añade cláusulas WHERE personalizadas

**Query HQL generado:**
```sql
SELECT {propiedades}
FROM Order AS ord
LEFT JOIN ord.obposApplications AS pos
LEFT JOIN ord.businessPartner AS bp
LEFT JOIN ord.salesRepresentative AS salesRep
LEFT JOIN ord.documentType AS docType
WHERE $filtersCriteria
  AND $hqlCriteria
  AND ord.client.id = $clientId
  AND ord.$orgId
  AND ord.obposIsDeleted = false
$orderByCriteria
```

### GetOrdersFilterProperties.java (Propiedades)

**Extiende:** `ModelExtension`

**Qualifier CDI:** `GetOrdersFilter_Extension`

**Responsabilidades:**
- Definir las 30+ propiedades HQL base
- Mapear expresiones HQL a nombres JSON

---

## Comparación con la Implementación Anterior

| Aspecto | Antes (HTTP Proxy) | Ahora (ProcessHQLQueryValidated) |
|---------|-------------------|----------------------------------|
| **Latencia** | +50-200ms (HTTP interno) | Sin overhead HTTP |
| **Extensibilidad** | Ninguna | Via CDI/ModelExtension |
| **Testabilidad** | Solo integración | Unit tests posibles |
| **Mantenimiento** | Dependía de ExportService | Autónomo |
| **Consistencia** | Diferente a otros endpoints | Igual que PaidReceiptsFilter |
| **Paginación** | Manual | Nativa (`_limit`, `_offset`) |
| **Ordenamiento** | No soportado | Nativo (`orderByClause`) |

---

## Dependencias

El módulo requiere:
- `org.openbravo.mobile.core` - Para `ProcessHQLQuery`, `HQLPropertyList`, `ModelExtension`
- `org.openbravo.retail.posterminal` - Para `ProcessHQLQueryValidated`

---

## Ejemplos de Uso

### Buscar orden por ID
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?filter=byId&id=AC2661DED5E1EEA353FD72885A7EA1AC"
```

### Buscar por número de documento
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?filter=byDocumentNo&documentNo=VBS2/0000"
```

### Buscar por organización y rango de fechas con paginación
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDateRange&organization=AUTO%205&dateFrom=2025-01-01&dateTo=2025-12-31&limit=50&offset=0"
```

---

*Documentación actualizada: 2026-02-03*
*Versión: 1.0*

