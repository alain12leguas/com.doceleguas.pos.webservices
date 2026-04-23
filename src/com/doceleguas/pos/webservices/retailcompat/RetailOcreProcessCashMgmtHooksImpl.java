/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.retailcompat;

import java.math.BigDecimal;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSAppPayment;
import org.openbravo.retail.posterminal.OBPOSPaymentcashupEvents;
import org.openbravo.retail.posterminal.ProcessCashMgmtHook;

import com.doceleguas.pos.webservices.spi.OcreProcessCashMgmtHooks;

@ApplicationScoped
public class RetailOcreProcessCashMgmtHooksImpl implements OcreProcessCashMgmtHooks {

  @Inject
  @Any
  private Instance<ProcessCashMgmtHook> retailHooks;

  @Override
  public void runAll(JSONObject jsonBody, String type, OBPOSAppPayment appPayment,
      OBPOSAppCashup cashup, OBPOSPaymentcashupEvents event, BigDecimal amount, BigDecimal origAmount)
      throws Exception {
    for (ProcessCashMgmtHook hook : retailHooks) {
      hook.exec(jsonBody, type, appPayment, cashup, event, amount, origAmount);
    }
  }
}
