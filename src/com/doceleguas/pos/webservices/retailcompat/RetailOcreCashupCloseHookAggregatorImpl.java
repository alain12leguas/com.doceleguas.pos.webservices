/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.retailcompat;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.CashupHook;
import org.openbravo.retail.posterminal.CashupHookResult;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;

import com.doceleguas.pos.webservices.spi.OcreCashupCloseHookAggregator;

@ApplicationScoped
public class RetailOcreCashupCloseHookAggregatorImpl implements OcreCashupCloseHookAggregator {

  @Inject
  @Any
  private Instance<CashupHook> cashupHooks;

  @Override
  public String runAll(JSONArray messages, OBPOSApplications posTerminal, OBPOSAppCashup cashUp,
      JSONObject jsonCashup) throws Exception {
    String next = null;
    for (CashupHook hook : cashupHooks) {
      CashupHookResult result = hook.exec(posTerminal, cashUp, jsonCashup);
      if (result != null) {
        if (result.getMessage() != null && !result.getMessage().equals("")) {
          messages.put(result.getMessage());
        }
        if (next == null && result.getNextAction() != null && !result.getNextAction().equals("")) {
          next = result.getNextAction();
        }
      }
    }
    return next;
  }
}
