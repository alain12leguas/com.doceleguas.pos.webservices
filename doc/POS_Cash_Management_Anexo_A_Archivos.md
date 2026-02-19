# Anexo A: Índice Completo de Archivos

> **Referencia para:** [Análisis Principal](POS_Cash_Management_Analysis.md)

---

## 1. Cash Up (Arqueo de Caja)

### 1.1. Java - Lógica de Negocio Backend

| Archivo | Descripción |
|---------|-------------|
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ProcessCashClose.java` | Procesador principal de cierre de caja. Recibe JSON del POS, actualiza cashup, ejecuta reconciliación e invoices |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/CashCloseProcessor.java` | Ejecuta la reconciliación financiera: crea transacciones de diferencia, reconciliaciones, transferencias |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ProcessCashCloseMaster.java` | Gestiona el cierre en terminales Master: lista slaves, verifica estados, acumula pagos compartidos |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ProcessCashCloseSlave.java` | Gestiona el cierre en terminales Slave |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/UpdateCashup.java` | Crea/actualiza registro cashup en BD, con lock FOR UPDATE para evitar duplicados |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/master/Cashup.java` | Servicio REST que el POS consulta para obtener el cashup actual o último procesado |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/CashupHook.java` | Interface de hook para extender el procesamiento de cashup |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/CashupHookResult.java` | Clase resultado del hook de cashup |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/importprocess/CashUpImportEntryProcessor.java` | Procesador de cola asíncrona para entradas de cashup (entity: `OBPOS_App_Cashup`) |

### 1.2. Java - Reportes

| Archivo | Descripción |
|---------|-------------|
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ad_reports/CashUpReport.java` | Generador del reporte de cashup |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ad_reports/CashupReportHook.java` | Hook para extender el reporte de cashup |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ad_reports/CashUpReport.jrxml` | Plantilla JasperReports del reporte principal |
| `modules/org.openbravo.retail.posterminal/src/org/openbravo/retail/posterminal/ad_reports/CashUpSubreport.jrxml` | Sub-reporte de JasperReports |

### 1.3. JavaScript - Nueva Arquitectura (App State)

| Archivo | Descripción |
|---------|-------------|
| `modules/.../app/model/business-object/cashup/Cashup.js` | Definición del modelo Cashup en el State API (campos: id, netSales, grossSales, cashPaymentMethodInfo, etc.) |
| `modules/.../app/model/business-object/cashup/CashupUtils.js` | Utilidades: createNewCashupFromScratch, createNewCashupFromBackend, countTicketInCashup, initializePaymentMethodCashup |
| `modules/.../app/model/business-object/cashup/CashManagementUtils.js` | Utilidades de Cash Management: getCashManagements, getCashManagementsInDraft, getCashManagementsByPaymentMethodId |
| `modules/.../app/model/business-object/cashup/actions/InitCashup.js` | Acción de inicialización: determina origen (local/backend/scratch) y crea cashup |
| `modules/.../app/model/business-object/cashup/actions/CompleteCashupAndCreateNew.js` | Acción de cierre: envía cashup procesado, crea nuevo cashup |
| `modules/.../app/model/business-object/cashup/actions/CreateCashManagement.js` | Acción: agrega un cash management en modo draft al cashup |
| `modules/.../app/model/business-object/cashup/actions/ProcessCashManagements.js` | Acción: procesa cash managements en draft, crea mensajes de sincronización |
| `modules/.../app/model/business-object/cashup/actions/CancelCashManagements.js` | Acción: cancela (elimina) cash managements en draft |

> **Nota:** `modules/...` = `modules/org.openbravo.retail.posterminal/web/org.openbravo.retail.posterminal`

### 1.4. JavaScript - Legacy (Backbone/Enyo)

| Archivo | Descripción |
|---------|-------------|
| `modules/.../js/model/cashup.js` | Modelo Backbone de CashUp |
| `modules/.../js/model/paymentmethodcashup.js` | Modelo Backbone de PaymentMethodCashup |
| `modules/.../js/model/taxcashup.js` | Modelo Backbone de TaxCashup |
| `modules/.../js/utils/cashUpReportUtils.js` | Utilidades para el reporte de cashup |

### 1.5. JavaScript - Vistas del Wizard Cash Up

