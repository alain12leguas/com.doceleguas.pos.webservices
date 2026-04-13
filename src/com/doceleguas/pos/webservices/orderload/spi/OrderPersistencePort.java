/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.spi;

import org.codehaus.jettison.json.JSONObject;

/**
 * Persists normalized native order payload after Doceleguas pre-hooks.
 */
public interface OrderPersistencePort {

  JSONObject persistTransformedEnvelope(JSONObject transformedEnvelope) throws Exception;
}
