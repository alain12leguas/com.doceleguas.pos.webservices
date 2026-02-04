/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.web.WebService;

import com.doceleguas.pos.webservices.orders.GetOrdersFilter;
import com.doceleguas.pos.webservices.orders.GetOrdersFilterProperties;

/**
 * WebService endpoint for querying Orders with dynamic filter support.
 * 
 * This service provides a REST API for retrieving order data using a flexible,
 * dynamic filter system similar to PaidReceiptsFilter. It acts as a wrapper that
 * translates HTTP GET parameters into the JSON format expected by {@link GetOrdersFilter},
 * which executes the actual HQL query against the database.
 * 
 * <h2>Architecture</h2>
 * This implementation follows the ProcessHQLQueryValidated pattern used by
 * PaidReceiptsFilter in the retail.posterminal module, providing:
 * <ul>
 *   <li>Direct DAL access (no HTTP proxy overhead)</li>
 *   <li>Extensible properties via CDI/ModelExtension</li>
 *   <li>Native pagination with _limit and _offset</li>
 *   <li>Dynamic filters with operator support</li>
 *   <li>Consistent API with other mobile endpoints</li>
 * </ul>
 * 
 * <h2>Endpoint</h2>
 * <pre>GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders</pre>
 * 
 * <h2>Filter Syntax</h2>
 * Dynamic filters use the following parameter patterns:
 * <ul>
 *   <li><b>{property}={value}</b>: Filter value for a property</li>
 *   <li><b>{property}_op={operator}</b>: Operator to use (optional, defaults to "contains")</li>
 *   <li><b>{property}_isId=true</b>: Indicates the value is a UUID (uses "equals" operator)</li>
 * </ul>
 * 
 * <h2>Valid Operators</h2>
 * <ul>
 *   <li>equals, notEquals, greaterThan, lessThan, startsWith, contains</li>
 * </ul>
 * 
 * <h2>Supported Filter Properties</h2>
 * <ul>
 *   <li><b>id</b>: Order UUID (use with _isId=true)</li>
 *   <li><b>documentNo</b>: Document number</li>
 *   <li><b>organization</b>: Organization UUID (use with _isId=true)</li>
 *   <li><b>orgSearchKey</b>: Organization search key (text)</li>
 *   <li><b>orgName</b>: Organization name (text)</li>
 *   <li><b>businessPartner</b>: Business partner UUID (use with _isId=true)</li>
 *   <li><b>orderType</b>: Order type (ORD, RET, LAY, QT, verifiedReturns, payOpenTickets)</li>
 *   <li><b>totalamount</b>: Order total amount</li>
 * </ul>
 * 
 * <h2>Date Filters (special handling)</h2>
 * Date filters are passed as parameters and handled in the HQL WHERE clause:
 * <ul>
 *   <li><b>orderDate</b>: Exact date (format: YYYY-MM-DD)</li>
 *   <li><b>dateFrom &amp; dateTo</b>: Date range (format: YYYY-MM-DD)</li>
 * </ul>
 * 
 * <h2>Reserved Parameters</h2>
 * <ul>
 *   <li><b>_limit</b>: Maximum number of results (default: no limit)</li>
 *   <li><b>_offset</b>: Offset for pagination (default: 0)</li>
 *   <li><b>orderBy</b>: Order by clause (default: "ord.creationDate desc")</li>
 * </ul>
 * 
 * <h2>Example Requests</h2>
 * <pre>
 * # Filter by document number (contains search, default operator)
 * GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2
 * 
 * # Filter by exact document number
 * GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders?documentNo=VBS2-001&amp;documentNo_op=equals
 * 
 * # Filter by order ID (UUID)
 * GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders?id=ABC-123&amp;id_isId=true
 * 
 * # Filter by organization search key and date range
 * GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders?orgSearchKey=E101&amp;dateFrom=2025-01-01&amp;dateTo=2025-01-31
 * 
 * # Combine multiple filters with pagination
 * GET /openbravo/ws/com.doceleguas.pos.webservices.GetOrders?orderType=ORD&amp;orgSearchKey=E101&amp;_limit=50&amp;_offset=0
 * </pre>
 * 
 * @see GetOrdersFilter The HQL query executor
 * @see GetOrdersFilterProperties The response properties definition
 */
public class GetOrders implements WebService {

  private static final Logger log = LogManager.getLogger();

  // Supported filter properties that map to HQL properties in GetOrdersFilterProperties
  private static final Set<String> FILTER_PROPERTIES = new HashSet<>(Arrays.asList(
      "id", "documentNo", "organization", "orgSearchKey", "orgName",
      "businessPartner", "orderType", "totalamount"
  ));

