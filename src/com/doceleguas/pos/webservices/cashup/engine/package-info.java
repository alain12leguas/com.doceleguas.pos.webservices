/**
 * Cash close / order grouping engine migrated from {@code org.openbravo.retail.posterminal}.
 * <p>
 * <b>OBPOS_* entity types</b> remain in the Web POS module ({@code org.openbravo.retail.posterminal})
 * for DAL. Cashup / invoice hook <b>extension points</b> are expressed as
 * {@code com.doceleguas.pos.webservices.spi} interfaces; the default implementations in
 * {@code com.doceleguas.pos.webservices.retailcompat} delegate to legacy
 * {@code CashupHook}, {@code ProcessCashMgmtHook}, and {@code FinishInvoiceHook} beans so
 * optional bundles (e.g. gift cards) keep working.
 * </p>
 * <p>
 * {@code OrderGroupingProcessorData} is sqlc-generated; source SQL is
 * {@code OrderGroupingProcessor_data.xsql} in this package. Regenerate after query changes.
 * </p>
 */
package com.doceleguas.pos.webservices.cashup.engine;
