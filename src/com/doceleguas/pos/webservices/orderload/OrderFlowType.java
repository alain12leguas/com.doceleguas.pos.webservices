/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload;

/**
 * High-level flow types identified from OCWS_Order payload.
 */
public enum OrderFlowType {
  STANDARD_SALE,
  RETURN,
  QUOTATION,
  LAYAWAY,
  OTHER
}
