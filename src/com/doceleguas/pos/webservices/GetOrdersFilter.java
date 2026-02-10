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
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

import com.doceleguas.pos.webservices.orders.OrdersFilterModel;

/**
 * WebService endpoint for querying Orders using Native SQL queries.
 * 
 * This service provides a REST API for retrieving order data using a flexible,
 * dynamic approach similar to MasterDataWebService. It executes native SQL queries
 * against the C_Order table with dynamic column selection and filtering.
 * 
 * @see OrdersFilterModel The native SQL query builder
 */
public class GetOrdersFilter implements WebService {

  private static final Logger log = LogManager.getLogger();
  
  /** Prefix for filter parameters in the URL */
  private static final String FILTER_PREFIX = "f.";

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    
    long startTime = System.currentTimeMillis();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    
    try {
      // Build JSON parameters from HTTP request
      JSONObject jsonParams = buildJsonParams(request);
      
      log.debug("GetOrdersFilter request: {}", jsonParams.toString());
      
      // Set OBContext for security
      OBContext.setOBContext(
          OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonParams.getString("client"),
          jsonParams.getString("organization"));
      
      // Create and execute the query
      OrdersFilterModel ordersFilterModel = new OrdersFilterModel();
      
      // First, get the total count for pagination (without limit/offset)
      long totalCount = ordersFilterModel.getTotalCount(jsonParams);
      
      // Then execute the paginated query
      NativeQuery<?> query = ordersFilterModel.createQuery(jsonParams);
      
      // Execute with scrollable results for memory efficiency
      ScrollableResults scroll = query.scroll(ScrollMode.FORWARD_ONLY);
      JSONArray dataArray = new JSONArray();
      int rowCount = 0;
      String lastReturnedId = null;
      
      try {
        while (scroll.next()) {
          @SuppressWarnings("unchecked")
          Map<String, Object> rowMap = (Map<String, Object>) scroll.get()[0];
          
          // Extract cursor ID for keyset pagination (always included by OrdersFilterModel)
          Object cursorId = rowMap.remove("__lastid");
          if (cursorId != null) {
            lastReturnedId = cursorId.toString();
          }
          
          JSONObject rowJson = ordersFilterModel.rowToJson(rowMap);
          dataArray.put(rowJson);
          rowCount++;
          
          // Flush session periodically to avoid memory issues
          if (rowCount % 100 == 0) {
            OBDal.getInstance().flush();
            OBDal.getInstance().getSession().clear();
          }
        }
      } finally {
        scroll.close();
      }
      
      // Build response with keyset pagination info
      long limit = jsonParams.optLong("limit", 1000);
      
      JSONObject responseJson = new JSONObject();
      responseJson.put("success", true);
      responseJson.put("data", dataArray);
      
      // Pagination fields for lazy loading support
      responseJson.put("totalRows", totalCount);           // Total matching records (all filters applied)
      responseJson.put("returnedRows", rowCount);          // Rows returned in this response
      responseJson.put("limit", limit);                    // Limit applied
      if (lastReturnedId != null) {
        responseJson.put("lastId", lastReturnedId);        // Cursor: pass as lastId in next request
      }
      responseJson.put("hasMore", rowCount > 0 && rowCount >= limit);  // More pages available
      
      response.getWriter().write(responseJson.toString());
      
      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("GetOrdersFilter completed in {}ms, returned {} rows", elapsed, rowCount);
      
    } catch (MissingParameterException e) {
      log.warn("Missing parameter in GetOrdersFilter: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetOrdersFilter WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
          "Error executing query: " + cause.getMessage());
    }
  }

  /**
   * Builds JSON parameters from HTTP request.
   * 
   * <p>Extracts and validates required parameters, parses filters with f. prefix,
   * and sets defaults for optional parameters.</p>
   * 
   * @param request The HTTP servlet request
   * @return JSONObject with all parameters ready for OrdersFilterModel
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
    
    // Optional pagination parameters (keyset pagination with lastId)
    jsonParams.put("limit", parseLongParam(request, "limit", 1000L));
    String lastId = request.getParameter("lastId");
    if (lastId != null && !lastId.trim().isEmpty()) {
      jsonParams.put("lastId", lastId.trim());
    }
    
    // Optional order by
    String orderBy = request.getParameter("orderBy");
    if (orderBy != null && !orderBy.isEmpty()) {
      jsonParams.put("orderBy", orderBy);
    }
    
    // Parse filters (f.{column}={value})
    JSONArray filters = parseFilters(request);
    jsonParams.put("filters", filters);
    
    return jsonParams;
  }
  
  /**
   * Parses filter parameters from the request.
   * 
   * <p>Filters are identified by the prefix "f." followed by the column name.
   * Example: f.documentno=VBS2 creates a filter for column "documentno" with value "VBS2".</p>
   * 
   * @param request The HTTP servlet request
   * @return JSONArray of filter objects with "column" and "value" properties
   * @throws JSONException if JSON construction fails
   */
  private JSONArray parseFilters(HttpServletRequest request) throws JSONException {
    JSONArray filters = new JSONArray();
    
    Map<String, String[]> params = request.getParameterMap();
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      String key = entry.getKey();
      
      // Check if this is a filter parameter
      if (key.startsWith(FILTER_PREFIX)) {
        String column = key.substring(FILTER_PREFIX.length()).toLowerCase();
        String[] values = entry.getValue();
        
        if (values.length > 0 && values[0] != null && !values[0].trim().isEmpty()) {
          JSONObject filter = new JSONObject();
          filter.put("column", column);
          filter.put("value", values[0].trim());
          filters.put(filter);
        }
      }
    }
    
    return filters;
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
   * Parses a long parameter with a default value.
   * 
   * @param request The HTTP request
   * @param paramName The parameter name
   * @param defaultValue The default value if parameter is missing or invalid
   * @return The parsed long value or the default
   */
  private long parseLongParam(HttpServletRequest request, String paramName, long defaultValue) {
    String value = request.getParameter(paramName);
    if (value != null && !value.isEmpty()) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        log.warn("Invalid {} parameter: {}, using default: {}", paramName, value, defaultValue);
      }
    }
    return defaultValue;
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
