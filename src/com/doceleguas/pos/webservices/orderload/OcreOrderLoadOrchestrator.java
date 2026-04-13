/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.json.JsonConstants;

import com.doceleguas.pos.webservices.orderload.impl.CoreOrderPersistenceAdapter;
import com.doceleguas.pos.webservices.orderload.impl.CoreOrderShadowValidator;
import com.doceleguas.pos.webservices.orderload.spi.ExternalEnvelopeTransform;
import com.doceleguas.pos.webservices.orderload.spi.OcreOrderPreLoadHook;

/**
 * OCRE order import pipeline: native inbound normalization + Doceleguas
 * {@link OcreOrderPreLoadHook}s + Core/DAL persistence.
 */
@ApplicationScoped
public class OcreOrderLoadOrchestrator {

  private static final Logger log = LogManager.getLogger();

  @Inject
  private ExternalEnvelopeTransform envelopeTransform;

  @Inject
  private CoreOrderPersistenceAdapter coreOrderPersistence;

  @Inject
  private CoreOrderShadowValidator coreShadowValidator;

  @Inject
  private OrderLoadExecutionModeResolver modeResolver;

  @Inject
  @Any
  private Instance<OcreOrderPreLoadHook> ocrePreHooks;

  public JSONObject importEnvelope(JSONObject messageIn) {
    JSONObject ret = new JSONObject();
    try {
      JSONObject prepared = envelopeTransform.onInboundEnvelope(messageIn);
      applyOcrePreHooks(prepared);

      OrderLoadExecutionMode mode = modeResolver.resolve();
      if (mode == OrderLoadExecutionMode.SHADOW_NATIVE) {
        coreShadowValidator.validate(prepared);
        log.info("[OCWS_Order] shadow-native validation executed for messageId={}",
            prepared.optString("messageId", "<missing>"));
      }

      ret = coreOrderPersistence.persistTransformedEnvelope(prepared);
    } catch (Exception e) {
      try {
        ret.put(JsonConstants.RESPONSE_STATUS, JsonConstants.RPCREQUEST_STATUS_FAILURE);
        ret.put("result", "-1");
        ret.put("message", e.getMessage());
      } catch (JSONException e1) {
        // ignore
      }
    }
    return ret;
  }

  private void applyOcrePreHooks(JSONObject transformedEnvelope) throws JSONException {
    JSONArray data = transformedEnvelope.getJSONArray("data");
    for (int i = 0; i < data.length(); i++) {
      JSONObject order = data.getJSONObject(i);
      for (OcreOrderPreLoadHook hook : ocrePreHooks) {
        hook.afterExternalTransform(order);
      }
    }
  }
}
