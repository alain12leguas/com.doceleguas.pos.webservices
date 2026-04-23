# Política de dependencias: `org.openbravo.retail` en `com.doceleguas.pos.webservices`

## Objetivo

- **Código de negocio y utilidades** del Web POS clásico (`POSUtils`, `POSConstants`, `POSDefaults`, …) **no** se importan en servicios, modelos ni motor de caja; se usan `com.doceleguas.pos.webservices.internal.terminal.*` y `spi.*`.
- **Extensiones por hooks** CDI del retail se consumen solo vía **SPI** (`com.doceleguas.pos.webservices.spi`) con implementaciones por defecto en **`com.doceleguas.pos.webservices.retailcompat`**, que delegan en `CashupHook` / `ProcessCashMgmtHook` / `FinishInvoiceHook` del módulo `org.openbravo.retail.posterminal` para no romper módulos opcionales.
- **Entidades DAL** (`OBPOS_*`, `Terminal*`, `TerminalAccess`, `OBRETCOProductList` vía resolución en `OcrePosTerminalSupport`, etc.) siguen mapeando tablas del retail-pack; **tipos** permanecen en el classpath de `org.openbravo.retail.*` hasta un posible módulo “solo entidades” (fuera de alcance actual).

## Verificación

Desde el directorio del módulo:

```bash
./scripts/verify-retail-imports.sh
```

El script falla si aparecen imports prohibidos (utilidades/hooks retail) **fuera** de `retailcompat/`.

## Evolución

Para eliminar por completo el JAR de posterminal habría que extraer o duplicar entidades generadas o sustituir acceso DAL; documentar en el plan de producto, no solo en este módulo.
