# Documentación del Módulo `com.doceleguas.pos.webservices`

## Información General

| Atributo | Valor |
|----------|-------|
| **Nombre** | POS Web Services |
| **Versión** | 1.0.0 |
| **Paquete Java** | `com.doceleguas.pos.webservices` |
| **Tipo de Licencia** | OBPL (Openbravo Public License) |

---

## Descripción General

Este módulo implementa una serie de **Web Services personalizados** para extender la funcionalidad del sistema POS (Point of Sale) de Openbravo. Proporciona endpoints REST que permiten:

1. **Consultar datos maestros** (MasterData) de forma flexible y personalizada
2. **Gestionar autenticación** de usuarios para terminales POS
3. **Cargar configuración de terminales**
4. **Gestionar Business Partners** (clientes) incluyendo sus ubicaciones y contactos

---

## Arquitectura del Módulo

### Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          com.doceleguas.pos.webservices                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        WEB SERVICES (Endpoints)                       │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │  MasterDataWebService    │ GetMasterDataModelsWebService            │   │
│  │  LoadTerminal            │ SaveBusinessPartner                      │   │
│  │  Login                   │                                          │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        MODELOS DE DATOS (Model)                       │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │  OCBusinessPartner  │ OCProduct    │ OCCountry   │ OCRegion         │   │
│  │  OCDiscount         │ OCTaxCategory│ OCTaxRate   │ OCTaxZone        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        AUTENTICACIÓN                                  │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │  OCMobileCoreLoginHandler    │ OCPOSLoginHandler                    │   │
│  │  OCMobileCoreLoginController │ OCMobileCoreLoginControllerMBean     │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        HOOKS Y UTILIDADES                             │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │  PreOrderLoaderHook          │ ResponseBufferWrapper                │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Endpoints Disponibles

Los endpoints están registrados en el archivo `config/com.doceleguas.pos.webservices-provider-config.xml`:

| Endpoint | Clase | Método | Descripción |
|----------|-------|--------|-------------|
| `/ws/com.doceleguas.pos.webservices.MasterDataService` | `MasterDataWebService` | GET | Consulta datos maestros dinámicamente |
| `/ws/com.doceleguas.pos.webservices.GetMasterDataModels` | `GetMasterDataModelsWebService` | GET | Lista los modelos de masterdata disponibles |
| `/ws/com.doceleguas.pos.webservices.Terminal` | `LoadTerminal` | GET | Carga la configuración de un terminal POS |
| `/ws/com.doceleguas.pos.webservices.SaveBusinessPartner` | `SaveBusinessPartner` | POST | Guarda/actualiza un Business Partner |
| `/ws/com.doceleguas.pos.webservices.GetOrders` | `GetOrders` | GET | Consulta órdenes del backend con filtros |

---

## Descripción Detallada de Cada Componente

---

### 1. `MasterDataWebService`

**Archivo:** `MasterDataWebService.java`

**Propósito:** Endpoint genérico para consultar cualquier modelo de datos maestros del sistema POS.

#### Flujo de Ejecución

```
┌─────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│   Cliente   │────▶│ MasterDataWebService│────▶│  Model Instance │
│   (GET)     │     │                     │     │  (ej: OCProduct)│
└─────────────┘     └─────────────────────┘     └─────────────────┘
                              │                          │
                              ▼                          ▼
                    ┌─────────────────┐        ┌─────────────────┐
                    │ Detectar versión│        │  createQuery()  │
                    │ (v2 o estándar) │        │     SQL nativo  │
                    └─────────────────┘        └─────────────────┘
```

