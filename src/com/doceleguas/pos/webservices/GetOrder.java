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

import com.doceleguas.pos.webservices.orders.OrderModel;

/**
 * WebService endpoint for retrieving a single Order by ID.
 * 
 * This service provides a REST API for retrieving detailed order data for a specific
 * order identified by its UUID. It is the equivalent of PaidReceipts in the
 * org.openbravo.retail.posterminal module.
 * 
 * @see GetOrdersFilter For querying multiple orders with filters
 * @see OrderModel The native SQL query builder for single order
 */
public class GetOrder implements WebService {

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
      
      log.debug("GetOrder request: {}", jsonParams.toString());
      
      // Set OBContext for security
      OBContext.setOBContext(
          OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonParams.getString("client"),
          jsonParams.getString("organization"));
      
      // Create and execute the query
      OrderModel orderModel = new OrderModel();
      NativeQuery<?> query = orderModel.createQuery(jsonParams);
      
      // Execute query - expecting single result
      @SuppressWarnings("unchecked")
      java.util.List<Object> results = (java.util.List<Object>) query.list();
      
      JSONObject responseJson = new JSONObject();
      
      if (results.isEmpty()) {
        // Order not found
        responseJson.put("success", false);
        responseJson.put("error", true);
        responseJson.put("message", "Order not found");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        // Found order - return first result
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = (Map<String, Object>) results.get(0);
        JSONObject orderData = orderModel.rowToJson(rowMap);
        
        responseJson.put("success", true);
        responseJson.put("data", orderData);
      }
      
      response.getWriter().write(responseJson.toString());
      
      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("GetOrder completed in {}ms", elapsed);
      
    } catch (MissingParameterException e) {
      log.warn("Missing parameter in GetOrder: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetOrder WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
          "Error executing query: " + cause.getMessage());
    }
  }

  /**
   * Builds JSON parameters from HTTP request.
   * 
   * <p>Extracts and validates required parameters for single order retrieval.</p>
   * 
   * @param request The HTTP servlet request
   * @return JSONObject with all parameters ready for OrderModel
   * @throws MissingParameterException if required parameters are missing
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildJsonParams(HttpServletRequest request) 
      throws MissingParameterException, JSONException {
    
    JSONObject jsonParams = new JSONObject();
    
    // Required parameters
    String client = getRequiredParameter(request, "client");
    String organization = getRequiredParameter(request, "organization");
    String selectList = getRequiredParameter(request, "selectList");
    
    jsonParams.put("client", client);
    jsonParams.put("organization", organization);
    jsonParams.put("selectList", selectList);
    
    // Either orderId or documentNo is required
    String orderId = request.getParameter("orderId");
    String documentNo = request.getParameter("documentNo");
    
    if ((orderId == null || orderId.trim().isEmpty()) && 
        (documentNo == null || documentNo.trim().isEmpty())) {
      throw new MissingParameterException(
          "Either 'orderId' or 'documentNo' parameter is required");
    }
    
    if (orderId != null && !orderId.trim().isEmpty()) {
      jsonParams.put("orderId", orderId.trim());
    }
    
    if (documentNo != null && !documentNo.trim().isEmpty()) {
      jsonParams.put("documentNo", documentNo.trim());
    }
    
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

  /**
   * Exception thrown when a required parameter is missing.
   */
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