| Archivo | Descripción |
|---------|-------------|
| `modules/.../js/closecash/view/cashup/cashup.js` | Vista principal del CashUp (extiende CloseCash). Define los 6 pasos y la lógica de navegación |
| `modules/.../js/closecash/view/cashup/cashuppostprintclose.js` | Vista del paso final: impresión y cierre |
| `modules/.../js/closecash/model/cashup/cashup-model.js` | Modelo del wizard CashUp: inicialización de pasos, cálculos de resumen, processAndFinish() |
| `modules/.../js/closecash/model/cashup/cashup-steps.js` | Definición de pasos: StepPendingOrders, Master |
| `modules/.../js/closecash/components/cashup/cashup-modals.js` | Modales del cashup (confirmaciones, errores) |
| `modules/.../js/closecash/components/cashup/cashup-popups.js` | Popups del cashup |

### 1.6. JavaScript - Vistas Close Cash (compartidas)

| Archivo | Descripción |
|---------|-------------|
| `modules/.../js/closecash/view/closecash.js` | Vista framework del cierre: toolbar, navegación de pasos, botones prev/next |
| `modules/.../js/closecash/model/closecash-model.js` | Modelo base CloseCash: control de pasos, setPaymentList, navigación |
| `modules/.../js/closecash/model/closecash-print.js` | Lógica de impresión del cierre de caja |
| `modules/.../js/closecash/model/closecash-steps.js` | Definición de pasos compartidos: CashPayments, PaymentMethods, CashToKeep, PostPrintAndClose |
| `modules/.../js/closecash/view/tabcountcash.js` | Vista del conteo de efectivo: renderiza líneas de payment methods con expected/counted |
| `modules/.../js/closecash/view/tabcashpayments.js` | Vista del conteo por denominación (monedas/billetes) |
| `modules/.../js/closecash/view/tabcashtokeep.js` | Vista "Cash to Keep": radio buttons para seleccionar cuánto mantener |

### 1.7. Impresión

| Archivo | Descripción |
|---------|-------------|
| `modules/.../app/external-device/actions/PrintCashup.js` | Acción de impresión del cashup (nueva arquitectura) |
| `modules/.../app/external-device/printing/CashupPrinter.js` | Printer del cashup para dispositivos externos |
| `modules/.../res/printcashup.xml` | Template XML de impresión del cashup |

---

## 2. Cash Management (Gestión de Efectivo)

### 2.1. Java - Backend

| Archivo | Descripción |
|---------|-------------|
| `modules/.../src/org/openbravo/retail/posterminal/ProcessCashMgmt.java` | Procesador principal: crea FIN_FinaccTransaction, actualiza balance, guarda eventos |
| `modules/.../src/org/openbravo/retail/posterminal/ProcessCashMgmtHook.java` | Interface de hook para extender Cash Management |
| `modules/.../src/org/openbravo/retail/posterminal/ProcessCashMgmtMaster.java` | Procesador para terminal Master |
| `modules/.../src/org/openbravo/retail/posterminal/ExtendsCashManagementPaymentTypeHook.java` | Hook para extender los tipos de pago en Cash Mgmt |
| `modules/.../src/org/openbravo/retail/posterminal/importprocess/CashManagementImportEntryProcessor.java` | Procesador de cola asíncrona (entity: `FIN_Finacc_Transaction`) |
| `modules/.../src/org/openbravo/retail/posterminal/term/CashMgmtDepositEvents.java` | Eventos de depósito del terminal |
| `modules/.../src/org/openbravo/retail/posterminal/term/CashMgmtDropEvents.java` | Eventos de retiro del terminal |

### 2.2. JavaScript - Legacy Views

| Archivo | Descripción |
|---------|-------------|
| `modules/.../js/cashmgmt/view/cashmgmt.js` | Vista principal: toolbar con Cancel/Done/Toggle |
| `modules/.../js/cashmgmt/view/cashmgmtinfo.js` | Panel de información del cash management |
| `modules/.../js/cashmgmt/view/cashmgmtkeyboard.js` | Teclado numérico para ingreso de montos |
| `modules/.../js/cashmgmt/model/cashmgmt-model.js` | Modelo: paymentDone, makeDeposits, cancelDeposits |
| `modules/.../js/cashmgmt/model/cashmgmt-print.js` | Impresión de recibos de Cash Management |
| `modules/.../js/cashmgmt/components/cashmgmt-modals.js` | Modales de Cash Management |
| `modules/.../js/model/cashmanagement.js` | Modelo Backbone de CashManagement |
| `modules/.../js/utils/cashManagementUtils.js` | Utilidades legacy de Cash Management |
| `modules/.../res/printcashmgmt.xml` | Template XML de impresión |

---

## 3. Safe Box (Caja Fuerte)

### 3.1. Java

