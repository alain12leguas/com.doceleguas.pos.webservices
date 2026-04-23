/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.spi;

import org.openbravo.model.common.invoice.Invoice;

/**
 * Abstraction for running invoice hooks after document numbering (replaces
 * {@code Instance<FinishInvoiceHook>} in {@code OrderGroupingProcessor}).
 */
public interface OcreFinishInvoiceHookRunner {

  void runHooks(Invoice invoice, String cashupId);
}
