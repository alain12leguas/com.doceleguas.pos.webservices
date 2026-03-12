/*
 *************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 *************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

@ServerEndpoint("/pos/websocket/notifications")
public class POSWebSocketEndpoint {

  private static final Logger log = LogManager.getLogger();

  private static final Set<Session> sessions = Collections
      .newSetFromMap(new ConcurrentHashMap<>());

  @OnOpen
  public void onOpen(Session session) {
    sessions.add(session);
    log.info("WebSocket client connected: {}", session.getId());
    try {
      JSONObject welcome = new JSONObject();
      welcome.put("type", "CONNECTION_ESTABLISHED");
      welcome.put("message", "Connected to POS notification service");
      welcome.put("sessionId", session.getId());
      session.getBasicRemote().sendText(welcome.toString());
    } catch (IOException | JSONException e) {
      log.error("Error sending welcome message", e);
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) {
    sessions.remove(session);
    log.info("WebSocket client disconnected: {}, reason: {}", session.getId(),
        closeReason.getReasonPhrase());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.error("WebSocket error on session {}: {}", session.getId(), throwable.getMessage(),
        throwable);
    sessions.remove(session);
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    log.info("Received message from {}: {}", session.getId(), message);
    try {
      JSONObject json = new JSONObject(message);
      String action = json.optString("action", "");

      if ("SUBSCRIBE".equals(action)) {
        String terminalId = json.optString("terminalId", "");
        if (!terminalId.isEmpty()) {
          session.getUserProperties().put("terminalId", terminalId);
          JSONObject response = new JSONObject();
          response.put("type", "SUBSCRIBED");
          response.put("terminalId", terminalId);
          response.put("message", "Successfully subscribed to terminal notifications");
          session.getBasicRemote().sendText(response.toString());
          log.info("Session {} subscribed to terminal {}", session.getId(), terminalId);
        }
      } else if ("PING".equals(action)) {
        JSONObject response = new JSONObject();
        response.put("type", "PONG");
        response.put("timestamp", System.currentTimeMillis());
        session.getBasicRemote().sendText(response.toString());
      }
    } catch (IOException | JSONException e) {
      log.error("Error processing message from {}: {}", session.getId(), e.getMessage(), e);
    }
  }

  public static void sendMessageToTerminal(String terminalId, JSONObject message) {
    for (Session session : sessions) {
      String sessionTerminalId = (String) session.getUserProperties().get("terminalId");
      if (terminalId.equals(sessionTerminalId) && session.isOpen()) {
        try {
          session.getBasicRemote().sendText(message.toString());
          log.info("Message sent to terminal {}: {}", terminalId, message.toString());
          return;
        } catch (IOException e) {
          log.error("Error sending message to terminal {}: {}", terminalId, e.getMessage(), e);
        }
      }
    }
    log.warn("Terminal {} not connected or not found", terminalId);
  }

  public static void broadcastMessage(JSONObject message) {
    for (Session session : sessions) {
      if (session.isOpen()) {
        try {
          session.getBasicRemote().sendText(message.toString());
        } catch (IOException e) {
          log.error("Error broadcasting message to {}: {}", session.getId(), e.getMessage(), e);
        }
      }
    }
  }

  public static boolean isTerminalConnected(String terminalId) {
    for (Session session : sessions) {
      String sessionTerminalId = (String) session.getUserProperties().get("terminalId");
      if (terminalId.equals(sessionTerminalId) && session.isOpen()) {
        return true;
      }
    }
    return false;
  }

  public static int getConnectedTerminalsCount() {
    return sessions.size();
  }
}
