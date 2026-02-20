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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.UpdateCashup;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for master terminal cash close aggregation.
 *
 * <p>This is the equivalent of the master terminal's cash-close data retrieval step.
 * It finds all slave terminals, associates their cashups with the master's parent cashup,
 * and returns aggregated payment method data for the cash close process.</p>
 *
 * <h3>Required Parameters (GET):</h3>
 * <ul>
 *   <li>{@code pos} - Master Terminal ID (UUID)</li>
 * </ul>
 *
 * <h3>Response:</h3>
 * Returns JSON with slave terminal statuses and aggregated payment method summaries.
 */
public class GetCashCloseMaster implements WebService {

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
      JSONObject responseJson = new JSONObject();
      JSONArray slavesArray = new JSONArray();

      // 1. Verify the terminal is a master
      if (!isMasterTerminal(conn, masterPosId)) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Terminal is not configured as a master terminal");
        return;
      }

      // 2. Get the master's current unprocessed cashup
      String masterCashupId = getCurrentCashupId(conn, masterPosId);
      if (masterCashupId == null) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "No unprocessed cashup found for master terminal");
        return;
      }

      // 3. Find all slave terminals for this master
      List<String[]> slaveTerminals = getSlaveTerminals(conn, masterPosId);

      for (String[] slave : slaveTerminals) {
        String slaveId = slave[0];
        String slaveName = slave[1];

        JSONObject slaveJson = new JSONObject();
        slaveJson.put("terminalId", slaveId);
        slaveJson.put("terminalName", slaveName);

        // 4. Get slave's current unprocessed cashup
        String slaveCashupId = getCurrentCashupId(conn, slaveId);
        if (slaveCashupId == null) {
          slaveJson.put("hasCashup", false);
          slaveJson.put("cashupId", JSONObject.NULL);
          slavesArray.put(slaveJson);
          continue;
        }

        slaveJson.put("hasCashup", true);
        slaveJson.put("cashupId", slaveCashupId);

        // 5. Associate slave cashup with master — requires DAL entity objects
        // OBDal.get() by primary key is NOT HQL, it's a direct Hibernate session.get()
        try {
          OBPOSAppCashup slaveCashup = OBDal.getInstance()
              .get(OBPOSAppCashup.class, slaveCashupId);
          OBPOSApplications slaveTerminal = OBDal.getInstance()
              .get(OBPOSApplications.class, slaveId);
          if (slaveCashup != null && slaveTerminal != null) {
            UpdateCashup.associateMasterSlave(slaveCashup, slaveTerminal);
            OBDal.getInstance().flush();
          }
        } catch (Exception e) {
          log.warn("Could not associate slave {} with master cashup: {}",
              slaveId, e.getMessage());
        }

        // 6. Check if slave cashup has pending transactions
        int pendingTxCount = countPendingTransactions(conn, slaveCashupId);
        slaveJson.put("pendingTransactions", pendingTxCount);

        // 7. Get aggregated payment method data from slave's cashup
        slaveJson.put("paymentMethodSummary",
            getPaymentMethodSummary(conn, slaveCashupId));

        slavesArray.put(slaveJson);
      }

      responseJson.put("data", slavesArray);
      responseJson.put("masterCashupId", masterCashupId);
      responseJson.put("status", 0);
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetCashCloseMaster WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error in cash close master: " + cause.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  // ============================================
  // Native SQL Queries
  // ============================================

  /**
   * Checks if the terminal is configured as a master (Ismaster = 'Y').
   */
  private boolean isMasterTerminal(Connection conn, String posId) throws Exception {
    String sql = "SELECT 1 FROM OBPOS_APPLICATIONS "
        + "WHERE Obpos_Applications_ID = ? AND Ismaster = 'Y'";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, posId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Gets the current (unprocessed) cashup ID for a terminal.
   * Returns null if no unprocessed cashup exists.
   */
  private String getCurrentCashupId(Connection conn, String posId) throws Exception {
    String sql = "SELECT Obpos_App_Cashup_ID "
        + "FROM OBPOS_App_Cashup "
        + "WHERE Obpos_Applications_ID = ? AND Isprocessed = 'N' "
        + "ORDER BY Created DESC LIMIT 1";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, posId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  /**
   * Finds all active slave terminals for a given master terminal.
   * Returns list of [terminalId, terminalName] pairs.
   */
  private List<String[]> getSlaveTerminals(Connection conn, String masterPosId) throws Exception {
    List<String[]> slaves = new ArrayList<>();
    String sql = "SELECT Obpos_Applications_ID, Name "
        + "FROM OBPOS_APPLICATIONS "
        + "WHERE Masterterminal_ID = ? AND Isactive = 'Y' "
        + "ORDER BY Name";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, masterPosId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          slaves.add(new String[] { rs.getString(1), rs.getString(2) });
        }
      }
    }
    return slaves;
  }

  /**
   * Counts unprocessed financial transactions for a cashup.
   */
  private int countPendingTransactions(Connection conn, String cashupId) throws Exception {
    String sql = "SELECT COUNT(*) "
        + "FROM FIN_Finacc_Transaction "
        + "WHERE EM_Obpos_App_Cashup_ID = ? AND Processed = 'N'";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, cashupId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  /**
   * Gets aggregated payment method summary for a cashup (SUM/GROUP BY).
   * Groups by payment method and returns totals for sales, returns, deposits, drops.
   */
  private JSONArray getPaymentMethodSummary(Connection conn, String cashupId) throws Exception {
    JSONArray result = new JSONArray();
    String sql = "SELECT pmc.Obpos_App_Payment_ID AS paymenttype, "
        + "  pmc.Name AS name, "
        + "  pmc.Searchkey AS searchkey, "
        + "  pmc.Isocode AS isocode, "
        + "  SUM(pmc.Startingcash) AS startingcash, "
        + "  SUM(pmc.Totalsales) AS totalsales, "
        + "  SUM(pmc.Totalreturns) AS totalreturns, "
        + "  SUM(pmc.Totaldeposits) AS totaldeposits, "
        + "  SUM(pmc.Totaldrops) AS totaldrops, "
        + "  SUM(pmc.Rate) AS rate "
        + "FROM OBPOS_paymentmethodcashup pmc "
        + "WHERE pmc.Obpos_App_Cashup_ID = ? "
        + "GROUP BY pmc.Obpos_App_Payment_ID, pmc.Name, pmc.Searchkey, pmc.Isocode "
        + "ORDER BY pmc.Name";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, cashupId);
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
          result.put(pm);
        }
      }
    }
    return result;
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
