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
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for retrieving Terminal configuration data.
 * 
 * This service provides a REST API for retrieving detailed terminal configuration data including
 * currency, payment methods, price lists, and other terminal-specific settings.
 * 
 * <p>
 * Required parameters:
 * </p>
 * <ul>
 * <li>{@code terminalSearchKey} - Terminal search key (or terminalId)</li>
 * <li>{@code terminalId} - Terminal UUID (alternative to searchKey)</li>
 * </ul>
 * 
 * <p>
 * Returns computed array properties:
 * </p>
 * <ul>
 * <li>{@code payments} - Payment methods configured for the terminal</li>
 * <li>{@code priceLists} - All available price lists</li>
 * <li>{@code currencyPanel} - Currency denominations for cash payments</li>
 * <li>{@code cashMgmtDepositEvents} - Deposit events for cash management</li>
 * <li>{@code cashMgmtDropEvents} - Drop events for cash management</li>
 * <li>{@code exchangeRates} - Currency exchange rates</li>
 * </ul>
 */
public class GetTerminal implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    long startTime = System.currentTimeMillis();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      JSONObject jsonParams = new JSONObject();
      jsonParams.put("terminalSearchKey", request.getParameter("terminalName"));
      // OBPOSApplications app = getTerminal(jsonParams.getString("terminalName"));
      // jsonParams.put("client", app.getOrganization().getClient().getId());
      // jsonParams.put("organization", app.getOrganization().getId());
      // jsonParams.put("pos", app.getId());
      // log.debug("GetTerminal request: {}", jsonParams.toString());

      // OBContext.setOBContext(OBContext.getOBContext().getUser().getId(),
      // OBContext.getOBContext().getRole().getId(), jsonParams.getString("client"),
      // jsonParams.getString("organization"));

      TerminalModel terminalModel = new TerminalModel();
      NativeQuery<?> query = terminalModel.createQuery(jsonParams);

      @SuppressWarnings("unchecked")
      java.util.List<Object> results = (java.util.List<Object>) query.list();

      JSONObject responseJson = new JSONObject();
      if (results.isEmpty()) {
        responseJson.put("success", false);
        responseJson.put("error", true);
        responseJson.put("message", "Terminal not found");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = (Map<String, Object>) results.get(0);
        JSONObject terminalData = terminalModel.rowToJson(rowMap);
        responseJson.put("terminal", terminalData);
        String terminalId = getTerminalId(rowMap);
        String organizationId = getOrganizationId(rowMap);
        String currencyId = getCurrencyId(rowMap);

        JSONArray payments = terminalModel.getPayments(terminalId);
        responseJson.put("payments", payments);

        JSONArray currencyPanel = terminalModel.getCurrencyPanel(terminalId);
        responseJson.put("currencyPanel", currencyPanel);

        JSONArray depositEvents = terminalModel.getCashMgmtDepositEvents(terminalId);
        responseJson.put("cashMgmtDepositEvents", depositEvents);

        JSONArray dropEvents = terminalModel.getCashMgmtDropEvents(terminalId);
        responseJson.put("cashMgmtDropEvents", dropEvents);

        if (organizationId != null) {
          JSONArray priceLists = terminalModel.getPriceLists(organizationId);
          responseJson.put("priceLists", priceLists);
        }

        if (organizationId != null && currencyId != null) {
          JSONArray exchangeRates = terminalModel.getExchangeRates(organizationId, currencyId);
          responseJson.put("exchangeRates", exchangeRates);
        }
        responseJson.put("success", true);
      }

      response.getWriter().write(responseJson.toString());

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("GetTerminal completed in {}ms", elapsed);

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetTerminal WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error executing query: " + cause.getMessage());
    }
  }

  private String getTerminalId(Map<String, Object> rowMap) {
    Object id = rowMap.get("terminalid");
    return id != null ? id.toString() : null;
  }

  private String getOrganizationId(Map<String, Object> rowMap) {
    Object id = rowMap.get("organizationid");
    return id != null ? id.toString() : null;
  }

  private String getCurrencyId(Map<String, Object> rowMap) {
    Object id = rowMap.get("currencyid");
    return id != null ? id.toString() : null;
  }

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
    }
  }

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
