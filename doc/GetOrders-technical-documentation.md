# GetOrders WebService - Documentación Técnica

## Resumen

El WebService `GetOrders` permite consultar órdenes del sistema POS utilizando el patrón arquitectónico **ProcessHQLQueryValidated**, el mismo patrón utilizado por `PaidReceiptsFilter` en el módulo `org.openbravo.retail.posterminal`.

Esta implementación soporta **filtros dinámicos** similares a PaidReceiptsFilter, permitiendo combinar cualquier propiedad de filtro con diferentes operadores.

---

## Arquitectura del Código

La implementación consiste en **3 clases** principales:

```
com.doceleguas.pos.webservices/
├── GetOrders.java                          (~400 líneas) - WebService wrapper HTTP → JSON
└── orders/
    ├── GetOrdersFilter.java                (~250 líneas) - Extiende ProcessHQLQueryValidated
    └── GetOrdersFilterProperties.java      (~120 líneas) - Define propiedades HQL extensibles
```

### Descripción de cada clase

| Clase | Responsabilidad |
|-------|-----------------|
| `GetOrders` | WebService HTTP que traduce parámetros GET dinámicos a formato JSON para ProcessHQLQuery |
| `GetOrdersFilter` | Extiende `ProcessHQLQueryValidated`, genera query HQL, ejecuta contra BD |
| `GetOrdersFilterProperties` | Define las propiedades HQL devueltas, extensible via CDI |

### Beneficios de esta arquitectura

- **Acceso directo a BD**: Elimina la latencia HTTP del proxy anterior
- **Filtros dinámicos**: Combina cualquier propiedad de filtro con operadores flexibles
- **Extensible via CDI**: Nuevas propiedades sin modificar código base
- **Testeable**: Puede mockearse el DAL para tests unitarios
- **Consistente**: Mismo patrón que PaidReceiptsFilter y otros endpoints Mobile Core

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
                    │ Parsea filtros  │           │ Ejecuta HQL directo  │
                    │ dinámicos GET   │           │ contra la BD         │
                    │ → remoteFilters │           └──────────────────────┘
                    └─────────────────┘                     │
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
│  1. Recibe request HTTP GET con parámetros de filtro dinámicos           │
│                                                                          │
│  2. buildJsonRequest() parsea filtros dinámicos → JSON ProcessHQLQuery:  │
│     - Extrae client/organization de OBContext                            │
│     - Itera parámetros, detecta propiedades de filtro                    │
│     - Aplica modificadores _op y _isId                                   │
│     - Añade _limit, _offset, orderByClause                               │
│                                                                          │
│  3. Obtiene instancia GetOrdersFilter via CDI:                           │
│     CDI.current().select(GetOrdersFilter.class).get()                    │
│                                                                          │
│  4. Ejecuta filter.exec(writer, jsonRequest)                             │
│     - GetOrdersFilter genera HQL desde propiedades                       │
│     - SimpleQueryBuilder aplica filtros con operadores                   │
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

### Sintaxis de Filtros Dinámicos

Los filtros usan una sintaxis simple basada en parámetros GET:

| Patrón | Descripción | Ejemplo |
|--------|-------------|---------|
| `{propiedad}={valor}` | Valor del filtro | `documentNo=VBS2` |
| `{propiedad}_op={operador}` | Operador a usar (opcional) | `documentNo_op=equals` |
| `{propiedad}_isId=true` | Indica que el valor es un UUID | `id_isId=true` |

### Operadores Válidos

Los operadores son procesados por `SimpleQueryBuilder`:

| Operador | SQL Generado | Uso Típico |
|----------|--------------|------------|
| `equals` | `= value` | IDs, valores exactos |
| `notEquals` | `<> value` | Exclusiones |
| `greaterThan` | `> value` | Comparaciones numéricas |
| `lessThan` | `< value` | Comparaciones numéricas |
| `startsWith` | `LIKE 'value%'` | Búsquedas por prefijo |
| `contains` | `LIKE '%value%'` | Búsquedas parciales (default) |

> **Nota**: El operador por defecto es `contains` para texto y `equals` cuando `_isId=true`.

### Propiedades de Filtro Soportadas

