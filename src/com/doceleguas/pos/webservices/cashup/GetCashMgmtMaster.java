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
 * WebService endpoint for retrieving aggregated shared payment method data
 * across slave terminals for cash management in a master terminal context.
 *
 * <p>This is the equivalent of the master terminal's cash management data retrieval.
 * It queries payment methods marked as "shared" ({@code Isshared = 'Y'} on
 * OBPOS_App_Payment_Type) and aggregates their cashup data across all slave
 * terminals' current (unprocessed) cashups that share the same parent cashup.</p>
 *
 * <h3>Required Parameters (GET):</h3>
 * <ul>
 *   <li>{@code pos} - Master Terminal ID (UUID)</li>
 * </ul>
 */
public class GetCashMgmtMaster implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      String masterPosId = request.getParameter("pos");
      if (masterPosId == null || masterPosId.trim().isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'pos'");
        return;
      }

      Connection conn = OBDal.getInstance().getConnection();

      // 1. Get the master's current unprocessed cashup (this is the parent)
      String masterCashupId = null;
      {
        String sql = "SELECT Obpos_App_Cashup_ID "
            + "FROM OBPOS_App_Cashup "
            + "WHERE Obpos_Applications_ID = ? AND Isprocessed = 'N' "
            + "ORDER BY Created DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, masterPosId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              masterCashupId = rs.getString(1);
            }
          }
        }
      }

      if (masterCashupId == null) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "No unprocessed cashup found for master terminal");
        return;
      }

      // 2. Aggregate shared payment method data across all child cashups
      //    (slave cashups whose Obpos_Parent_Cashup_ID = master cashup ID)
      //    filtered to only shared payment types (Isshared = 'Y' on OBPOS_App_Payment_Type).
      JSONArray dataArray = new JSONArray();
      String sql = "SELECT pmc.Obpos_App_Payment_ID AS paymenttype, "
          + "  pmc.Name AS name, "
          + "  pmc.Searchkey AS searchkey, "
          + "  pmc.Isocode AS isocode, "
          + "  SUM(pmc.Startingcash) AS startingcash, "
          + "  SUM(pmc.Totalsales) AS totalsales, "
          + "  SUM(pmc.Totalreturns) AS totalreturns, "
          + "  SUM(pmc.Totaldeposits) AS totaldeposits, "
          + "  SUM(pmc.Totaldrops) AS totaldrops, "
          + "  SUM(pmc.Rate) AS rate, "
          + "  SUM(pmc.Amounttokeep) AS amounttokeep "
          + "FROM OBPOS_paymentmethodcashup pmc "
          + "JOIN OBPOS_App_Cashup c "
          + "  ON pmc.Obpos_App_Cashup_ID = c.Obpos_App_Cashup_ID "
          + "JOIN OBPOS_APP_PAYMENT ap "
          + "  ON pmc.Obpos_App_Payment_ID = ap.Obpos_App_Payment_ID "
          + "JOIN OBPOS_App_Payment_Type apt "
          + "  ON ap.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
          + "WHERE c.Obpos_Parent_Cashup_ID = ? "
          + "  AND apt.Isshared = 'Y' "
          + "GROUP BY pmc.Obpos_App_Payment_ID, pmc.Name, pmc.Searchkey, pmc.Isocode "
          + "ORDER BY pmc.Name";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, masterCashupId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            JSONObject pm = new JSONObject();
            pm.put("paymentType", rs.getString("paymenttype"));
            pm.put("name", rs.getString("name"));
            pm.put("searchKey", rs.getString("searchkey"));
            pm.put("isocode", rs.getString("isocode"));
            pm.put("startingCash", rs.getBigDecimal("startingcash"));
            pm.put("totalSales", rs.getBigDecimal("totalsales"));
            pm.put("totalReturns", rs.getBigDecimal("totalreturns"));
            pm.put("totalDeposits", rs.getBigDecimal("totaldeposits"));
            pm.put("totalDrops", rs.getBigDecimal("totaldrops"));
            pm.put("rate", rs.getBigDecimal("rate"));
            pm.put("amountToKeep", rs.getBigDecimal("amounttokeep"));
            dataArray.put(pm);
          }
        }
      }

      // 3. Also include the master's own payment methods (for completeness)
      String sqlMaster = "SELECT pmc.Obpos_App_Payment_ID AS paymenttype, "
          + "  pmc.Name AS name, "
          + "  pmc.Searchkey AS searchkey, "
          + "  pmc.Isocode AS isocode, "
          + "  pmc.Startingcash AS startingcash, "
          + "  pmc.Totalsales AS totalsales, "
          + "  pmc.Totalreturns AS totalreturns, "
          + "  pmc.Totaldeposits AS totaldeposits, "
          + "  pmc.Totaldrops AS totaldrops, "
          + "  pmc.Rate AS rate, "
          + "  pmc.Amounttokeep AS amounttokeep "
          + "FROM OBPOS_paymentmethodcashup pmc "
          + "JOIN OBPOS_APP_PAYMENT ap "
          + "  ON pmc.Obpos_App_Payment_ID = ap.Obpos_App_Payment_ID "
          + "JOIN OBPOS_App_Payment_Type apt "
          + "  ON ap.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
          + "WHERE pmc.Obpos_App_Cashup_ID = ? "
          + "  AND apt.Isshared = 'Y' "
          + "ORDER BY pmc.Name";

      JSONArray masterArray = new JSONArray();
      try (PreparedStatement ps = conn.prepareStatement(sqlMaster)) {
        ps.setString(1, masterCashupId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            JSONObject pm = new JSONObject();
            pm.put("paymentType", rs.getString("paymenttype"));
            pm.put("name", rs.getString("name"));
            pm.put("searchKey", rs.getString("searchkey"));
            pm.put("isocode", rs.getString("isocode"));
            pm.put("startingCash", rs.getBigDecimal("startingcash"));
            pm.put("totalSales", rs.getBigDecimal("totalsales"));
            pm.put("totalReturns", rs.getBigDecimal("totalreturns"));
            pm.put("totalDeposits", rs.getBigDecimal("totaldeposits"));
            pm.put("totalDrops", rs.getBigDecimal("totaldrops"));
            pm.put("rate", rs.getBigDecimal("rate"));
            pm.put("amountToKeep", rs.getBigDecimal("amounttokeep"));
            masterArray.put(pm);
          }
        }
      }

      JSONObject responseJson = new JSONObject();
      responseJson.put("slaveSharedPayments", dataArray);
      responseJson.put("masterPayments", masterArray);
      responseJson.put("masterCashupId", masterCashupId);
      responseJson.put("status", 0);
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetCashMgmtMaster WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error in cash management master: " + cause.getMessage());
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