  // Date filter properties (handled specially via parameters, not remoteFilters)
  private static final Set<String> DATE_PROPERTIES = new HashSet<>(Arrays.asList(
      "orderDate", "dateFrom", "dateTo"
  ));

  // Reserved parameters (not filter properties)
  private static final Set<String> RESERVED_PARAMS = new HashSet<>(Arrays.asList(
      "_limit", "_offset", "orderBy"
  ));

  // Valid operators in SimpleQueryBuilder
  private static final Set<String> VALID_OPERATORS = new HashSet<>(Arrays.asList(
      "equals", "notEquals", "greaterThan", "lessThan", "startsWith", "contains"
  ));

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    long startTime = System.currentTimeMillis();

    try {
      // Build JSON request from HTTP parameters
      JSONObject jsonsent = buildJsonRequest(request);

      log.debug("GetOrders request: {}", jsonsent.toString());

      // Get the filter instance via CDI
      GetOrdersFilter filter = CDI.current().select(GetOrdersFilter.class).get();

      // Execute the filter
      StringWriter writer = new StringWriter();
      filter.exec(writer, jsonsent);

      // Build the response wrapper
      String filterOutput = writer.toString();
      if (filterOutput != null && !filterOutput.isEmpty()) {
        // The ProcessHQLQuery writes JSON fragments, we wrap them properly
        if (filterOutput.startsWith("\"data\":")) {
          // Already has data wrapper from ProcessHQLQuery
          response.getWriter().write("{" + filterOutput + "}");
        } else {
          // Wrap in standard response format
          response.getWriter().write("{\"success\":true," + filterOutput + "}");
        }
      } else {
        // Empty result
        JSONObject emptyResponse = new JSONObject();
        emptyResponse.put("data", new JSONArray());
        emptyResponse.put("success", true);
        emptyResponse.put("totalRows", 0);
        response.getWriter().write(emptyResponse.toString());
      }

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("GetOrders completed in {}ms", elapsed);

    } catch (MissingParameterException e) {
      log.warn("Missing parameter in GetOrders: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (InvalidFilterException e) {
      log.warn("Invalid filter in GetOrders: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      log.error("Error in GetOrders WebService", e);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error: " + e.getMessage());
    }
  }

