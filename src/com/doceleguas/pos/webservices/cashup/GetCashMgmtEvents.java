/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.cashup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for retrieving Cash Management Events configured for a POS terminal.
 *
 * <p>This is the equivalent of {@code org.openbravo.retail.posterminal.master.CashManagementEvents}
 * but exposed as a standard WebService endpoint using native SQL.</p>
 *
 * <h3>Required Parameters (GET):</h3>
 * <ul>
 *   <li>{@code pos} - Terminal ID (UUID)</li>
 * </ul>
 *
 * <h3>Optional Parameters:</h3>
 * <ul>
 *   <li>{@code type} - "deposit", "drop", or empty for all</li>
 * </ul>
 *
 * @see org.openbravo.retail.posterminal.master.CashManagementEvents Original implementation
 */
public class GetCashMgmtEvents implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      String posId = request.getParameter("pos");
      if (posId == null || posId.trim().isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'pos'");
        return;
      }

      String type = request.getParameter("type");

      // Query to get Cash Management Events for payment methods linked to the terminal.
      // Joins OBRETCO_CMEvents → OBPOS_APP_PAYMENT (through FIN_Paymentmethod_ID matching)
      // and retrieves currency ISO code.
      //
      // The Eventtype column stores a string that may contain 'IN' (deposit) and/or 'OUT' (drop).
      // When a type filter is provided, we use LIKE to match the event direction.
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT DISTINCT cme.Obretco_Cmevents_ID AS id, ");
      sql.append("  cme.Name AS name, ");
      sql.append("  cme.Eventtype AS eventtype, ");
      sql.append("  cur.ISO_Code AS isocode, ");
      sql.append("  cme.FIN_Financial_Account_ID AS financialaccountid ");
      sql.append("FROM OBRETCO_CMEvents cme ");
      sql.append("JOIN OBPOS_APP_PAYMENT ap ");
      sql.append("  ON ap.Obretco_Cmevents_ID = cme.Obretco_Cmevents_ID ");
      sql.append("  AND ap.Isactive = 'Y' ");
      sql.append("  AND ap.Obpos_Applications_ID = ? ");
      sql.append("LEFT JOIN C_Currency cur ON cme.C_Currency_ID = cur.C_Currency_ID ");
      sql.append("WHERE cme.Isactive = 'Y' ");

      if ("deposit".equalsIgnoreCase(type)) {
        sql.append("  AND cme.Eventtype LIKE '%IN%' ");
      } else if ("drop".equalsIgnoreCase(type)) {
        sql.append("  AND cme.Eventtype LIKE '%OUT%' ");
      }
      sql.append("ORDER BY cme.Name");

      Connection conn = OBDal.getInstance().getConnection();
      JSONObject responseJson = new JSONObject();
      JSONArray dataArray = new JSONArray();

      try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
        ps.setString(1, posId);

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            JSONObject event = new JSONObject();
            event.put("id", rs.getString("id"));
            event.put("name", rs.getString("name"));
            event.put("eventType", rs.getString("eventtype"));
            event.put("isocode", rs.getString("isocode"));
            event.put("financialAccountId", rs.getString("financialaccountid"));
            dataArray.put(event);
          }
        }
      }

      responseJson.put("data", dataArray);
      responseJson.put("status", 0);
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetCashMgmtEvents WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error retrieving cash management events: " + cause.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  // ============================================
  // Utility Methods
  // ============================================

  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
    response.setStatus(statusCode);
    try {
      JSONObject errorJson = new JSONObject();
      errorJson.put("status", -1);
      errorJson.put("error", true);
      errorJson.put("message", message);
      response.getWriter().write(errorJson.toString());
    } catch (Exception e) {
      log.error("Error sending error response", e);
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
