/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.spi;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;

/**
 * Aggregates {@code CashupHook} results during {@code CashCloseProcessor} (post-reconciliation).
 */
public interface OcreCashupCloseHookAggregator {

  String runAll(JSONArray messages, OBPOSApplications posTerminal, OBPOSAppCashup cashUp,
      JSONObject jsonCashup) throws Exception;
}
