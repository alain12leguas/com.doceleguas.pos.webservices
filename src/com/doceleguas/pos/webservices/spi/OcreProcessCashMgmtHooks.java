/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.spi;

import java.math.BigDecimal;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSAppPayment;
import org.openbravo.retail.posterminal.OBPOSPaymentcashupEvents;

/**
 * Runs all process-cash-management hooks (replaces direct {@code ProcessCashMgmtHook} loops).
 */
public interface OcreProcessCashMgmtHooks {

  void runAll(JSONObject jsonBody, String type, OBPOSAppPayment appPayment, OBPOSAppCashup cashup,
      OBPOSPaymentcashupEvents event, BigDecimal amount, BigDecimal origAmount) throws Exception;
}
