/*
 *************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 *************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class POSWebSocketService {

  private static final Logger log = LogManager.getLogger();

  public enum NotificationType {
    TERMINAL_UNLINKED("TERMINAL_UNLINKED");

    private final String value;

    NotificationType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static JSONObject createNotification(NotificationType type, String searchKey,
      String message) {
    return createNotification(type, searchKey, message, null);
  }

  public static JSONObject createNotification(NotificationType type, String searchKey,
      String message, JSONObject data) {
    JSONObject notification = new JSONObject();
    try {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
      notification.put("type", type.getValue());
      notification.put("searchKey", searchKey);
      notification.put("message", message);
      notification.put("timestamp", sdf.format(new Timestamp(System.currentTimeMillis())));
      if (data != null) {
        notification.put("data", data);
      }
    } catch (JSONException e) {
      log.error("Error creating notification: {}", e.getMessage(), e);
    }
    return notification;
  }

  public static boolean sendTerminalUnlinkedNotification(String searchKey, String reason) {
    JSONObject notification = createNotification(NotificationType.TERMINAL_UNLINKED, searchKey, "");
    POSWebSocketEndpoint.sendMessageToTerminal(searchKey, notification);
    log.info("Sent TERMINAL_UNLINKED notification to terminal: {}", searchKey);
    return POSWebSocketEndpoint.isTerminalConnected(searchKey);
  }

  public static boolean sendCustomNotification(String searchKey, NotificationType type,
      String message, JSONObject data) {
    JSONObject notification = createNotification(type, searchKey, message, data);
    POSWebSocketEndpoint.sendMessageToTerminal(searchKey, notification);
    log.info("Sent custom notification type {} to terminal: {}", type.getValue(), searchKey);
    return POSWebSocketEndpoint.isTerminalConnected(searchKey);
  }

  public static void broadcastNotification(NotificationType type, String message) {
    JSONObject notification = createNotification(type, "ALL", message);
    POSWebSocketEndpoint.broadcastMessage(notification);
    log.info("Broadcast notification type {} to all terminals", type.getValue());
  }

  public static boolean isTerminalConnected(String searchKey) {
    return POSWebSocketEndpoint.isTerminalConnected(searchKey);
  }

  public static int getConnectedTerminalsCount() {
    return POSWebSocketEndpoint.getConnectedTerminalsCount();
  }
}