#### Parámetros de Entrada

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `client` | String | Sí | ID del cliente (AD_Client_ID) |
| `organization` | String | Sí | ID de la organización (AD_Org_ID) |
| `pos` | String | Sí | ID del terminal POS |
| `model` | String | Sí | Nombre del modelo a consultar (ej: `BusinessPartner`, `Product`) |
| `offset` | Integer | No | Desplazamiento para paginación (default: 0) |
| `limit` | Integer | No | Límite de registros (default: 1000) |
| `isMasterdata` | Boolean | No | Indica si es consulta de masterdata |
| `selectList` | String | No | Lista de campos SELECT personalizados (v2) |
| `v2` | Boolean | No | Activa el modo v2 (consulta SQL nativa personalizable) |
| `lastUpdated` | Long | No | Timestamp para consultas incrementales |

#### Ejemplo de Uso (v2)

```http
GET /ws/com.doceleguas.pos.webservices.MasterDataService?
    client=757D621ABD1948F5BCBAD91F19BB70AC&
    organization=594C60A9C1154300AEB808C117437D7F&
    pos=3D3E84F1127F4FB78084D8C645791E20&
    model=BusinessPartner&
    offset=0&
    limit=1&
    isMasterdata=true&
    selectList=e.c_bpartner_id+as+"id"%2C+e.name+as+"name"&
    v2=true
```

#### Respuesta

```json
{
    "model": "BusinessPartner",
    "data": [
        {
            "id": "ABC123...",
            "name": "Cliente Ejemplo",
            "isActive": true,
            "locations": [...],
            "contact": {...}
        }
    ]
}
```

#### Características Técnicas

1. **Modo v2 (SQL Nativo):**
   - Permite especificar campos SELECT personalizados
   - Utiliza `NativeQuery` de Hibernate con `ScrollableResults`
   - Sanitización de SQL para prevenir inyección (elimina palabras reservadas: SELECT, UPDATE, DELETE, DROP)
   - Soporte para consultas incrementales con `lastUpdated`

2. **Modo Estándar:**
   - Utiliza `MasterDataProcessHQLQuery` del core de Openbravo Mobile
   - Delega la ejecución al modelo registrado via CDI

3. **Seguridad:**
   - Establece el contexto OB (`OBContext`) con los parámetros del request
   - Filtra por clientes y organizaciones accesibles

---

### 2. `GetMasterDataModelsWebService`

**Archivo:** `GetMasterDataModelsWebService.java`

**Propósito:** Listar todos los modelos de MasterData disponibles en el sistema.

#### Endpoint

```http
GET /ws/com.doceleguas.pos.webservices.GetMasterDataModels
```

#### Respuesta

```json
{
    "models": [
        "BusinessPartner",
        "Product",
        "Country",
        "Region",
        "Discount",
        "TaxCategory",
        "TaxRate",
        "TaxZone"
    ]
}
```

#### Funcionamiento Técnico

1. Utiliza CDI (`WeldUtils`) para obtener todas las instancias de `MasterDataProcessHQLQuery`
2. Extrae el nombre de cada modelo usando `getName()`
3. Devuelve un array JSON con todos los nombres disponibles

---

### 3. `LoadTerminal`

**Archivo:** `LoadTerminal.java`

**Propósito:** Cargar la configuración completa de un terminal POS.

#### Endpoint

```http
GET /ws/com.doceleguas.pos.webservices.Terminal?terminalName=POS001
```

#### Parámetros

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `terminalName` | String | Sí | Search Key del terminal POS |

#### Flujo de Ejecución

```
┌──────────┐    ┌──────────────┐    ┌─────────────────┐    ┌───────────────┐
│ Request  │───▶│ LoadTerminal │───▶│ getTerminal()   │───▶│ Terminal.exec │
│          │    │              │    │ (OBPOSApps)     │    │ (Core)        │
└──────────┘    └──────────────┘    └─────────────────┘    └───────────────┘
                                            │
                                            ▼
                                    ┌─────────────────┐
                                    │ Obtener Client, │
                                    │ Org, POS ID     │
                                    └─────────────────┘
```

#### Respuesta