| Propiedad | Tipo | Descripción |
|-----------|------|-------------|
| `id` | UUID | ID de la orden (usar con `_isId=true`) |
| `documentNo` | String | Número de documento |
| `organization` | UUID | ID de organización (usar con `_isId=true`) |
| `orgSearchKey` | String | Código de organización (ej: "E101") |
| `orgName` | String | Nombre de organización |
| `businessPartner` | UUID | ID del cliente (usar con `_isId=true`) |
| `orderType` | String | Tipo de orden: `ORD`, `RET`, `LAY`, `QT`, `verifiedReturns`, `payOpenTickets` |
| `totalamount` | Decimal | Monto total |

### Filtros de Fecha (Manejo Especial)

Las fechas se pasan como parámetros y se manejan en la cláusula WHERE del HQL:

| Parámetro | Formato | Descripción |
|-----------|---------|-------------|
| `orderDate` | YYYY-MM-DD | Fecha exacta |
| `dateFrom` | YYYY-MM-DD | Inicio de rango (usar con `dateTo`) |
| `dateTo` | YYYY-MM-DD | Fin de rango (usar con `dateFrom`) |

### Parámetros Reservados

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `_limit` (o `limit`) | Integer | Máximo de resultados |
| `_offset` (o `offset`) | Integer | Offset para paginación |
| `orderBy` | String | Ordenamiento (default: `ord.creationDate desc`) |

---

## Ejemplos de Uso

### Buscar por número de documento (búsqueda parcial)
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2
```
Genera filtro: `documentNo LIKE '%VBS2%'`

### Buscar por número de documento exacto
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2-001&documentNo_op=equals
```
Genera filtro: `documentNo = 'VBS2-001'`

### Buscar por ID de orden (UUID)
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?id=ABC-123&id_isId=true
```
Genera filtro: `id = 'ABC-123'`

### Buscar por organización y rango de fechas
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?orgSearchKey=E101&dateFrom=2025-01-01&dateTo=2025-12-31
```
Genera filtros:
- `orgSearchKey LIKE '%E101%'`
- `orderDate >= '2025-01-01' AND orderDate <= '2025-12-31'`

### Combinar múltiples filtros con paginación
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?orderType=ORD&orgSearchKey=E101&_limit=50&_offset=0
```

### Filtrar órdenes por monto mínimo
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?totalamount=100&totalamount_op=greaterThan&orgSearchKey=E101
```
Genera filtros:
- `totalamount > 100`
- `orgSearchKey LIKE '%E101%'`

---

## Estructura de la Respuesta

### Respuesta Exitosa

```json
{
  "success": true,
  "data": [
    {
      "id": "AC2661DED5E1EEA353FD72885A7EA1AC",
      "documentNo": "VBS2-0000039",
      "orderDate": "2025-11-17",
      "creationDate": "2025-11-17T18:14:02",
      "documentStatus": "CO",
      "iscancelled": false,
      "organization": "ABC123...",
      "orgSearchKey": "E101",
      "orgName": "Tienda Centro",
      "businessPartner": "BPID123...",
      "businessPartnerName": "Cliente Ejemplo",
      "totalamount": 81.11,
      "orderType": "ORD",
      "isAnonymousCustomerSale": false
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
  "message": "At least one filter parameter is required. Supported filters: [id, documentNo, ...], [orderDate, dateFrom, dateTo]",
  "statusCode": 400
}
```

### Error por Operador Inválido

```json
{
  "success": false,
  "error": true,
  "message": "Invalid operator 'iContains' for property 'documentNo'. Valid operators: [equals, notEquals, greaterThan, lessThan, startsWith, contains]",
  "statusCode": 400
}
```

---

## Propiedades Devueltas

Las propiedades son definidas en `GetOrdersFilterProperties` y son extensibles via CDI.

### Propiedades Base

| Categoría | Propiedades |
|-----------|-------------|
| **Core** | id, documentNo, orderDate, creationDate |
| **Montos** | totalamount |
| **Estado** | documentStatus, iscancelled |
| **Organización** | organization (UUID), orgSearchKey, orgName |
| **Org. Transacción** | trxOrganization (UUID), trxOrganizationName |
| **Cliente** | businessPartner (UUID), businessPartnerName |
| **Documento** | documentTypeId, orderType |
| **Entrega** | isdelivered, deliveryMode, deliveryDate, externalBusinessPartnerReference |
| **Facturación** | invoiceTerms, fullInvoice |
| **Otros** | isAnonymousCustomerSale |

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
- Recibir peticiones HTTP GET con filtros dinámicos
- Parsear parámetros y detectar propiedades de filtro
- Aplicar modificadores `_op` y `_isId`
- Validar operadores
- Traducir parámetros HTTP a formato JSON/remoteFilters
- Obtener `GetOrdersFilter` via CDI
- Manejar paginación y ordenamiento
- Formatear respuestas de éxito y error

