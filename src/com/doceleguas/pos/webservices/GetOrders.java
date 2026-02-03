/*
 ************************************************************************************
 * Copyright (C) 2025 Doceleguas
 * Licensed under the Openbravo Commercial License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.retail.posterminal.PaidReceiptsFilter;
import org.openbravo.service.web.WebService;

/**
 * WebService to retrieve Orders using the PaidReceiptsFilter mechanism.
 * 
 * <p>Accepts GET requests with query parameters and builds internally the JSON
 * request expected by {@link PaidReceiptsFilter}.</p>
 * 
 * @see PaidReceiptsFilter
 * @see org.openbravo.retail.posterminal.PaidReceiptsFilterProperties
 */
public class GetOrders implements WebService {

  private static final Logger log = LogManager.getLogger();

  // Default values
  private static final int DEFAULT_LIMIT = 50;
  private static final int DEFAULT_OFFSET = 0;
  private static final String DEFAULT_ORDER_BY = "creationDate desc, documentNo desc";

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      // Validate required parameter: pos
      String pos = request.getParameter("pos");
      if (pos == null || pos.trim().isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'pos'. Provide a valid terminal ID.");
        return;
      }

      // Build JSON request in the format expected by PaidReceiptsFilter
      JSONObject jsonRequest = buildJsonRequest(request, pos.trim());
      
      log.debug("GetOrders request: {}", jsonRequest.toString());

      // Get the filter instance via WeldUtils and execute
      PaidReceiptsFilter filter = WeldUtils
          .getInstanceFromStaticBeanManager(PaidReceiptsFilter.class);
      StringWriter writer = new StringWriter();
      filter.exec(writer, jsonRequest);
      