Devuelve la configuración completa del terminal en formato JSON, incluyendo:
- Información del terminal
- Configuración de impresión
- Métodos de pago disponibles
- Configuración de precios
- Permisos y accesos

---

### 4. `SaveBusinessPartner`

**Archivo:** `SaveBusinessPartner.java`

**Propósito:** Crear o actualizar un Business Partner (cliente) con sus ubicaciones y contactos.

#### Endpoint

```http
POST /ws/com.doceleguas.pos.webservices.SaveBusinessPartner
Content-Type: application/json
```

#### Body del Request

```json
{
    "id": "ABC123...",
    "name": "Nombre del Cliente",
    "contact": {
        "id": "CONTACT_ID",
        "firstName": "Juan",
        "lastName": "Pérez",
        "phone": "+34 600 000 000",
        "email": "juan@example.com"
    },
    "locations": [
        {
            "id": "LOC_ID",
            "locationId": "C_LOCATION_ID",
            "name": "Dirección Principal",
            "adressLine1": "Calle Principal 123",
            "adressLine2": "Piso 2",
            "countryId": "COUNTRY_ID",
            "regionId": "REGION_ID",
            "postalCode": "28001",
            "cityName": "Madrid",
            "isShipTo": true,
            "isBillTo": true
        }
    ],
    "deletedLocationIds": ["LOC_TO_DELETE_1", "LOC_TO_DELETE_2"]
}
```

#### Operaciones Realizadas

1. **`updateCustomerHeader`:** Actualiza el nombre del Business Partner
2. **`updateContactInfo`:** Actualiza datos del contacto (nombre, teléfono, email)
3. **`updateLocations`:** Crea o actualiza ubicaciones
   - Si la ubicación no existe: INSERT en `c_location` y `c_bpartner_location`
   - Si existe: UPDATE en ambas tablas
4. **`deleteLocations`:** Elimina ubicaciones especificadas

#### Gestión de Transacciones

- Usa `Connection.setAutoCommit(false)` para manejar la transacción manualmente
- `COMMIT` al finalizar exitosamente
- `ROLLBACK` en caso de error

#### Respuesta Exitosa

```json
{
    "status": "success"
}
```

#### Respuesta de Error

```json
{
    "error": "Mensaje de error descriptivo"
}
```

---

### 5. `Login`

**Archivo:** `Login.java`

**Propósito:** Autenticar usuarios en el sistema POS y devolver información extendida del usuario.

#### Funcionamiento

1. Extiende `OCPOSLoginHandler` para heredar la lógica base de autenticación
2. Envuelve la respuesta original usando `ResponseBufferWrapper`
3. Añade información adicional al response:

---

### 6. `GetOrders` - Consulta de Órdenes

**Archivos:**
- `GetOrders.java` - WebService wrapper
- `orders/GetOrdersFilter.java` - Filtro HQL extensible
- `orders/GetOrdersFilterProperties.java` - Propiedades HQL

**Propósito:** Consultar órdenes del sistema POS con filtros avanzados y acceso directo a la base de datos.

---

## Arquitectura Implementada (Patrón ProcessHQLQueryValidated)

### Implementación Anterior (Obsoleta - HTTP Proxy)

La implementación anterior actuaba como un proxy HTTP hacia `ExportService/Order`:

```
┌──────────────┐     ┌─────────────┐     ┌──────────────────────────────────┐
│   Cliente    │────▶│  GetOrders  │────▶│ org.openbravo.api.ExportService │
│   (GET)      │     │  WebService │     │          /Order                  │
└──────────────┘     └─────────────┘     └──────────────────────────────────┘
       │                    │                           │
       │    HTTP Request    │    HTTP Request           │
       │    + Auth Header   │    + Auth Header          │
       │                    │───────────────────────────▶
       │                    │                           │
       │    Response        │    Response               │
       │◀───────────────────│◀──────────────────────────│
```

#### Problemas de la Implementación Anterior (Ya Resueltos)

