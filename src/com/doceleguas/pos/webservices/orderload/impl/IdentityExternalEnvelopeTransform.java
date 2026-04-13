/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.doceleguas.pos.webservices.orderload.spi.ExternalEnvelopeTransform;

/**
 * Native inbound normalization for OCOrder.
 */
@ApplicationScoped
public class IdentityExternalEnvelopeTransform implements ExternalEnvelopeTransform {

  @Override
  public JSONObject onInboundEnvelope(JSONObject envelope) throws JSONException {
    JSONObject normalized = new JSONObject(envelope.toString());

    if (!normalized.has("messageId")) {
      normalized.put("messageId", UUID.randomUUID().toString().replace("-", ""));
    }

    if (!normalized.has("channel")) {
      normalized.put("channel", "Native");
    }

    if (!normalized.has("appName")) {
      normalized.put("appName", "External");
    }

    if (normalized.has("posTerminal") && !normalized.has("pos")) {
      normalized.put("pos", normalized.optString("posTerminal"));
    }

    if (normalized.has("order") && normalized.opt("order") instanceof JSONObject) {
      JSONArray wrapped = new JSONArray();
      wrapped.put(normalized.getJSONObject("order"));
      normalized.put("data", wrapped);
      normalized.remove("order");
    } else {
      Object data = normalized.opt("data");
      if (data instanceof JSONObject) {
        JSONArray wrapped = new JSONArray();
        wrapped.put(data);
        normalized.put("data", wrapped);
      } else if (!(data instanceof JSONArray)) {
        normalized.put("data", new JSONArray());
      }
    }

    return normalized;
  }
}