      // Write the response
      String filterResult = writer.toString();
      response.getWriter().write("{" + filterResult + "}");

    } catch (Exception e) {
      log.error("Error in GetOrders WebService", e);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error: " + e.getMessage());
    }
  }

  /**
   * Builds the JSON request in the format expected by PaidReceiptsFilter.
   * 
   * <p>The resulting JSON follows the mobile core convention:</p>
   * <pre>
   * {
   *   "client": "...",
   *   "organization": "...",
   *   "pos": "...",
   *   "remoteFilters": [...],
   *   "orderByClause": "...",
   *   "_limit": 50,
   *   "_offset": 0
   * }
   * </pre>
   */
  private JSONObject buildJsonRequest(HttpServletRequest request, String pos) throws JSONException {
    JSONObject json = new JSONObject();
    JSONArray remoteFilters = new JSONArray();

    // Required context fields
    json.put("client", OBContext.getOBContext().getCurrentClient().getId());
    json.put("organization", OBContext.getOBContext().getCurrentOrganization().getId());
    json.put("pos", pos);

    // Build remoteFilters from query parameters
    
    // documentNo - contains filter
    String documentNo = request.getParameter("documentNo");
    if (isNotEmpty(documentNo)) {
      remoteFilters.put(buildFilter("documentNo", documentNo, "contains"));
    }

    // businessPartner - equals filter
    String businessPartner = request.getParameter("businessPartner");
    if (isNotEmpty(businessPartner)) {
      remoteFilters.put(buildFilter("businessPartner", businessPartner, "="));
    }

    // orderDateFrom and orderDateTo - combined as a date range filter
    String orderDateFrom = request.getParameter("orderDateFrom");
    String orderDateTo = request.getParameter("orderDateTo");
    if (isNotEmpty(orderDateFrom) || isNotEmpty(orderDateTo)) {
      remoteFilters.put(buildDateFilter("orderDate", "OrderDate", orderDateFrom, orderDateTo));
    }

    // totalamountFrom and totalamountTo - combined as an amount range filter
    String totalamountFrom = request.getParameter("totalamountFrom");
    String totalamountTo = request.getParameter("totalamountTo");
    if (isNotEmpty(totalamountFrom) || isNotEmpty(totalamountTo)) {
      remoteFilters.put(buildAmountFilter("totalamount", totalamountFrom, totalamountTo));
    }

    // orderType - equals filter (ORD, RET, LAY, verifiedReturns, payOpenTickets)
    String orderType = request.getParameter("orderType");
    if (isNotEmpty(orderType)) {
      remoteFilters.put(buildFilter("orderType", orderType, "="));
    }

    // store - for cross-store filtering
    String store = request.getParameter("store");
    if (isNotEmpty(store)) {
      remoteFilters.put(buildFilter("store", store, "="));
    }

    json.put("remoteFilters", remoteFilters);

    // Pagination
    json.put("_limit", parseIntOrDefault(request.getParameter("limit"), DEFAULT_LIMIT));
    json.put("_offset", parseIntOrDefault(request.getParameter("offset"), DEFAULT_OFFSET));

    // Ordering
    String orderBy = request.getParameter("orderBy");
    json.put("orderByClause", isNotEmpty(orderBy) ? orderBy : DEFAULT_ORDER_BY);
    json.put("orderByProperties", JSONObject.NULL);

    return json;
  }

  /**
   * Builds a simple filter object.
   * 
   * @param column The column name to filter on
   * @param value The filter value
   * @param operator The filter operator (=, contains, etc.)
   */
  private JSONObject buildFilter(String column, String value, String operator) throws JSONException {
    JSONObject filter = new JSONObject();
    filter.put("columns", new JSONArray().put(column));
    filter.put("value", value);
    filter.put("operator", operator);
    filter.put("isId", false);
    return filter;
  }

  /**
   * Builds a date range filter object.
   * 
   * <p>Uses the "filter" operator with params array [fromDate, toDate]
   * as expected by PaidReceiptsFilter for date ranges.</p>
   * 
   * @param column The column name (e.g., "orderDate")
   * @param displayValue The display value (e.g., "OrderDate")
   * @param fromDate Start date (can be null)
   * @param toDate End date (can be null)
   */
  private JSONObject buildDateFilter(String column, String displayValue, 
      String fromDate, String toDate) throws JSONException {
    JSONObject filter = new JSONObject();
    filter.put("columns", new JSONArray().put(column));
    filter.put("value", displayValue);
    filter.put("operator", "filter");
    
    JSONArray params = new JSONArray();
    params.put(fromDate != null ? fromDate : JSONObject.NULL);
    params.put(toDate != null ? toDate : JSONObject.NULL);
    filter.put("params", params);
    
    filter.put("isId", false);
    return filter;
  }

  /**
   * Builds an amount range filter object.
   * 
   * @param column The column name (e.g., "totalamount")
   * @param fromAmount Minimum amount (can be null)
   * @param toAmount Maximum amount (can be null)
   */
  private JSONObject buildAmountFilter(String column, String fromAmount, String toAmount) 
      throws JSONException {
    JSONObject filter = new JSONObject();
    filter.put("columns", new JSONArray().put(column));
    filter.put("value", "Amount");
    filter.put("operator", "filter");
    
    JSONArray params = new JSONArray();
    params.put(fromAmount != null ? fromAmount : JSONObject.NULL);
    params.put(toAmount != null ? toAmount : JSONObject.NULL);
    filter.put("params", params);
    
    filter.put("isId", false);
    return filter;
  }

  /**
   * Checks if a string is not null and not empty.
   */
  private boolean isNotEmpty(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /**
   * Parses an integer from a string, returning a default value if parsing fails.
   */
  private int parseIntOrDefault(String value, int defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Sends an error response as JSON.
   */
  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setStatus(statusCode);
    try {
      JSONObject errorJson = new JSONObject();
      errorJson.put("error", true);
      errorJson.put("message", message);
      errorJson.put("statusCode", statusCode);
      response.getWriter().write(errorJson.toString());
    } catch (JSONException e) {
      response.getWriter().write("{\"error\":true,\"message\":\"" + message + "\"}");
    }
  }

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "POST method not supported. Use GET with query parameters.");
  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "DELETE method not supported. Use GET with query parameters.");
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "PUT method not supported. Use GET with query parameters.");
  }
}