  /**
   * Transforms HTTP request parameters into the JSON format expected by GetOrdersFilter.
   * 
   * This method handles the translation between the external REST API format and the
   * internal ProcessHQLQuery JSON format, including:
   * <ul>
   *   <li>Client and organization context from OBContext</li>
   *   <li>Dynamic filter parsing from request parameters</li>
   *   <li>Pagination parameters (_limit, _offset)</li>
   *   <li>Order by clause</li>
   * </ul>
   * 
   * <h3>Parameter Patterns</h3>
   * <ul>
   *   <li>{property}={value}: Filter value for a property</li>
   *   <li>{property}_op={operator}: Operator (defaults to "contains" or "equals" for IDs)</li>
   *   <li>{property}_isId=true: Indicates the value is a UUID</li>
   * </ul>
   * 
   * @param request The HTTP servlet request
   * @return JSONObject in the format expected by ProcessHQLQuery
   * @throws MissingParameterException if no filter parameters are provided
   * @throws InvalidFilterException if an invalid operator is specified
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildJsonRequest(HttpServletRequest request)
      throws MissingParameterException, InvalidFilterException, JSONException {

    JSONObject json = new JSONObject();
    JSONArray remoteFilters = new JSONArray();
    JSONObject parameters = new JSONObject();

    // Set client and organization from OBContext
    OBContext ctx = OBContext.getOBContext();
    json.put("client", ctx.getCurrentClient().getId());
    json.put("organization", ctx.getCurrentOrganization().getId());

    // Handle pagination - support both old (limit/offset) and new (_limit/_offset) naming
    String limit = request.getParameter("_limit");
    if (limit == null) {
      limit = request.getParameter("limit");
    }
    if (limit != null && !limit.isEmpty()) {
      try {
        json.put("_limit", Integer.parseInt(limit));
      } catch (NumberFormatException e) {
        log.warn("Invalid limit parameter: {}", limit);
      }
    }

    String offset = request.getParameter("_offset");
    if (offset == null) {
      offset = request.getParameter("offset");
    }
    if (offset != null && !offset.isEmpty()) {
      try {
        json.put("_offset", Integer.parseInt(offset));
      } catch (NumberFormatException e) {
        log.warn("Invalid offset parameter: {}", offset);
      }
    }

    // Handle order by
    String orderBy = request.getParameter("orderBy");
    if (orderBy != null && !orderBy.isEmpty()) {
      String sanitized = sanitizeOrderBy(orderBy);
      if (sanitized != null) {
        json.put("orderByClause", sanitized);
      }
    } else {
      json.put("orderByClause", "ord.creationDate desc");
    }

    // Parse dynamic filters from request parameters
    Map<String, String[]> parameterMap = request.getParameterMap();
    Set<String> processedProperties = new HashSet<>();

    for (String paramName : parameterMap.keySet()) {
      // Skip reserved parameters and modifier suffixes
      if (RESERVED_PARAMS.contains(paramName) 
          || paramName.equals("limit") || paramName.equals("offset")
          || paramName.endsWith("_op") || paramName.endsWith("_isId")) {
        continue;
      }

      // Check if it's a supported filter property
      if (FILTER_PROPERTIES.contains(paramName)) {
        String value = request.getParameter(paramName);
        if (value != null && !value.trim().isEmpty()) {
          value = value.trim();
          
          // Check for operator modifier
          String operator = request.getParameter(paramName + "_op");
          boolean isId = "true".equalsIgnoreCase(request.getParameter(paramName + "_isId"));
          
          // Determine operator: isId -> equals, explicit operator, or default to contains
          if (operator == null || operator.isEmpty()) {
            operator = isId ? "equals" : "contains";
          } else if (!VALID_OPERATORS.contains(operator)) {
            throw new InvalidFilterException("Invalid operator '" + operator 
                + "' for property '" + paramName + "'. Valid operators: " + VALID_OPERATORS);
          }
          
          remoteFilters.put(buildRemoteFilter(paramName, value, operator, isId));
          processedProperties.add(paramName);
        }
      }
      // Handle date properties specially (via parameters, not remoteFilters)
      else if (DATE_PROPERTIES.contains(paramName)) {
        String value = request.getParameter(paramName);
        if (value != null && !value.trim().isEmpty()) {
          parameters.put(paramName, value.trim());
          processedProperties.add(paramName);
        }
      }
    }

    // Require at least one filter
    if (remoteFilters.length() == 0 && parameters.length() == 0) {
      throw new MissingParameterException(
          "At least one filter parameter is required. Supported filters: " 
          + FILTER_PROPERTIES + ", " + DATE_PROPERTIES);
    }

    json.put("remoteFilters", remoteFilters);
    json.put("parameters", parameters);

    return json;
  }

  /**
   * Builds a remote filter object in the format expected by ProcessHQLQuery.
   * 
   * @param column The column/property to filter on
   * @param value The filter value
   * @param operator The filter operator (equals, contains, etc.)
   * @param isId Whether the value is a UUID
   * @return JSONObject representing the remote filter
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildRemoteFilter(String column, String value, String operator, boolean isId)
      throws JSONException {
    JSONObject filter = new JSONObject();
    JSONArray columns = new JSONArray();
    columns.put(column);
    filter.put("columns", columns);
    filter.put("value", value);
    filter.put("operator", operator);
    if (isId) {
      filter.put("isId", true);
    }
    return filter;
  }

  /**
   * Sanitizes the orderBy clause to prevent SQL injection.
   * Only allows safe column references and order directions.
   * 
   * @param orderBy The raw orderBy value
   * @return Sanitized orderBy or null if invalid
   */
  private String sanitizeOrderBy(String orderBy) {
    if (orderBy == null) {
      return null;
    }

    // Only allow alphanumeric, dots, underscores, spaces, and asc/desc
    String sanitized = orderBy.replaceAll("[^a-zA-Z0-9_.\\s]", "").trim();

    // Verify it ends with asc or desc (case insensitive)
    String lower = sanitized.toLowerCase();
    if (!lower.endsWith("asc") && !lower.endsWith("desc")) {
      sanitized = sanitized + " asc";
    }

    // Ensure it starts with a valid prefix
    if (!sanitized.startsWith("ord.")) {
      sanitized = "ord." + sanitized;
    }

    return sanitized;
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

  /**
   * Utility method to convert all request parameters to JSON.
   * Useful for debugging or for passing all parameters to the filter.
   * 
   * @param jsonParams Base JSON object to add parameters to
   * @param request The HTTP request
   * @return The JSON object with added parameters
   * @throws JSONException if JSON construction fails
   */
  public JSONObject requestParamsToJson(JSONObject jsonParams, HttpServletRequest request)
      throws JSONException {
    Map<String, String[]> params = request.getParameterMap();

    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      String key = entry.getKey();
      String[] values = entry.getValue();

      if (values.length == 1) {
        jsonParams.put(key, values[0]);
      } else if (values.length > 1) {
        JSONArray jsonArray = new JSONArray();
        for (String value : values) {
          jsonArray.put(value);
        }
        jsonParams.put(key, jsonArray);
      }
    }

    return jsonParams;
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

  /**
   * Exception thrown when an invalid filter type is specified.
   */
  private static class InvalidFilterException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidFilterException(String message) {
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
