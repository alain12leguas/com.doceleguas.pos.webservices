/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders.loader;

/**
 * Import queue type for OCRE-POS native SaveOrder payloads.
 */
public final class OCOrderImportConstants {

  private OCOrderImportConstants() {
  }

  /** Value of {@code C_IMPORT_ENTRY.TYPEOFDATA} and {@code ImportEntryBuilder} type argument. */
  public static final String TYPE_OF_DATA = "OCOrder";
}
