/*
 ************************************************************************************
 * Copyright (C) 2020 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.cashup.engine;

import java.math.BigDecimal;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public interface TicketPropertyMapping {

  default JSONObject getBusinessPartner(JSONObject json) throws JSONException {
    return json.optJSONObject("businessPartner") != null ? json.getJSONObject("businessPartner")
        : json.getJSONObject("bp");
  }

  default JSONObject getTaxes(JSONObject json) throws JSONException {
    return json.optJSONObject("taxes") != null ? json.getJSONObject("taxes")
        : json.getJSONObject("taxLines");
  }

  default BigDecimal getGrossListPrice(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(json.optDouble("grossListPrice", 0));
  }

  default BigDecimal getNetListPrice(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(
        json.has("netListPrice") ? json.getDouble("netListPrice") : json.optDouble("listPrice", 0));
  }

  default BigDecimal getBaseGrossUnitPrice(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(json.has("baseGrossUnitPrice") ? json.getDouble("baseGrossUnitPrice")
        : json.optDouble("price", 0));
  }

  default BigDecimal getBaseNetUnitPrice(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(json.has("baseNetUnitPrice") ? json.getDouble("baseNetUnitPrice")
        : json.optDouble("standardPrice", 0));
  }

  default BigDecimal getGrossUnitPrice(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(json.optDouble("grossUnitPrice", 0));
  }

  default BigDecimal getNetUnitPrice(JSONObject json) throws JSONException {
    if (json.has("netUnitPrice")) {
      return BigDecimal.valueOf(json.getDouble("netUnitPrice"));
    }
    return BigDecimal.valueOf(
        json.has("unitPrice") ? json.getDouble("unitPrice") : json.optDouble("pricenet", 0));
  }

  default BigDecimal getGrossUnitAmount(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(json.has("grossUnitAmount") ? json.getDouble("grossUnitAmount")
        : json.getDouble("lineGrossAmount"));
  }

  default BigDecimal getNetUnitAmount(JSONObject json) throws JSONException {
    return BigDecimal.valueOf(
        json.has("netUnitAmount") ? json.getDouble("netUnitAmount") : json.getDouble("net"));
  }

  default BigDecimal getGrossAmount(JSONObject json) throws JSONException {
    return BigDecimal
        .valueOf(json.has("grossAmount") ? json.getDouble("grossAmount") : json.getDouble("gross"));
  }

  default BigDecimal getNetAmount(JSONObject json) throws JSONException {
    return BigDecimal
        .valueOf(json.has("netAmount") ? json.getDouble("netAmount") : json.getDouble("net"));
  }

  default BigDecimal getUnitPriceUnrounded(JSONObject json) throws JSONException {
    if (json.has("_unitPrice")) {
      return BigDecimal.valueOf(json.getDouble("_unitPrice"));
    }
    return getNetUnitPrice(json);
  }
}