**Métodos principales:**
- `doGet()`: Entry point del WebService
- `buildJsonRequest()`: Parsea filtros dinámicos y construye JSON
- `buildRemoteFilter()`: Crea objetos de filtro remoto con isId
- `sanitizeOrderBy()`: Previene SQL injection en ordenamiento
- `sendErrorResponse()`: Respuestas de error estandarizadas

**Constantes importantes:**
- `FILTER_PROPERTIES`: Propiedades de filtro soportadas
- `DATE_PROPERTIES`: Propiedades de fecha (manejo especial)
- `VALID_OPERATORS`: Operadores válidos de SimpleQueryBuilder

### GetOrdersFilter.java (Filtro HQL)

**Extiende:** `ProcessHQLQueryValidated`

**Responsabilidades:**
- Generar query HQL dinámicamente
- Aplicar filtros remotos via SimpleQueryBuilder
- Validar que existan filtros relevantes
- Manejar parámetros de fecha en WHERE clause

**Métodos principales:**
- `getQueryValidated()`: Genera el HQL completo
- `getHqlProperties()`: Obtiene propiedades desde extensiones CDI
- `hasRelevantRemoteFilters()`: Valida filtros obligatorios
- `addCustomWhereClause()`: Añade cláusulas WHERE para fechas
- `getParameterValues()`: Convierte strings de fecha a java.sql.Date

**JOINs utilizados:**
```sql
LEFT JOIN ord.obposApplications AS obpos
LEFT JOIN ord.organization AS org
LEFT JOIN obpos.organization AS trxOrg
LEFT JOIN ord.businessPartner AS bp
LEFT JOIN ord.salesRepresentative AS salesRep
LEFT JOIN ord.documentType AS docType
```

### GetOrdersFilterProperties.java (Propiedades)

**Extiende:** `ModelExtension`

**Qualifier CDI:** `GetOrdersFilter_Extension`

**Responsabilidades:**
- Definir las propiedades HQL base
- Mapear expresiones HQL a nombres JSON
- Generar lógica dinámica para orderType

---

## Comparación con PaidReceiptsFilter

| Aspecto | PaidReceiptsFilter | GetOrders |
|---------|-------------------|-----------|
| **Método HTTP** | POST con JSON body | GET con parámetros URL |
| **Formato filtros** | `remoteFilters` en JSON | Parámetros dinámicos `{prop}={val}&{prop}_op={op}` |
| **Manejo fechas** | remoteFilters directos | Parámetros → WHERE clause |
| **Operadores** | Todos los de SimpleQueryBuilder | Los mismos, con validación explícita |
| **Patrón base** | ProcessHQLQueryValidated | ProcessHQLQueryValidated |
| **Extensibilidad** | CDI/ModelExtension | CDI/ModelExtension |

---

## Dependencias

El módulo requiere:
- `org.openbravo.mobile.core` - Para `ProcessHQLQuery`, `HQLPropertyList`, `ModelExtension`
- `org.openbravo.retail.posterminal` - Para `ProcessHQLQueryValidated`

---

## Ejemplos de Uso (curl)

### Buscar por número de documento (parcial)
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2"
```

### Buscar por número de documento exacto
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2-001&documentNo_op=equals"
```

### Buscar por ID de orden (UUID)
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?id=AC2661DED5E1EEA353FD72885A7EA1AC&id_isId=true"
```

### Buscar por organización y rango de fechas con paginación
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?orgSearchKey=E101&dateFrom=2025-01-01&dateTo=2025-12-31&_limit=50&_offset=0"
```

### Buscar órdenes por tipo con organización
```bash
curl -u admin:admin \
  "http://localhost:8080/openbravo/ws/com.doceleguas.pos.webservices.GetOrders?orderType=ORD&orgSearchKey=E101"
```

---

*Documentación actualizada: 2026-02-03*
*Versión: 2.0 - Filtros Dinámicos*

