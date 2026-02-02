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

### 6. `GetOrders`

**Archivo:** `GetOrders.java`

**Propósito:** Consultar órdenes del backend utilizando el servicio `org.openbravo.api.ExportService/Order` como proxy.

#### Endpoint

```http
GET /ws/com.doceleguas.pos.webservices.GetOrders
```

#### Parámetros de Entrada

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `filter` | String | Sí | Tipo de filtro a aplicar (ver tipos disponibles abajo) |

#### Tipos de Filtro Disponibles

##### 1. `byId` - Filtrar por ID de Orden

| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `id` | Sí | UUID de la orden |

**Ejemplo:**
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byId&id=068DCCBCB90F80C459DD7BA46E32C16B
```

**Internamente invoca:**
```
/ws/org.openbravo.api.ExportService/Order/068DCCBCB90F80C459DD7BA46E32C16B
```

---

##### 2. `byDocumentNo` - Filtrar por Número de Documento

| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `documentNo` | Sí | Número de documento de la orden |

**Ejemplo:**
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byDocumentNo&documentNo=AUTBE0504P99-0000039
```

**Internamente invoca:**
```
/ws/org.openbravo.api.ExportService/Order/byDocumentNo?documentNo=AUTBE0504P99-0000039
```

---

##### 3. `byOrgOrderDate` - Filtrar por Organización y Fecha

| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `organization` | Sí | Nombre o ID de la organización |
| `orderDate` | Sí | Fecha de la orden (formato: YYYY-MM-DD) |

**Ejemplo:**
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDate&organization=AUTO%205%20BIERGES&orderDate=2025-11-17
```

**Internamente invoca:**
```
/ws/org.openbravo.api.ExportService/Order/byOrgOrderDate?organization=AUTO%205%20BIERGES&orderDate=2025-11-17
```

---

##### 4. `byOrgOrderDateRange` - Filtrar por Organización y Rango de Fechas

| Parámetro | Obligatorio | Descripción |
|-----------|-------------|-------------|
| `organization` | Sí | Nombre o ID de la organización |
| `dateFrom` | Sí | Fecha inicial del rango (formato: YYYY-MM-DD) |
| `dateTo` | Sí | Fecha final del rango (formato: YYYY-MM-DD) |

**Ejemplo:**
```http
GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDateRange&organization=AUTO%205%20BIERGES&dateFrom=2025-11-01&dateTo=2025-11-30
```

**Internamente invoca:**
```
/ws/org.openbravo.api.ExportService/Order/byOrgOrderDateRange?organization=AUTO%205%20BIERGES&dateFrom=2025-11-01&dateTo=2025-11-30
```

---

#### Respuesta Exitosa

La respuesta es un relay directo del `ExportService/Order`:

```json
{
  "data": [
    {
      "id": "AC2661DED5E1EEA353FD72885A7EA1AC",
      "client": "Mobivia",
      "organization": "AUTO 5 BIERGES",
      "organization_info": {
        "name": "AUTO 5 BIERGES",
        "searchKey": "0504",
        "id": "610BDE28B0AF4D4685FC9B475B635591"
      },
      "orderDate": "2025-11-17",
      "terminal": "0504099",
      "documentNo": "AUTBE0504P99-0000039",
      "documentType": "POS Order 0504",
      "priceList": "Tarif Ventes AUTO 5 BIERGES 0504",
      "businessPartner": "AUBE1000136",
      "shipmentAddress": {
        "address": "2 RUE DU PONT NEUF",
        "city": "LILLE",
        "zipCode": "59800",
        "country": "RU",
        "name": "2 RUE DU PONT NEUF ",
        "shippingAddress": true,
        "invoiceAddress": true
      },
      "creationDate": "2025-11-17 18:14:02",
      "warehouse": "AUTO 5 BIERGES",
      "isSale": true,
      "isLayaway": false,
      "isCancelled": false,
      "priceIncludesTax": true,
      "currency": "EUR",
      "grossAmount": 81.11,
      "netAmount": 67.03,
      "lines": [
        {
          "id": "DC744004CB2C13E5944911D0AA4EC804",
          "orderedQuantity": 1,
          "grossAmount": 81.11,
          "netAmount": 67.03,
          "product": "39332",
          "promotions": [...],
          "taxes": [...]
        }
      ],
      "payments": [
        {
          "name": "CASH",
          "currency": "EUR",
          "amount": 81.11,
          "paidAmount": 81.1
        }
      ],
      "taxes": [...],
      "isReturn": false
    }
  ],
  "links": null
}
```

#### Respuesta de Error

```json
{
  "error": true,
  "message": "Missing required parameter: 'filter'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange",
  "statusCode": 400
}
```

#### Diagrama de Flujo

```
┌──────────────┐     ┌─────────────┐     ┌──────────────────────────────────┐
│   Cliente    │────▶│  GetOrders  │────▶│ org.openbravo.api.ExportService │
│   (GET)      │     │  WebService │     │          /Order                  │
└──────────────┘     └─────────────┘     └──────────────────────────────────┘
       │                    │                           │
       │    1. Valida       │                           │
       │       filter       │                           │
       │                    │    2. Construye URL       │
       │                    │       según filter        │
       │                    │                           │
       │                    │    3. Invoca ExportService│
       │                    │───────────────────────────▶
       │                    │                           │
       │                    │    4. Relay response      │
       │◀───────────────────│◀──────────────────────────│
       │                    │                           │
```

#### Características Técnicas

1. **Proxy Pattern:** El endpoint actúa como proxy hacia el `ExportService` de Openbravo API
2. **Autenticación:** Reenvía el header `Authorization` de la petición original
3. **Validación de parámetros:** Valida que los parámetros requeridos estén presentes según el tipo de filtro
4. **URL Encoding:** Los parámetros se codifican correctamente para URLs
5. **Timeout configurado:** 30s para conexión, 60s para lectura
   - Información del usuario
   - Rol por defecto
   - Lista de roles disponibles
   - Idioma por defecto
   - Lista de idiomas disponibles

#### Respuesta Extendida

```json
{
    "showMessage": false,
    "user": {
        "name": "Usuario POS",
        "id": "USER_ID",
        "defaultRole": {
            "id": "ROLE_ID",
            "identifier": "POS Cashier"
        },
        "roles": [
            {"id": "ROLE_1", "identifier": "POS Cashier"},
            {"id": "ROLE_2", "identifier": "POS Manager"}
        ],
        "defaultLanguage": {
            "id": "LANG_ID",
            "identifier": "Español"
        },
        "languages": [
            {"id": "LANG_1", "identifier": "Español"},
            {"id": "LANG_2", "identifier": "English"}
        ]
    }
}
```

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
