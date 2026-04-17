/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Shared helpers to classify and route OCWS_Order flows.
 */
public final class OrderFlowUtils {

  private OrderFlowUtils() {
  }

  public static OrderFlowType classify(JSONObject orderJson) {
    if (orderJson == null) {
      return OrderFlowType.OTHER;
    }
    if (orderJson.optBoolean("isQuotation", false)) {
      return OrderFlowType.QUOTATION;
    }
    if (orderJson.optBoolean("isReturn", false)) {
      return OrderFlowType.RETURN;
    }
    if (orderJson.optBoolean("isLayaway", false) || orderJson.optBoolean("isNewLayaway", false)
        || orderJson.optBoolean("payOnCredit", false)) {
      return OrderFlowType.LAYAWAY;
    }
    String step = resolveStep(orderJson);
    if (!"create".equalsIgnoreCase(step) && !"all".equalsIgnoreCase(step)
        && !"pay".equalsIgnoreCase(step) && !"ship".equalsIgnoreCase(step)
        && !"cancel".equalsIgnoreCase(step) && !"cancel_replace".equalsIgnoreCase(step)) {
      return OrderFlowType.OTHER;
    }
    return OrderFlowType.STANDARD_SALE;
  }

  public static String resolveStep(JSONObject orderJson) {
    if (orderJson == null) {
      return "create";
    }
    String step = orderJson.optString("step", "create");
    return StringUtils.isBlank(step) ? "create" : step;
  }

  public static boolean isReturnFlow(JSONObject orderJson) {
    return classify(orderJson) == OrderFlowType.RETURN;
  }

  public static JSONObject wrapSingleEnvelope(JSONObject preparedEnvelope, JSONObject orderJson)
      throws JSONException {
    JSONObject single = new JSONObject();
    single.put("messageId", preparedEnvelope.optString("messageId", ""));
    single.put("posTerminal", preparedEnvelope.optString("posTerminal", ""));
    single.put("appName", preparedEnvelope.optString("appName", "OCRE"));
    single.put("channel", preparedEnvelope.optString("channel", "Native"));
    JSONArray data = new JSONArray();
    data.put(new JSONObject(orderJson.toString()));
    single.put("data", data);
    if (preparedEnvelope.has("metadata")) {
      single.put("metadata", preparedEnvelope.get("metadata"));
    }
    return single;
  }
}