| Problema | Descripción | Impacto |
|----------|-------------|---------|
| **Latencia HTTP** | Cada request genera una llamada HTTP interna al servidor | ~50-200ms adicionales por request |
| **Overhead de red** | Serialización/deserialización HTTP redundante | Mayor uso de CPU |
| **No extensible** | No permite añadir nuevas propiedades sin modificar ExportService | Difícil evolución |
| **No testeable unitariamente** | Depende de conexión HTTP real | Tests de integración obligatorios |
| **Duplicación de autenticación** | Reenvía headers, doble validación | Ineficiente |
| **Sin acceso a hooks** | No puede ejecutar hooks pre/post proceso | Funcionalidad limitada |
| **Timeout fijo** | Timeouts hardcodeados (30s/60s) | Poco flexible |

---

### Implementación Actual: Patrón ProcessHQLQueryValidated

La implementación actual **extiende `ProcessHQLQueryValidated`**, siguiendo el mismo patrón arquitectónico de `PaidReceiptsFilter`.

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
                                                  │ ModelExtension/      │
                                                  │ HQLPropertyList      │
                                                  │ (extensible via CDI) │
                                                  └──────────────────────┘
```

#### Ventajas de la Implementación Actual

| Ventaja | Descripción | Beneficio |
|---------|-------------|-----------|
| **Acceso directo a BD** | Elimina llamada HTTP intermedia | -50-200ms latencia |
| **Extensible via CDI** | Nuevas propiedades via `ModelExtension` | Sin modificar código base |
| **Testeable unitariamente** | Puede mockearse el DAL | Tests rápidos y aislados |
| **Reutiliza infraestructura** | Usa `SimpleQueryBuilder`, `JSONRowConverter` | Código robusto y probado |
| **Soporte de Hooks** | Puede integrar hooks pre/post proceso | Mayor flexibilidad |
| **Paginación nativa** | `_limit`, `_offset` integrados | Consistente con otros endpoints |
| **Filtros remotos** | Usa el mismo mecanismo de `remoteFilters` | API consistente |
| **Ordenamiento flexible** | `orderByClause`, `orderByProperties` | Mayor control |

---

## Estructura de Archivos Implementados

```
com.doceleguas.pos.webservices/
├── GetOrders.java                    # WebService wrapper (implementa WebService)
└── orders/
    ├── GetOrdersFilter.java          # Extiende ProcessHQLQueryValidated
    └── GetOrdersFilterProperties.java # Define HQLProperties extensibles via CDI