| Archivo | Descripción |
|---------|-------------|
| `modules/.../src/org/openbravo/retail/posterminal/SafeBoxes.java` | Servicio de Safe Boxes |
| `modules/.../src/org/openbravo/retail/posterminal/SafeBoxesProperties.java` | Propiedades de Safe Box |
| `modules/.../src/org/openbravo/retail/posterminal/CountSafeBoxProcessor.java` | Procesador de conteo de safe box |
| `modules/.../src/org/openbravo/retail/posterminal/CountSafeboxHook.java` | Hook para conteo de safe box |
| `modules/.../src/org/openbravo/retail/posterminal/ProcessCountSafeBox.java` | Proceso de conteo |
| `modules/.../src/org/openbravo/retail/posterminal/importprocess/CountSafeBoxImportEntryProcessor.java` | Procesador de cola asíncrona |

### 3.2. JavaScript

| Archivo | Descripción |
|---------|-------------|
| `modules/.../app/model/business-object/safebox/SynchronizeCountSafeBox.js` | Sincronización de conteo de safe box |
| `modules/.../js/closecash/view/countsafebox/countsafebox.js` | Vista de conteo de safe box |
| `modules/.../js/closecash/model/countsafebox/countsafebox-model.js` | Modelo de conteo de safe box |
| `modules/.../js/closecash/model/countsafebox/countsafebox-steps.js` | Pasos del wizard de safe box |
| `modules/.../js/components/modalsafebox.js` | Modal de selección de safe box |

---

## 4. Entidades Generadas (src-gen/)

| Archivo | Entidad |
|---------|---------|
| `src-gen/org/openbravo/retail/posterminal/OBPOSAppCashup.java` | OBPOS_APP_CASHUP |
| `src-gen/org/openbravo/retail/posterminal/OBPOSPaymentMethodCashup.java` | OBPOS_PAYMENTMETHODCASHUP |
| `src-gen/org/openbravo/retail/posterminal/OBPOSTaxCashup.java` | OBPOS_TAXCASHUP |
| `src-gen/org/openbravo/retail/posterminal/OBPOSCashupApproval.java` | OBPOS_CASHUP_APPROVAL |
| `src-gen/org/openbravo/retail/posterminal/OBPOSPaymentcashupEvents.java` | OBPOS_PAYMENTCASHUP_EVENTS |
| `src-gen/org/openbravo/retail/posterminal/OBPOS_PaymentMethodCashCountPerAmount.java` | OBPOS_PMCASHUP_AMNTCNT |
| `src-gen/org/openbravo/retail/posterminal/OBPOSAppCashReconcil.java` | OBPOS_APP_CASH_RECONCIL |
| `src-gen/org/openbravo/retail/posterminal/OBPOSSafeBox.java` | OBPOS_SAFEBOX |
| `src-gen/org/openbravo/retail/posterminal/OBPOSSafeBoxPaymentMethod.java` | OBPOS_SAFEBOX_PAYMENTMETHOD |
| `src-gen/org/openbravo/retail/posterminal/OBPOS_SafeboxCount.java` | OBPOS_SAFEBOX_COUNT |
| `src-gen/org/openbravo/retail/posterminal/OBPOS_SafeboxCountPaymentMethod.java` | OBPOS_SAFEBOX_COUNT_PM |

---

## 5. Definiciones de Tablas (Database Model)

| Archivo | Tabla |
|---------|-------|
| `modules/.../src-db/database/model/tables/OBPOS_APP_CASHUP.xml` | Registro principal de cashup |
| `modules/.../src-db/database/model/tables/OBPOS_PAYMENTMETHODCASHUP.xml` | Método de pago por cashup |
| `modules/.../src-db/database/model/tables/OBPOS_TAXCASHUP.xml` | Impuestos por cashup |
| `modules/.../src-db/database/model/tables/OBPOS_CASHUP_APPROVAL.xml` | Aprobaciones de cashup |
| `modules/.../src-db/database/model/tables/OBPOS_PMCASHUP_AMNTCNT.xml` | Conteo por denominación |
| `modules/.../src-db/database/model/tables/OBPOS_PAYMENTCASHUP_EVENTS.xml` | Eventos de Cash Mgmt por cashup |
| `modules/.../src-db/database/model/tables/OBPOS_APP_CASH_RECONCIL.xml` | Reconciliación por cashup |
| `modules/.../src-db/database/model/tables/OBPOS_SAFEBOX.xml` | Safe Box |
| `modules/.../src-db/database/model/tables/OBPOS_SAFEBOX_COUNT.xml` | Conteo de Safe Box |
| `modules/.../src-db/database/model/tables/OBPOS_SAFEBOX_COUNT_PM.xml` | Conteo por método de pago |
| `modules/.../src-db/database/model/tables/OBPOS_SAFEBOX_PAYMENTMETHOD.xml` | Métodos de pago de Safe Box |
| `modules/.../src-db/database/model/tables/OBPOS_SAFEBOX_TOUCHPOINT.xml` | Touchpoints de Safe Box |

