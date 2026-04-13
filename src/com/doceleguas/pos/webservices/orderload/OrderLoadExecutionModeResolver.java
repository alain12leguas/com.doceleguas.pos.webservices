/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload;

import javax.enterprise.context.ApplicationScoped;

/**
 * Resolves pipeline mode using system property:
 * -Ddoceleguas.ocorder.mode=shadow_native|native
 */
@ApplicationScoped
public class OrderLoadExecutionModeResolver {

  private static final String MODE_PROPERTY = "doceleguas.ocorder.mode";

  public OrderLoadExecutionMode resolve() {
    String raw = System.getProperty(MODE_PROPERTY, "native");
    if ("shadow_native".equalsIgnoreCase(raw) || "shadow".equalsIgnoreCase(raw)) {
      return OrderLoadExecutionMode.SHADOW_NATIVE;
    }
    return OrderLoadExecutionMode.NATIVE;
  }
}