```

### Descripción de las Clases Implementadas

#### `GetOrders.java` (WebService Wrapper)

**Ubicación:** `src/com/doceleguas/pos/webservices/GetOrders.java`

Este es el endpoint HTTP que recibe las peticiones REST y las traduce al formato JSON esperado por `GetOrdersFilter`. Mantiene la misma API externa que la implementación anterior.

**Responsabilidades:**
- Validar parámetros HTTP de entrada
- Traducir parámetros HTTP a formato JSON/remoteFilters
- Obtener instancia de `GetOrdersFilter` via CDI
- Manejar paginación (`limit`, `offset`)
- Sanitizar parámetros `orderBy`
- Formatear respuesta JSON

**Métodos principales:**
- `buildJsonRequest()`: Transforma HTTP params → JSON para ProcessHQLQuery
- `buildRemoteFilter()`: Construye filtros en formato remoteFilters
- `sanitizeOrderBy()`: Previene SQL injection en ordenamiento
- `sendErrorResponse()`: Respuestas de error estandarizadas

---

#### `GetOrdersFilter.java` (Core del Filtro)

**Ubicación:** `src/com/doceleguas/pos/webservices/orders/GetOrdersFilter.java`

Extiende `ProcessHQLQueryValidated` siguiendo el patrón de `PaidReceiptsFilter`. Ejecuta queries HQL directamente contra la base de datos.

**Características:**
- Inyección CDI de `ModelExtension` para propiedades extensibles
- Construcción dinámica de HQL con `getQueryValidated()`
- Soporte de filtros remotos via `remoteFilters`
- Validación de filtros obligatorios
- Soporte de parámetros de rango de fechas

**Qualifier CDI:** `GetOrdersFilter_Extension`

---

#### `GetOrdersFilterProperties.java` (Propiedades Extensibles)

**Ubicación:** `src/com/doceleguas/pos/webservices/orders/GetOrdersFilterProperties.java`

Define las propiedades HQL que se incluyen en la respuesta. Otros módulos pueden extender las propiedades sin modificar este código.

**Propiedades incluidas:**

| Categoría | Propiedades JSON |
|-----------|------------------|
| **Core** | id, documentNo, orderDate, creationDate, updated |
| **Amounts** | grossAmount, netAmount |
| **Status** | documentStatus, isCancelled, isLayaway, isSalesTransaction |
| **Organization** | organizationId, organization, organizationSearchKey |
| **Terminal** | terminalId, terminal, terminalName |
| **Customer** | businessPartnerId, businessPartner, businessPartnerName |
| **Document** | documentTypeId, documentType, isReturn, documentSubType |
| **Currency** | currencyId, currency, priceIncludesTax, priceListId, priceList |
| **Warehouse** | warehouseId, warehouse |
| **Sales Rep** | salesRepresentativeId, salesRepresentative |
| **Other** | description, orderReference, externalReference |

---

## Endpoint API GetOrders

#### URL

```http
GET /ws/com.doceleguas.pos.webservices.GetOrders
```

#### Parámetros de Entrada

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `filter` | String | Sí | Tipo de filtro: `byId`, `byDocumentNo`, `byOrgOrderDate`, `byOrgOrderDateRange` |
| `limit` | Integer | No | Límite de resultados (default: sin límite) |
| `offset` | Integer | No | Offset para paginación (default: 0) |
| `orderBy` | String | No | Ordenamiento (default: `ord.creationDate desc`) |

#### Tipos de Filtro (mantiene compatibilidad)

##### 1. `byId` - Filtrar por ID de Orden
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byId&id=ABC123
```

##### 2. `byDocumentNo` - Filtrar por Número de Documento
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byDocumentNo&documentNo=ORD-001
```

##### 3. `byOrgOrderDate` - Filtrar por Organización y Fecha
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDate&organization=STORE1&orderDate=2025-01-15
```

##### 4. `byOrgOrderDateRange` - Filtrar por Rango de Fechas
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDateRange&organization=STORE1&dateFrom=2025-01-01&dateTo=2025-01-31
```

---

## EXTENSIBILIDAD

### Añadir Nuevas Propiedades (sin modificar código base)

Otros módulos pueden extender las propiedades creando una nueva clase:

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
    return properties;
  }
  
  @Override
  public int getPriority() {
    return 100; // Se ejecuta después del default (0)
  }
}
```

---

## COMPARACIÓN: Antes vs Después de Refactorización

| Aspecto | Antes (HTTP Proxy) | Después (ProcessHQLQueryValidated) |
|---------|-------------------|-------------------------------------|
| **Latencia** | +50-200ms (HTTP interno) | Sin overhead HTTP |
| **Extensibilidad** | Ninguna | Via CDI/ModelExtension |
| **Testabilidad** | Solo integración | Unit tests posibles |
| **Mantenimiento** | Dependía de ExportService | Autónomo |
| **Consistencia API** | Diferente a otros endpoints | Igual que MasterData, PaidReceipts |
| **Soporte de Hooks** | No | Sí |
| **Paginación** | Manual via parámetros URL | Nativa (`_limit`, `_offset`) |
| **Ordenamiento** | No soportado | Nativo (`orderByClause`) |

---

## Respuesta Exitosa

