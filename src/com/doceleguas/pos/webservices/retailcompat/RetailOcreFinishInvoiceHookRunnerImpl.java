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

import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.retail.posterminal.FinishInvoiceHook;

import com.doceleguas.pos.webservices.spi.OcreFinishInvoiceHookRunner;

@ApplicationScoped
public class RetailOcreFinishInvoiceHookRunnerImpl implements OcreFinishInvoiceHookRunner {

  @Inject
  @Any
  private Instance<FinishInvoiceHook> finishInvoiceHooks;

  @Override
  public void runHooks(Invoice invoice, String cashupId) {
    for (FinishInvoiceHook hook : finishInvoiceHooks) {
      hook.exec(invoice, cashupId);
    }
  }
}
