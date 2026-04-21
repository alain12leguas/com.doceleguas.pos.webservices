/*
 ************************************************************************************
 * Subset of org.openbravo.retail.posterminal.POSUtils methods required by cashup engine
 * (OrderGroupingProcessor, InvoiceUtils). Entity types remain in org.openbravo.retail.posterminal.
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.cashup.engine;

import org.apache.commons.lang.StringUtils;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.retail.posterminal.OBPOSApplications;

/**
 * Payment / cross-store helpers extracted from {@code POSUtils} for the cashup engine package.
 */
public final class CashupPOSUtils {

  private CashupPOSUtils() {
  }

  public static boolean isPaidStatus(FIN_Payment payment) {
    return FIN_Utility.seqnumberpaymentstatus(payment.getStatus()) >= FIN_Utility
        .seqnumberpaymentstatus(FIN_Utility.invoicePaymentStatus(payment));
  }

  public static boolean isCrossStoreEnabled(final OBPOSApplications posTerminal) {
    final org.openbravo.model.common.enterprise.Organization crossOrganization;
    OBContext.setAdminMode(true);
    try {
      crossOrganization = posTerminal.getOrganization().getOBRETCOCrossStoreOrganization();
    } finally {
      OBContext.restorePreviousMode();
    }
    return crossOrganization != null;
  }

  public static boolean isCrossStore(final Order order, final OBPOSApplications posTerminal) {
    if (isCrossStoreEnabled(posTerminal)) {
      if (!StringUtils.equals(order.getOrganization().getId(),
          order.getObposApplications().getOrganization().getId())) {
        return true;
      }

      if (!StringUtils.equals(order.getOrganization().getId(),
          posTerminal.getOrganization().getId())) {
        return true;
      }
    }

    return false;
  }
}