```json
{
  "data": [
    {
      "id": "AC2661DED5E1EEA353FD72885A7EA1AC",
      "documentNo": "AUTBE0504P99-0000039",
      "orderDate": "2025-11-17",
      "creationDate": "2025-11-17 18:14:02",
      "grossAmount": 81.11,
      "netAmount": 67.03,
      "documentStatus": "CO",
      "isCancelled": false,
      "isLayaway": false,
      "organizationId": "610BDE28B0AF4D4685FC9B475B635591",
      "organization": "AUTO 5 BIERGES",
      "organizationSearchKey": "0504",
      "terminal": "0504099",
      "terminalId": "3D3E84F1127F4FB78084D8C645791E20",
      "businessPartnerId": "BPID123...",
      "businessPartner": "AUBE1000136",
      "businessPartnerName": "Cliente Ejemplo",
      "documentTypeId": "DTID123...",
      "documentType": "POS Order 0504",
      "isReturn": false,
      "currency": "EUR",
      "priceIncludesTax": true,
      "priceList": "Tarif Ventes AUTO 5 BIERGES 0504",
      "warehouse": "AUTO 5 BIERGES",
      "salesRepresentative": "POS User"
    }
  ],
  "totalRows": 1,
  "queryIndex": 0
}
```

### Respuesta de Error

```json
{
  "error": true,
  "message": "Missing required parameter: 'filter'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange",
  "statusCode": 400
}
```

---

## NOTAS ADICIONALES PARA LA IMPLEMENTACIÓN

### Dependencias Requeridas

El módulo debe declarar dependencia a:
- `org.openbravo.mobile.core` (para `ProcessHQLQuery`, `HQLPropertyList`, `ModelExtension`)
- `org.openbravo.retail.posterminal` (para `ProcessHQLQueryValidated`)

### Registro en provider-config.xml

```xml
<bean>
    <name>GetOrders</name>
    <class>com.doceleguas.pos.webservices.GetOrdersWebService</class>
    <singleton>true</singleton>
</bean>
```

### Filtros de Entidad (EntityFilter)

Para que los filtros funcionen, debe registrarse la entidad en el sistema de filtros de Openbravo Mobile Core:
- Tabla: `OBMOBC_ENTITY_FILTER`
- Entity: `OrderFilter`
- Mapeo de columnas a propiedades HQL

---

## Modelos de Datos (Clases Model)

Todas las clases de modelo extienden la clase abstracta `Model` y deben implementar:

```java
public abstract class Model {
    public abstract NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException;
    public abstract String getName();
    public JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException;
}
```

---

### OCBusinessPartner

**Nombre del Modelo:** `BusinessPartner`

**Características:**
- Consulta clientes (`iscustomer = 'Y'`)
- Incluye ubicaciones como array JSON anidado
- Incluye información de contacto
- Soporta consultas incrementales (`lastUpdated`)
- Paginación con `limit` y `lastId`

**Campos Especiales:**
- `locations`: Array de ubicaciones con direcciones completas
- `contact`: Información del contacto principal

**Tablas Involucradas:**
- `c_bpartner`
- `c_bpartner_location`
- `c_location`
- `ad_user`
- `m_pricelist`
- `c_bp_group`

---

### OCProduct

**Nombre del Modelo:** `Product`

**Características:**
- Filtra por lista de productos del terminal (`obretco_productlist`)
- Filtra por versión de lista de precios
- Soporta consultas incrementales

**Tablas Involucradas:**
- `m_product`
- `m_productprice`
- `obretco_prol_product`
- `m_product_category`
- `c_uom`

---

### OCCountry

**Nombre del Modelo:** `Country`

**Características:**
- Lista países accesibles para el cliente
- Ordenado por nombre
- Soporta paginación con `limit` y `offset`

---

### OCRegion

**Nombre del Modelo:** `Region`

