/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

/**
 * Validates transformed payload shape for the future Core persistence adapter.
 * No business persistence side effects.
 */
@ApplicationScoped
public class CoreOrderShadowValidator {

  private static final Logger log = LogManager.getLogger();

  public void validate(JSONObject transformedEnvelope) {
    JSONArray data = transformedEnvelope.optJSONArray("data");
    if (data == null) {
      log.warn("[OCWS_Order][shadow] transformed envelope has no data[]");
      return;
    }

    for (int i = 0; i < data.length(); i++) {
      JSONObject order = data.optJSONObject(i);
      if (order == null) {
        log.warn("[OCWS_Order][shadow] data[{}] is not an object", i);
        continue;
      }
      validateOrder(order, i);
    }
  }

  private void validateOrder(JSONObject order, int index) {
    String orderId = order.optString("id", "<missing>");
    if (!order.has("id")) {
      log.warn("[OCWS_Order][shadow] order[{}] missing id", index);
    }
    if (!order.has("pos") && !order.has("posTerminal")) {
      log.warn("[OCWS_Order][shadow] order {} missing pos/posTerminal", orderId);
    }
    if (order.optJSONArray("lines") == null) {
      log.warn("[OCWS_Order][shadow] order {} missing lines[]", orderId);
    }
    if (order.optJSONArray("payments") == null && !order.optBoolean("isLayaway", false)) {
      log.debug("[OCWS_Order][shadow] order {} has no payments[]", orderId);
    }
  }
}
