/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.spi;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * First stage of the import pipeline: map SaveOrder request payload to normalized
 * internal native envelope expected by OCRE Core persistence.
 */
public interface ExternalEnvelopeTransform {

  JSONObject onInboundEnvelope(JSONObject envelope) throws JSONException;
}