**Características:**
- Lista regiones/provincias
- Filtrado por cliente
- Ordenado por nombre

---

### OCDiscount

**Nombre del Modelo:** `Discount`

**Características:**
- Incluye filtros anidados como arrays JSON:
  - `discountFilterBPCategory`: Categorías de clientes aplicables
  - `discountFilterBPartner`: Clientes específicos
  - `discountFilterProdCategory`: Categorías de productos
  - `discountFilterProducts`: Productos específicos
- Filtra por lista de precios y organización
- Validación de moneda

**Tablas Involucradas:**
- `m_offer`
- `m_offer_pricelist`
- `m_offer_organization`
- `m_offer_product`
- `m_offer_prod_cat`
- `m_offer_bp_group`
- `m_offer_bpartner`

---

### OCTaxCategory

**Nombre del Modelo:** `TaxCategory`

**Características:**
- Excluye categorías BOM (`Asbom='N'`)
- Ordenado por: defecto primero, luego por nombre

---

### OCTaxRate

**Nombre del Modelo:** `TaxRate`

**Características:**
- Filtra por país y región del terminal
- Incluye solo impuestos de ventas (`SOPOType IN ('S', 'B')`)
- Considera zonas fiscales

---

### OCTaxZone

**Nombre del Modelo:** `TaxZone`

**Características:**
- Zonas fiscales aplicables al terminal
- Filtra por país y región de origen
- Ordenado por ID de zona

---

## Sistema de Autenticación

### OCMobileCoreLoginController

**Tipo:** Singleton con JMX MBean

**Propósito:** Controlar el acceso al login de aplicaciones móviles basado en carga del sistema.

**Configuración (Openbravo.properties):**
```properties
mobileappslogin.allow=true       # Permitir logins móviles
mobileappslogin.maxLoad=8.0      # Carga máxima del sistema permitida
```

**Métricas JMX Disponibles:**
- `allowMobileAppsLogin`: Estado de permisos de login
- `maxLoad`: Carga máxima configurada
- `currentLoad`: Carga actual del sistema
- `rejectedLogins`: Contador de logins rechazados

### OCMobileCoreLoginHandler

**Extiende:** `LoginHandler` (Openbravo Core)

**Funcionalidades:**
- Manejo de CORS para peticiones cross-domain
- Autenticación de usuarios
- Gestión de sesiones
- Verificación de licencias
- Asignación de roles y defaults

### OCPOSLoginHandler

**Extiende:** `OCMobileCoreLoginHandler`

**Funcionalidades Específicas POS:**
- Validación de terminal POS por `searchKey`
- Asignación de organización del terminal a la sesión
- Validación de acceso del usuario al terminal
- Gestión de warehouse por defecto

---

## Hooks

### PreOrderLoaderHook

**Archivo:** `hooks/PreOrderLoaderHook.java`

**Implementa:** `OrderLoaderPreProcessHook`

**Propósito:** Procesar pedidos antes de ser guardados.

**Funcionalidad:**
1. Desactiva la generación de factura externa por defecto
2. Si `ocreIssueInvoice = true`:
   - Clona el pedido completo
   - Copia las propiedades de `calculatedInvoiceInfo` al clon
   - Almacena el clon en `calculatedInvoice`

---

## Utilidades

### ResponseBufferWrapper

**Propósito:** Capturar la respuesta HTTP para modificarla antes de enviarla al cliente.

**Uso:** Utilizado en `Login` para interceptar y modificar la respuesta del login handler padre.

```java
ResponseBufferWrapper wrappedRes = new ResponseBufferWrapper(res);
super.doPost(req, wrappedRes);
String capturedContent = wrappedRes.getCapturedContent();
// Modificar capturedContent y escribir respuesta final
```

---

## Consideraciones de Seguridad

### Sanitización de SQL (v2 Mode)

El `MasterDataWebService` incluye sanitización básica del parámetro `selectList`:

