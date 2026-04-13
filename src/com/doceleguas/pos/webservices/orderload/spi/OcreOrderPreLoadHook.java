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
 * Runs on each normalized native order in {@code data[]} before Core/DAL persistence.
 */
public interface OcreOrderPreLoadHook {

  /**
   * @param transformedOrder one element of the normalized envelope's {@code data} array
   */
  void afterExternalTransform(JSONObject transformedOrder) throws JSONException;
}
