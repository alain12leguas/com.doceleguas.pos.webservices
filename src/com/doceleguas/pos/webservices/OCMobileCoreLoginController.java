/*
 ************************************************************************************
 * Copyright (C) 2018 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 * You may obtain a copy of the License at http://www.openbravo.com/legal/obcl.html
 * or in the legal folder of this module distribution.
 ************************************************************************************
 */

package com.doceleguas.pos.webservices;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.jmx.MBeanRegistry;

public class OCMobileCoreLoginController implements OCMobileCoreLoginControllerMBean {

  private static final Logger log = LogManager.getLogger();
  private OperatingSystemMXBean osBean;
  private boolean allowMobileAppsLogin;
  private double maxLoad;
  private AtomicInteger rejectedLogins = new AtomicInteger();

  private static final OCMobileCoreLoginController INSTANCE = new OCMobileCoreLoginController();

  public static OCMobileCoreLoginController getInstance() {
    return INSTANCE;
  }

  private OCMobileCoreLoginController() {
    osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    OBPropertiesProvider obProps = OBPropertiesProvider.getInstance();
    boolean allowProp;
    if (obProps.getOpenbravoProperties().containsKey("mobileappslogin.allow")) {
      allowProp = obProps.getBooleanProperty("mobileappslogin.allow");
    } else {
      allowProp = true;
    }
    setAllowMobileAppsLogin(allowProp);

    double maxLoadProp;
    if (obProps.getOpenbravoProperties().containsKey("mobileappslogin.maxLoad")) {
      try {
        maxLoadProp = Double
            .parseDouble(obProps.getOpenbravoProperties().getProperty("mobileappslogin.maxLoad"));
      } catch (NumberFormatException e) {
        maxLoadProp = 0;
      }
    } else {
      maxLoadProp = 0;
    }
    setMaxLoad(maxLoadProp);

    MBeanRegistry.registerMBean("MobileCoreLoginController", this);
  }

  public boolean shouldAllowLogin(String loginClass) {
    if (!allowMobileAppsLogin) {
      log.warn(loginClass + " login rejected: rejected all");
      rejectedLogins.incrementAndGet();
      return false;
    }
    if (maxLoad <= 0) {
      return true;
    }
    double currentLoad = osBean.getSystemLoadAverage();
    if (currentLoad > maxLoad) {
      rejectedLogins.incrementAndGet();
      log.warn(loginClass + " login rejected: current load {}, max load {}", currentLoad, maxLoad);
      return false;
    }
    return true;
  }

  @Override
  public boolean getAllowMobileAppsLogin() {
    return allowMobileAppsLogin;
  }

  @Override
  public void setAllowMobileAppsLogin(boolean allowMobileAppsLogin) {
    log.info("Setting allow mobile apps logins to {}", allowMobileAppsLogin);
    this.allowMobileAppsLogin = allowMobileAppsLogin;
  }

  @Override
  public double getMaxLoad() {
    return maxLoad;
  }

  @Override
  public void setMaxLoad(double maxLoad) {
    log.info("Setting max allowed load to {}", maxLoad);
    this.maxLoad = maxLoad;
  }

  @Override
  public double getCurrentLoad() {
    return osBean.getSystemLoadAverage();
  }

  @Override
  public double getRejectedLogins() {
    return rejectedLogins.get();
  }

}
