/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.io.StringWriter;
import java.util.Map;

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

/**
 * WebService endpoint for querying Orders with various filter options.
 * 
 * This service provides a REST API for retrieving order data using different
 * filter types. It acts as a wrapper that translates HTTP parameters into the
 * JSON format expected by {@link GetOrdersFilter}, which executes the actual
 * HQL query against the database.
 * 
 * <h2>Architecture</h2>
 * This implementation follows the ProcessHQLQueryValidated pattern used by
 * PaidReceiptsFilter in the retail.posterminal module, providing:
 * <ul>
 *   <li>Direct DAL access (no HTTP proxy overhead)</li>
 *   <li>Extensible properties via CDI/ModelExtension</li>
 *   <li>Native pagination with _limit and _offset</li>
 *   <li>Consistent API with other mobile endpoints</li>
 * </ul>
 * 
 * <h2>Endpoint</h2>
 * <pre>GET /ws/com.doceleguas.pos.webservices.GetOrders</pre>
 * 
 * <h2>Filter Types</h2>
 * <ul>
 *   <li><b>byId</b>: Filter by order UUID. Requires: id</li>
 *   <li><b>byDocumentNo</b>: Filter by document number. Requires: documentNo</li>
 *   <li><b>byOrgOrderDate</b>: Filter by org and date. Requires: organization, orderDate</li>
 *   <li><b>byOrgOrderDateRange</b>: Filter by org and date range. Requires: organization, dateFrom, dateTo</li>
 * </ul>
 * 
 * <h2>Optional Parameters</h2>
 * <ul>
 *   <li><b>limit</b>: Maximum number of results (default: unlimited)</li>
 *   <li><b>offset</b>: Offset for pagination (default: 0)</li>
 *   <li><b>orderBy</b>: Order by clause (e.g., "orderDate desc")</li>
 * </ul>
 * 
 * <h2>Example Requests</h2>
 * <pre>
 * GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byId&amp;id=ABC123
 * GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byDocumentNo&amp;documentNo=ORD-001
 * GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDate&amp;organization=STORE1&amp;orderDate=2025-01-15
 * GET /ws/com.doceleguas.pos.webservices.GetOrders?filter=byOrgOrderDateRange&amp;organization=STORE1&amp;dateFrom=2025-01-01&amp;dateTo=2025-01-31&amp;limit=100
 * </pre>
 * 
 * @see GetOrdersFilter
 * @see com.doceleguas.pos.webservices.orders.GetOrdersFilterProperties
 */
public class GetOrders implements WebService {

  private static final Logger log = LogManager.getLogger();

  // Filter type constants
  private static final String FILTER_BY_ID = "byId";
  private static final String FILTER_BY_DOCUMENT_NO = "byDocumentNo";
  private static final String FILTER_BY_ORG_ORDER_DATE = "byOrgOrderDate";
  private static final String FILTER_BY_ORG_ORDER_DATE_RANGE = "byOrgOrderDateRange";

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
   * - Client and organization context from OBContext
   * - Filter type translation to remoteFilters array
   * - Pagination parameters (_limit, _offset)
   * - Order by clause
   * 
   * @param request The HTTP servlet request
   * @return JSONObject in the format expected by ProcessHQLQuery
   * @throws MissingParameterException if required parameters are missing
   * @throws InvalidFilterException if the filter type is invalid
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildJsonRequest(HttpServletRequest request)
      throws MissingParameterException, InvalidFilterException, JSONException {

    String filterType = request.getParameter("filter");
    if (filterType == null || filterType.isEmpty()) {
      throw new MissingParameterException(
          "Missing 'filter' parameter. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange");
    }

    JSONObject json = new JSONObject();
    JSONArray remoteFilters = new JSONArray();
    JSONObject parameters = new JSONObject();

    // Set client and organization from OBContext
    OBContext ctx = OBContext.getOBContext();
    json.put("client", ctx.getCurrentClient().getId());
    json.put("organization", ctx.getCurrentOrganization().getId());

    // Handle pagination
    String limit = request.getParameter("limit");
    if (limit != null && !limit.isEmpty()) {
      try {
        json.put("_limit", Integer.parseInt(limit));
      } catch (NumberFormatException e) {
        log.warn("Invalid limit parameter: {}", limit);
      }
    }

    String offset = request.getParameter("offset");
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
      // Sanitize orderBy to prevent injection
      String sanitized = sanitizeOrderBy(orderBy);
      if (sanitized != null) {
        json.put("orderByClause", sanitized);
      }
    } else {
      // Default ordering by creation date descending
      json.put("orderByClause", "ord.creationDate desc");
    }

    // Build remoteFilters based on filter type
    switch (filterType) {
      case FILTER_BY_ID:
        String id = getRequiredParameter(request, "id");
        remoteFilters.put(buildRemoteFilter("id", id, "equals"));
        break;

      case FILTER_BY_DOCUMENT_NO:
        String documentNo = getRequiredParameter(request, "documentNo");
        remoteFilters.put(buildRemoteFilter("documentNo", documentNo, "iContains"));
        break;

      case FILTER_BY_ORG_ORDER_DATE:
        String orgName = getRequiredParameter(request, "organization");
        String orderDate = getRequiredParameter(request, "orderDate");
        remoteFilters.put(buildRemoteFilter("organization", orgName, "iContains"));
        remoteFilters.put(buildRemoteFilter("orderDate", orderDate, "equals"));
        break;

      case FILTER_BY_ORG_ORDER_DATE_RANGE:
        String org = getRequiredParameter(request, "organization");
        String dateFrom = getRequiredParameter(request, "dateFrom");
        String dateTo = getRequiredParameter(request, "dateTo");
        remoteFilters.put(buildRemoteFilter("organization", org, "iContains"));
        // Store date range in parameters for the HQL where clause
        parameters.put("dateFrom", dateFrom);
        parameters.put("dateTo", dateTo);
        break;

      default:
        throw new InvalidFilterException("Invalid filter type: '" + filterType
            + "'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange");
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
   * @param operator The filter operator (equals, iContains, etc.)
   * @return JSONObject representing the remote filter
   * @throws JSONException if JSON construction fails
   */
  private JSONObject buildRemoteFilter(String column, String value, String operator)
      throws JSONException {
    JSONObject filter = new JSONObject();
    JSONArray columns = new JSONArray();
    columns.put(column);
    filter.put("columns", columns);
    filter.put("value", value);
    filter.put("operator", operator);
    return filter;
  }

  /**
   * Gets a required parameter from the request, throwing an exception if missing.
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