---

## 6. Tests

### 6.1. Web Tests (Jest)

| Archivo | Cobertura |
|---------|-----------|
| `modules/.../web-test/model/business-object/cashup/Cashup.test.js` | Test general del modelo Cashup |
| `modules/.../web-test/.../Cashup-cancelCashManagements-StateAction.test.js` | Test cancelar Cash Managements |
| `modules/.../web-test/.../Cashup-completeCashupAndCreateNew.test.js` | Test completar cashup y crear nuevo |
| `modules/.../web-test/.../Cashup-createCashManagement-StateAction.test.js` | Test crear Cash Management |
| `modules/.../web-test/.../Cashup-InitCashup-ActionPreparation.test.js` | Test preparación de Init Cashup |
| `modules/.../web-test/.../Cashup-initCashup-StateAction-fromBackend.test.js` | Test init desde backend |
| `modules/.../web-test/.../Cashup-initCashup-StateAction-fromLocal.test.js` | Test init desde local |
| `modules/.../web-test/.../Cashup-initCashup-StateAction-fromScratch.test.js` | Test init desde scratch |
| `modules/.../web-test/.../Cashup-processCashManagements-StateAction.test.js` | Test procesar Cash Managements |
| `modules/.../web-test/.../Cashup-updateCashupFunction-afterTicketDone.test.js` | Test actualización post-ticket |

### 6.2. Java Tests

| Archivo | Cobertura |
|---------|-----------|
| `modules/.../src-test/org/openbravo/retail/posterminal/POSOrderCashupPerformanceTest.java` | Test de rendimiento |
| `modules/.../src-test/org/openbravo/retail/posterminal/cashup.json` | Datos de prueba JSON |

---

## 7. Extensiones y Personalizaciones

### 7.1. Gift Cards (extensión de Cash Management)

| Archivo | Descripción |
|---------|-------------|
| `modules/org.openbravo.retail.giftcards/src/.../ExtendsCashManagementPaymentTypeHookImplementation.java` | Extiende tipos de pago para gift cards |
| `modules/org.openbravo.retail.giftcards/src/.../hooks/ProcessCashMgmtHookGiftCard.java` | Hook de Cash Mgmt para gift cards |
| `modules/org.openbravo.retail.giftcards/src/.../master/CashMgmtEvents.java` | Eventos de Cash Mgmt para gift cards |
| `modules/org.openbravo.retail.giftcards/web/.../app/.../cashup/actions/CashManagementActions.js` | Acciones de Cash Mgmt para gift cards |
| `modules/org.openbravo.retail.giftcards/web/.../js/hooks/AddButtonToCashManagementHook.js` | Hook UI: agrega botones |
| `modules/org.openbravo.retail.giftcards/web/.../js/hooks/AddPaymentToCashManagementHook.js` | Hook UI: agrega métodos de pago |

### 7.2. Halsteds WebPOS Customization

| Archivo | Descripción |
|---------|-------------|
| `modules/com.doceleguas.halsteds.webpos.customization/src/.../WebPosCustomizationComponentProvider.java` | Registra componentes personalizados |
| `modules/com.doceleguas.halsteds.webpos.customization/web/.../js/component/send_ecocash_change_button.js` | Botón de cambio EcoCash |
| `modules/com.doceleguas.halsteds.webpos.customization/web/.../js/model/ecocash_model_controller.js` | Controlador de modelo EcoCash |
| `modules/com.doceleguas.halsteds.webpos.customization/web/.../js/hook/ecocash_hook.js` | Hook de integración EcoCash |

### 7.3. SMF Change Funds

| Archivo | Descripción |
|---------|-------------|
| `modules/com.smf.pos.change.funds/web/.../js/components/cashupUtilities.js` | Utilidades de cashup para cambio |
| `modules/com.smf.pos.change.funds/web/.../js/components/addChange.js` | Lógica de cambio al cliente |
| `modules/com.smf.pos.change.funds/src/.../hooks/AddFinancialAccountHook.java` | Hook de cuenta financiera |