```java
String regex = "(?i)\\b(select|update|delete|drop)\\b";
String selectList = parameters.getString("selectList")
    .replaceAll(regex, "")
    .trim()
    .replaceAll(" +", " ");
```

**⚠️ Advertencia:** Esta sanitización es básica. Se recomienda:
- Usar prepared statements siempre que sea posible
- Validar y sanitizar todos los inputs de usuario
- Implementar una whitelist de campos permitidos

### Control de Acceso

- Todas las consultas respetan el `OBContext` del usuario
- Filtrado automático por `readableClients` y `readableOrganizations`
- Validación de terminal para operaciones POS

---

## Configuración del Módulo

### provider-config.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<provider>
    <bean>
        <name>MasterDataService</name>
        <class>com.doceleguas.pos.webservices.MasterDataWebService</class>
        <singleton>true</singleton>
    </bean>
    <bean>
        <name>GetMasterDataModels</name>
        <class>com.doceleguas.pos.webservices.GetMasterDataModelsWebService</class>
        <singleton>true</singleton>
    </bean>
    <bean>
        <name>Terminal</name>
        <class>com.doceleguas.pos.webservices.LoadTerminal</class>
        <singleton>true</singleton>
    </bean>    
    <bean>
        <name>SaveBusinessPartner</name>
        <class>com.doceleguas.pos.webservices.SaveBusinessPartner</class>
        <singleton>true</singleton>
    </bean>  
</provider>
```

---

## Dependencias del Módulo

Este módulo depende de:
- **Openbravo Core** (`org.openbravo.base`, `org.openbravo.dal`)
- **Openbravo Mobile Core** (`org.openbravo.mobile.core`)
- **Openbravo Retail POS Terminal** (`org.openbravo.retail.posterminal`)
- **Hibernate** (para queries nativas)
- **Jettison** (para manejo de JSON)
- **Log4j 2** (para logging)

---

## Ejemplos de Uso

### Consultar Productos

```http
GET /ws/com.doceleguas.pos.webservices.MasterDataService?
    client=CLIENT_ID&
    organization=ORG_ID&
    pos=POS_ID&
    model=Product&
    limit=100&
    selectList=e.m_product_id+as+"id"%2C+e.value+as+"code"%2C+e.name+as+"name"&
    v2=true
```

### Consultar Descuentos

```http
GET /ws/com.doceleguas.pos.webservices.MasterDataService?
    client=CLIENT_ID&
    organization=ORG_ID&
    pos=POS_ID&
    model=Discount&
    limit=50&
    selectList=e.m_offer_id+as+"id"%2C+e.name+as+"name"%2C+e.discount+as+"discount"&
    v2=true
```

### Actualizar Cliente

```http
POST /ws/com.doceleguas.pos.webservices.SaveBusinessPartner
Content-Type: application/json

{
    "id": "BPARTNER_ID",
    "name": "Nuevo Nombre Cliente",
    "contact": {
        "id": "CONTACT_ID",
        "firstName": "María",
        "lastName": "García",
        "phone": "+34 611 222 333",
        "email": "maria@empresa.com"
    },
    "locations": [...],
    "deletedLocationIds": []
}
```

---

## Notas de Implementación

1. **Paginación:** Los modelos soportan `limit` y `offset` o `limit` y `lastId` para paginación eficiente.

2. **Consultas Incrementales:** El parámetro `lastUpdated` permite obtener solo registros modificados después de cierta fecha.

3. **JSON Anidado:** Algunos modelos (BusinessPartner, Discount) utilizan funciones de PostgreSQL (`json_agg`, `json_build_object`) para crear estructuras JSON anidadas directamente en la consulta SQL.

4. **Transacciones:** `SaveBusinessPartner` maneja transacciones explícitamente para garantizar la integridad de los datos.

5. **Control de Carga:** El sistema puede rechazar logins si la carga del servidor excede el umbral configurado.
