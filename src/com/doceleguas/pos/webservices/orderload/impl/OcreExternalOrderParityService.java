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
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import org.apache.commons.lang.StringUtils;

/**
 * Parity with legacy {@code org.openbravo.retail.posterminal.ExternalOrderLoader} for SaveOrder
 * payloads that follow the "external" shape (grossAmount, step, per-line {@code obrdmDeliveryMode}
 * / {@code deliveryMode}) without the retail module on the classpath.
 * <p>
 * Ported blocks (retail, approximate line refs): {@code handleOrderSteps} (L632–674),
 * {@code hasLinesNoPickAndCarry} (L681–690), {@code setQuantitiesToDeliver} /
 * {@code setQuantityToDeliver} (L749–773). Gated by order-level
 * {@code ocreExternalOrderParity} (default {@code true}); set to {@code false} to skip.
 */
@ApplicationScoped
public class OcreExternalOrderParityService {

  private static final Logger log = LogManager.getLogger();

  public static final String CREATE = "create";
  public static final String PAY = "pay";
  public static final String SHIP = "ship";
  public static final String CANCEL = "cancel";
  public static final String CANCEL_REPLACE = "cancel_replace";
  public static final String ALL = "all";

  /**
   * Applies line aliases, step handling, and quantity-to-deliver fields before Core persistence.
   */
  public void apply(JSONObject orderJson) throws JSONException {
    if (!orderJson.optBoolean("ocreExternalOrderParity", true)) {
      return;
    }
    if (!orderJson.has("lines") || orderJson.getJSONArray("lines") == null) {
      return;
    }
    JSONArray lines = orderJson.getJSONArray("lines");
    if (lines.length() == 0) {
      return;
    }
    applyDeliveryModeAliases(lines);
    handleOrderSteps(orderJson);
    setQuantitiesToDeliver(orderJson);
  }

  private void applyDeliveryModeAliases(JSONArray lines) throws JSONException {
    for (int i = 0; i < lines.length(); i++) {
      JSONObject line = lines.getJSONObject(i);
      if (!line.has("obrdmDeliveryMode") || isBlankJsonString(line, "obrdmDeliveryMode")) {
        if (line.has("deliveryMode") && !line.isNull("deliveryMode")
            && StringUtils.isNotBlank(String.valueOf(line.get("deliveryMode")))) {
          line.put("obrdmDeliveryMode", String.valueOf(line.get("deliveryMode")).trim());
        }
      }
    }
  }

  private boolean isBlankJsonString(JSONObject o, String key) throws JSONException {
    if (!o.has(key) || o.isNull(key)) {
      return true;
    }
    return StringUtils.isBlank(String.valueOf(o.get(key)));
  }

  private void handleOrderSteps(JSONObject orderJson) throws JSONException {
    final JSONArray linesJson = orderJson.getJSONArray("lines");
    boolean hasLinesNoPickAndCarry = hasLinesNoPickAndCarry(linesJson);
    if (!orderJson.has("step")) {
      orderJson.put("step", ALL);
    }
    final String step = orderJson.getString("step");
    if (CREATE.equals(step) || CANCEL_REPLACE.equals(step)) {
      orderJson.put("payment", -1);
      orderJson.put("isLayaway", false);
    } else if (PAY.equals(step)) {
      orderJson.put("payment", -1);
      orderJson.put("isLayaway", true);
    } else if (SHIP.equals(step)) {
      orderJson.put("payment", orderJson.getDouble("grossAmount"));
      orderJson.put("generateExternalInvoice", true);
      orderJson.put("generateShipment", true);
      orderJson.put("deliver", true);
      orderJson.put("isLayaway", true);
    } else if (CANCEL.equals(step)) {
      if (orderJson.has("cancelLayaway") && orderJson.getBoolean("cancelLayaway")) {
        orderJson.put("paymentWithSign", orderJson.getDouble("grossAmount"));
        orderJson.put("payment", -orderJson.getDouble("grossAmount"));
      }
    } else if (ALL.equals(step)) {
      copyPropertyValue(orderJson, "grossAmount", "payment");
      orderJson.put("generateExternalInvoice", true);
      orderJson.put("generateShipment", true);
      if (!hasLinesNoPickAndCarry) {
        orderJson.put("deliver", true);
      }
    } else {
      log.warn("Step value {} not recognized, order documentNo={}, assuming all", step,
          orderJson.optString("documentNo", "<missing>"));
      copyPropertyValue(orderJson, "grossAmount", "payment");
    }
    boolean isNewLayaway = orderJson.optBoolean("isNewLayaway", false);
    if ("layby".equals(orderJson.optString("type")) && !isNewLayaway) {
      orderJson.put("isLayaway", true);
    }
  }

  private boolean hasLinesNoPickAndCarry(final JSONArray linesJson) throws JSONException {
    for (int j = 0; j < linesJson.length(); j++) {
      final JSONObject line = linesJson.getJSONObject(j);
      if (!"PickAndCarry".equals(line.optString("obrdmDeliveryMode", "PickAndCarry"))) {
        return true;
      }
    }
    return false;
  }

  private void setQuantitiesToDeliver(JSONObject orderJson) throws JSONException {
    final String step = orderJson.getString("step");
    for (int i = 0; i < orderJson.getJSONArray("lines").length(); i++) {
      setQuantityToDeliver(orderJson, orderJson.getJSONArray("lines").getJSONObject(i), step);
    }
  }

  private void setQuantityToDeliver(JSONObject orderJson, JSONObject lineJson, String step)
      throws JSONException {
    if (hasLinesNoPickAndCarry(new JSONArray().put(lineJson))) {
      lineJson.put("obposQtytodeliver", 0);
      return;
    }
    if (CREATE.equals(step) || PAY.equals(step) || CANCEL_REPLACE.equals(step)) {
      if (lineJson.has("deliveredQuantity")) {
        copyPropertyValue(lineJson, "deliveredQuantity", "obposQtytodeliver");
      } else if (!lineJson.has("obposQtytodeliver")) {
        lineJson.put("obposQtytodeliver", 0);
      }
    } else {
      copyPropertyValue(lineJson, "qty", "obposQtytodeliver");
    }
    if (orderJson.optBoolean("completeTicket", false) || orderJson.optBoolean("payOnCredit", false)) {
      lineJson.put("obposIspaid", true);
    }
  }

  private void copyPropertyValue(JSONObject json, String from, String to) throws JSONException {
    if (json.has(to)) {
      return;
    }
    if (!json.has(from)) {
      return;
    }
    json.put(to, json.get(from));
  }
}
