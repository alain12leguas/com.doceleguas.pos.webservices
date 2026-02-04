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
 * <p>This service provides a REST API for retrieving order data using a flexible,
 * dynamic approach similar to MasterDataWebService. It executes native SQL queries
 * against the C_Order table with dynamic column selection and filtering.</p>
 * 
 * <h2>Architecture</h2>
 * <p>This implementation follows the MasterDataWebService + Model pattern, providing:</p>
 * <ul>
 *   <li>Native SQL queries (no HQL mapping overhead)</li>
 *   <li>Dynamic column selection via selectList parameter</li>
 *   <li>Dynamic filtering via f.{column}={value} parameters</li>
 *   <li>Native pagination with limit and offset</li>
 *   <li>Simplified filter syntax (no operators in URL)</li>
 * </ul>
 * 
 * <h2>Endpoint</h2>
 * <pre>GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrdersFilter</pre>
 * 
 * <h2>Required Parameters</h2>
 * <ul>
 *   <li><b>client</b>: Client UUID</li>
 *   <li><b>organization</b>: Organization UUID</li>
 *   <li><b>selectList</b>: SQL SELECT columns (URL-encoded)</li>
 * </ul>
 * 
 * <h2>Filter Syntax</h2>
 * <p>Filters use the prefix <code>f.</code> followed by the SQL column name:</p>
 * <pre>f.{column}={value}</pre>
 * 
 * <h3>Filter Behavior</h3>
 * <ul>
 *   <li><b>documentno</b>: Uses ILIKE (case-insensitive contains search)</li>
 *   <li><b>All other columns</b>: Uses = (equals)</li>
 * </ul>
 * 
 * <h2>Available Filter Columns</h2>
 * <table border="1">
 *   <tr><th>Filter</th><th>SQL Column</th><th>Description</th></tr>
 *   <tr><td>f.c_order_id</td><td>ord.c_order_id</td><td>Order UUID</td></tr>
 *   <tr><td>f.documentno</td><td>ord.documentno</td><td>Document number (ILIKE)</td></tr>
 *   <tr><td>f.ad_org_id</td><td>ord.ad_org_id</td><td>Organization UUID</td></tr>
 *   <tr><td>f.c_bpartner_id</td><td>ord.c_bpartner_id</td><td>Business Partner UUID</td></tr>
 *   <tr><td>f.dateordered</td><td>ord.dateordered</td><td>Order date (YYYY-MM-DD)</td></tr>
 *   <tr><td>f.datefrom</td><td>ord.dateordered</td><td>Date range start</td></tr>
 *   <tr><td>f.dateto</td><td>ord.dateordered</td><td>Date range end</td></tr>
 *   <tr><td>f.ordertype</td><td>(special)</td><td>Order type: ORD, RET, LAY, etc.</td></tr>
 * </table>
 * 
 * <h2>Optional Parameters</h2>
 * <ul>
 *   <li><b>limit</b>: Maximum number of results (default: 1000)</li>
 *   <li><b>offset</b>: Offset for pagination (default: 0)</li>
 *   <li><b>orderBy</b>: ORDER BY clause (default: ord.created DESC)</li>
 * </ul>
 * 
 * <h2>Example Request</h2>
 * <pre>
 * GET /ws/com.doceleguas.pos.webservices.GetOrdersFilter
 *   ?client=757D621ABD1948F5BCBAD91F19BB70AC
 *   &amp;organization=594C60A9C1154300AEB808C117437D7F
 *   &amp;selectList=ord.c_order_id+as+"id",ord.documentno+as+"documentNo",ord.dateordered+as+"orderDate"
 *   &amp;f.documentno=VBS2
 *   &amp;f.ordertype=ORD
 *   &amp;limit=50
 *   &amp;offset=0
 * </pre>
 * 
 * <h2>Response Format</h2>
 * <pre>
 * {
 *   "success": true,
 *   "data": [
 *     {"id": "ABC123...", "documentNo": "VBS2-0001", "orderDate": "2025-01-15"},
 *     ...
 *   ],
 *   "totalRows": 50
 * }
 * </pre>
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
      NativeQuery<?> query = ordersFilterModel.createQuery(jsonParams);
      
      // Execute with scrollable results for memory efficiency
      ScrollableResults scroll = query.scroll(ScrollMode.FORWARD_ONLY);
      JSONArray dataArray = new JSONArray();
      int rowCount = 0;
      
      try {
        while (scroll.next()) {
          @SuppressWarnings("unchecked")
          Map<String, Object> rowMap = (Map<String, Object>) scroll.get()[0];
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
      
      // Build response
      JSONObject responseJson = new JSONObject();
      responseJson.put("success", true);
      responseJson.put("data", dataArray);
      responseJson.put("totalRows", rowCount);
      
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
    
    // Optional pagination parameters
    jsonParams.put("limit", parseLongParam(request, "limit", 1000L));
    jsonParams.put("offset", parseLongParam(request, "offset", 0L));
    
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
