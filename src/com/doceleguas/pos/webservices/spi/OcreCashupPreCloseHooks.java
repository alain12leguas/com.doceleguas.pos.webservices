/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.spi;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;

/**
 * Runs all cashup hooks before close processing (Web POS {@code CashupHook} pre-step).
 */
public interface OcreCashupPreCloseHooks {

  void runAll(OBPOSApplications terminal, OBPOSAppCashup cashup, JSONObject cashUpJson)
      throws Exception;
}
