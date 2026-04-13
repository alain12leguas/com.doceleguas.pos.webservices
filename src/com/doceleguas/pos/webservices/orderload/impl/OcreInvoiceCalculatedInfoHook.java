/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.util.Iterator;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.doceleguas.pos.webservices.orderload.spi.OcreOrderPreLoadHook;

/**
 * Former {@code PreOrderLoaderHook} logic, applied after {@code transformMessage} via
 * {@link com.doceleguas.pos.webservices.orderload.OcreOrderLoadOrchestrator}. Skips when
 * {@code isModified} is true, matching {@code OrderLoader}'s use of
 * {@code OrderLoaderPreProcessHook} vs {@code OrderLoaderModifiedPreProcessHook}.
 */
@ApplicationScoped
public class OcreInvoiceCalculatedInfoHook implements OcreOrderPreLoadHook {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void afterExternalTransform(JSONObject jsonOrder) throws JSONException {
    if (jsonOrder.optBoolean("isModified", false)) {
      return;
    }
    try {
      jsonOrder.put("generateExternalInvoice", false);
      if (jsonOrder.optBoolean("ocreIssueInvoice", false)) {
        JSONObject calculatedInvoice = cloneJSON(jsonOrder);
        JSONObject invoiceInfo = jsonOrder.getJSONObject("calculatedInvoiceInfo");
        copyProperties(invoiceInfo, calculatedInvoice);
        jsonOrder.put("calculatedInvoice", calculatedInvoice);
      }
    } catch (Exception e) {
      log.debug("Error adjusting OCRE invoice fields: {}", e.getMessage());
    }
  }

  private JSONObject cloneJSON(JSONObject original) {
    if (original == null) {
      return null;
    }
    try {
      return new JSONObject(original.toString());
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  @SuppressWarnings("unchecked")
  private void copyProperties(JSONObject origin, JSONObject target) {
    try {
      Iterator<String> keys = origin.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Object value = origin.get(key);
        target.put(key, value);
      }
    } catch (Exception e) {
      log.debug("Error copying JSON properties: {}", e.getMessage());
    }
  }
}
