/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 *
 * Replaces {@code org.openbravo.retail.posterminal.POSDefaults} for mobile profile / login.
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.internal.terminal;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.mobile.core.MobileDefaults;
import org.openbravo.model.ad.access.User;

@ApplicationScoped
@Qualifier(OcrePosConstants.APP_NAME)
public class OcrePosWebDefaults extends MobileDefaults {

  @Override
  public String getFormId() {
    return OcrePosConstants.WEB_POS_FORM_ID;
  }

  @Override
  public String getAppName() {
    return "Openbravo Web POS";
  }

  @Override
  public String getDefaultRoleProperty() {
    return User.PROPERTY_OBPOSDEFAULTPOSROLE;
  }
}
