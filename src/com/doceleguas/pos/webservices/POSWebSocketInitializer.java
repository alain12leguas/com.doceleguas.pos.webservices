/*
 *************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 *************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class POSWebSocketInitializer implements ServletContextListener {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    log.info("Initializing POS WebSocket endpoints...");

    ServletContext context = sce.getServletContext();
    javax.websocket.server.ServerContainer serverContainer = (javax.websocket.server.ServerContainer) context
        .getAttribute("javax.websocket.server.ServerContainer");

    if (serverContainer == null) {
      log.warn("ServerContainer not available - WebSocket may not be supported");
      return;
    }

    try {
      ServerEndpointConfig config = ServerEndpointConfig.Builder
          .create(POSWebSocketEndpoint.class, "/pos/websocket/notifications")
          .build();
      serverContainer.addEndpoint(config);
      log.info("POS WebSocket endpoint registered successfully at /pos/websocket/notifications");
    } catch (DeploymentException e) {
      log.error("Failed to register POS WebSocket endpoint: {}", e.getMessage(), e);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    log.info("POS WebSocket endpoints shutting down...");
  }
}
