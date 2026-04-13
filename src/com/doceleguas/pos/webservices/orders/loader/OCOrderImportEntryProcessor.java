/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders.loader;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.base.weld.WeldUtils;
import org.openbravo.service.importprocess.ImportEntry;
import org.openbravo.service.importprocess.ImportEntryManager.ImportEntryQualifier;
import org.openbravo.service.importprocess.ImportEntryProcessor;
import org.openbravo.service.importprocess.ImportEntryProcessor.ImportEntryProcessRunnable;

/**
 * Processes {@value OCOrderImportConstants#TYPE_OF_DATA} import entries via
 * {@link com.doceleguas.pos.webservices.orderload.OcreOrderLoadOrchestrator} using the
 * native SaveOrder pipeline.
 */
@ImportEntryQualifier(entity = OCOrderImportConstants.TYPE_OF_DATA)
@ApplicationScoped
public class OCOrderImportEntryProcessor extends ImportEntryProcessor {

  @Override
  protected ImportEntryProcessRunnable createImportEntryProcessRunnable() {
    return WeldUtils.getInstanceFromStaticBeanManager(OCOrderImportRunnable.class);
  }

  @Override
  protected boolean canHandleImportEntry(ImportEntry importEntryInformation) {
    return OCOrderImportConstants.TYPE_OF_DATA.equals(importEntryInformation.getTypeofdata());
  }

  @Override
  protected String getProcessSelectionKey(ImportEntry importEntry) {
    return importEntry.getOrganization().getId();
  }
}
