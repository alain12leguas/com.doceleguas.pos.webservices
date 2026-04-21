/**
 * Cash close / order grouping engine migrated from {@code org.openbravo.retail.posterminal}.
 * <p>
 * <b>OBPOS_* entity types</b> remain in the Web POS module ({@code org.openbravo.retail.posterminal});
 * this package depends on that module at compile time for DAL entities and for CDI extension
 * interfaces ({@link org.openbravo.retail.posterminal.CashupHook},
 * {@link org.openbravo.retail.posterminal.ProcessCashMgmtHook},
 * {@link org.openbravo.retail.posterminal.FinishInvoiceHook}) so optional hooks in other bundles
 * (e.g. gift cards) continue to resolve.
 * </p>
 * <p>
 * {@link OrderGroupingProcessorData} is sqlc-generated; source SQL is
 * {@code OrderGroupingProcessor_data.xsql} in this package. Regenerate after query changes.
 * </p>
 */
package com.doceleguas.pos.webservices.cashup.engine;
