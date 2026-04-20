/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.OBPOSApplications;
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
public class LoadTerminal implements WebService {

  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings({ "unchecked", "deprecation" })
  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    long startTime = System.currentTimeMillis();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      final String terminalSearchKey = request.getParameter("terminalName");
      OBPOSApplications pOSTerminal = getTerminal(terminalSearchKey);
      TerminalModel terminalModel = new TerminalModel(pOSTerminal);
      NativeQuery<?> query = terminalModel.createQuery();
      query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

      List<?> results = query.list();

      JSONObject responseJson = new JSONObject();
      if (results.isEmpty()) {
        responseJson.put("success", false);
        responseJson.put("error", true);
        responseJson.put("message", "Terminal not found");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        Map<String, Object> rowMap = (Map<String, Object>) results.get(0);

        JSONObject terminalJson = terminalModel.buildTerminalJson(rowMap);

        terminalJson.put("payments", terminalModel.getPayments());
        terminalJson.put("currencyPanel", terminalModel.getCurrencyPanel());
        terminalJson.put("cashMgmtDepositEvents", terminalModel.getCashMgmtDepositEvents());
        terminalJson.put("cashMgmtDropEvents", terminalModel.getCashMgmtDropEvents());
        terminalJson.put("priceLists", terminalModel.getPriceLists());
        terminalJson.put("hardwareURL", terminalModel.getHardwareUrl());
        terminalJson.put("deliveryModes", terminalModel.getDeliveryModes());
        terminalJson.put("rates", terminalModel.getExchangeRates());
        responseJson.put("terminal", terminalJson);
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

  private OBPOSApplications getTerminal(String term) {
    OBCriteria<OBPOSApplications> terminalCriteria = OBDal.getInstance()
        .createCriteria(OBPOSApplications.class);
    terminalCriteria
        .add(Restrictions.or(Restrictions.eq(OBPOSApplications.PROPERTY_SEARCHKEY, term),
            Restrictions.eq(OBPOSApplications.PROPERTY_ID, term)));
    terminalCriteria.setMaxResults(1);
    return (OBPOSApplications) terminalCriteria.uniqueResult();
  }
}
