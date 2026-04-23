/*
 ************************************************************************************
 * Copyright (C) 2018 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 * You may obtain a copy of the License at http://www.openbravo.com/legal/obcl.html
 * or in the legal folder of this module distribution.
 ************************************************************************************
 */

package com.doceleguas.pos.webservices;

public interface OCMobileCoreLoginControllerMBean {

  boolean getAllowMobileAppsLogin();

  void setAllowMobileAppsLogin(boolean allowMobileAppsLogin);

  double getMaxLoad();

  void setMaxLoad(double maxLoad);

  double getCurrentLoad();

  double getRejectedLogins();

}
