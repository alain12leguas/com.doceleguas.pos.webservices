/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for system health checks and connectivity monitoring.
 * 
 */
public class Ping implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    setCorsHeaders(response);

    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "0");

    response.setStatus(HttpServletResponse.SC_OK);

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"success\":true}");
  }

  private void setCorsHeaders(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
  }

  // ============================================
  // Unsupported HTTP Methods
  // ============================================

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "POST method not supported. Use GET instead.");
  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "DELETE method not supported. Use GET instead.");
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "PUT method not supported. Use GET instead.");
  }

  /**
   * Sends an error response as JSON.
   * 
   * @param response
   *          The HTTP response
   * @param statusCode
   *          The HTTP status code
   * @param message
   *          The error message
   */
  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
    response.setStatus(statusCode);
    try {
      JSONObject errorJson = new JSONObject();
      errorJson.put("success", false);
      errorJson.put("error", true);
      errorJson.put("message", message);
      errorJson.put("statusCode", statusCode);
      response.getWriter().write(errorJson.toString());
    } catch (Exception e) {
      log.error("Error sending error response", e);
      try {
        response.getWriter().write("{\"error\":true,\"message\":\"" + message + "\"}");
      } catch (Exception ex) {
        log.error("Failed to write error response", ex);
      }
    }
  }
}
