/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 *
 * Delegates to {@code org.openbravo.retail.posterminal.CashupHook} beans (isolated from core WS).
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.retailcompat;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.CashupHook;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;

import com.doceleguas.pos.webservices.spi.OcreCashupPreCloseHooks;

@ApplicationScoped
public class RetailOcreCashupPreCloseHooksImpl implements OcreCashupPreCloseHooks {

  @Inject
  @Any
  private Instance<CashupHook> cashupHooks;

  @Override
  public void runAll(OBPOSApplications terminal, OBPOSAppCashup cashup, JSONObject cashUpJson)
      throws Exception {
    for (CashupHook hook : cashupHooks) {
      hook.exec(terminal, cashup, cashUpJson);
    }
  }
}
