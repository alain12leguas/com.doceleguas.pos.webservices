/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders.loader;

import javax.enterprise.context.ApplicationScoped;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.SessionHandler;
import org.openbravo.dal.service.OBDal;
import com.doceleguas.pos.webservices.orderload.OcreOrderLoadOrchestrator;
import org.openbravo.service.importprocess.ImportEntry;
import org.openbravo.service.importprocess.ImportEntryManager;
import org.openbravo.service.importprocess.ImportEntryProcessor.ImportEntryProcessRunnable;
import org.openbravo.service.json.JsonConstants;

/**
 * CDI bean loaded by {@link OCOrderImportEntryProcessor#createImportEntryProcessRunnable()}.
 */
@ApplicationScoped
public class OCOrderImportRunnable extends ImportEntryProcessRunnable {

  private static final Logger log = LogManager.getLogger();

  @Override
  protected void processEntry(ImportEntry importEntry) throws Exception {
    JSONObject envelope = new JSONObject(importEntry.getJsonInfo());
    OcreOrderLoadOrchestrator orchestrator = WeldUtils
        .getInstanceFromStaticBeanManager(OcreOrderLoadOrchestrator.class);
    JSONObject result = orchestrator.importEnvelope(envelope);

    int status = result.optInt(JsonConstants.RESPONSE_STATUS, JsonConstants.RPCREQUEST_STATUS_SUCCESS);
    if (status != JsonConstants.RPCREQUEST_STATUS_SUCCESS) {
      String msg = result.optString("message", result.optString(JsonConstants.RESPONSE_ERRORMESSAGE,
          "Order import failed (status=" + status + ")"));
      log.error("OCOrder import failed for entry {}: {}", importEntry.getId(), result);
      throw new OBException(msg);
    }

    ImportEntry toUpdate = OBDal.getInstance().get(ImportEntry.class, importEntry.getId());
    if (toUpdate != null) {
      toUpdate.setResponseinfo(result.toString());
      OBDal.getInstance().save(toUpdate);
    }

    ImportEntryManager.getInstance().setImportEntryProcessed(importEntry.getId());
    if (SessionHandler.isSessionHandlerPresent()) {
      OBDal.getInstance().commitAndClose();
    }
  }
}
