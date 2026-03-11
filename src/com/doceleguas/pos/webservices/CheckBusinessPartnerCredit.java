/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for checking Business Partner credit availability.
 *
 * <p>Provides a REST GET API that queries real-time credit data for a specific
 * Business Partner identified by its UUID. Used by the POS "Sell on Credit"
 * feature to validate credit availability before completing a credit sale.</p>
 *
 * <h3>Required Parameters:</h3>
 * <ul>
 *   <li>{@code businessPartnerId} — UUID of the Business Partner to check</li>
 * </ul>
 *
 * <h3>Optional Parameters:</h3>
 * <ul>
 *   <li>{@code client} — Client ID for OBContext security (defaults to current session client)</li>
 *   <li>{@code organization} — Organization ID for OBContext security (defaults to current session organization)</li>
 * </ul>
 *
 * <h3>Response Format:</h3>
 * <pre>{@code
 * {
 *   "response": {
 *     "status": 0,
 *     "data": [{
 *       "id": "bp-uuid",
 *       "bpName": "Customer Name",
 *       "creditLimit": 10000.00,
 *       "creditUsed": 3500.00,
 *       "availableCredit": 6500.00
 *     }]
 *   }
 * }
 * }</pre>
 *
 * @see CheckBusinessPartnerCreditModel The native SQL query builder
 */
public class CheckBusinessPartnerCredit implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    long startTime = System.currentTimeMillis();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      // Build JSON parameters from HTTP request
      JSONObject jsonParams = buildJsonParams(request);

      log.debug("CheckBusinessPartnerCredit request: {}", jsonParams.toString());

      // Set OBContext for security
      OBContext.setOBContext(
          OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonParams.getString("client"),
          jsonParams.getString("organization"));

      // Create and execute the query
      CheckBusinessPartnerCreditModel model = new CheckBusinessPartnerCreditModel();
      NativeQuery<?> query = model.createQuery(jsonParams);

      @SuppressWarnings("unchecked")
      java.util.List<Object> results = (java.util.List<Object>) query.list();

      JSONObject responseJson = new JSONObject();

      if (results.isEmpty()) {
        // Business Partner not found
        JSONObject resp = new JSONObject();
        resp.put("status", -1);
        resp.put("data", new JSONArray());
        resp.put("message", "Business Partner not found");
        responseJson.put("response", resp);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        // Found BP — return credit data
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = (Map<String, Object>) results.get(0);
        JSONObject creditData = model.rowToJson(rowMap);

        JSONArray dataArray = new JSONArray();
        dataArray.put(creditData);

        JSONObject resp = new JSONObject();
        resp.put("status", 0);
        resp.put("data", dataArray);
        responseJson.put("response", resp);
      }

      response.getWriter().write(responseJson.toString());

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("CheckBusinessPartnerCredit completed in {}ms", elapsed);

    } catch (MissingParameterException e) {
      log.warn("Missing parameter in CheckBusinessPartnerCredit: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in CheckBusinessPartnerCredit WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error executing query: " + cause.getMessage());
    }
  }

  /**
   * Builds JSON parameters from HTTP request.
   *
   * @param request The HTTP servlet request
   * @return JSONObject with validated parameters
   * @throws MissingParameterException if required parameters are missing
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildJsonParams(HttpServletRequest request)
      throws MissingParameterException, JSONException {

    JSONObject jsonParams = new JSONObject();

    String businessPartnerId = getRequiredParameter(request, "businessPartnerId");

    // client and organization are optional — fall back to the authenticated session context
    String client = getOptionalParameter(request, "client",
        OBContext.getOBContext().getCurrentClient().getId());
    String organization = getOptionalParameter(request, "organization",
        OBContext.getOBContext().getCurrentOrganization().getId());

    jsonParams.put("client", client);
    jsonParams.put("organization", organization);
    jsonParams.put("businessPartnerId", businessPartnerId);

    return jsonParams;
  }

  /**
   * Gets a required parameter from the request.
   *
   * @param request The HTTP request
   * @param paramName The parameter name
   * @return The parameter value (trimmed)
   * @throws MissingParameterException if the parameter is missing or empty
   */
  private String getRequiredParameter(HttpServletRequest request, String paramName)
      throws MissingParameterException {
    String value = request.getParameter(paramName);
    if (value == null || value.trim().isEmpty()) {
      throw new MissingParameterException("Missing required parameter: '" + paramName + "'");
    }
    return value.trim();
  }

  /**
   * Gets an optional parameter from the request, falling back to a default value.
   *
   * @param request The HTTP request
   * @param paramName The parameter name
   * @param defaultValue The fallback value if the parameter is missing or empty
   * @return The parameter value (trimmed) or the default value
   */
  private String getOptionalParameter(HttpServletRequest request, String paramName,
      String defaultValue) {
    String value = request.getParameter(paramName);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    return value.trim();
  }

  /**
   * Sends an error response as JSON.
   *
   * @param response The HTTP response
   * @param statusCode The HTTP status code
   * @param message The error message
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

  // ============================================
  // Exception Classes
  // ============================================

  private static class MissingParameterException extends Exception {
    private static final long serialVersionUID = 1L;

    public MissingParameterException(String message) {
      super(message);
    }
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
}
