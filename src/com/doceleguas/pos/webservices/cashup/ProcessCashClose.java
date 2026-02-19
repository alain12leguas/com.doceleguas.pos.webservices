/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.cashup;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.CashCloseProcessor;
import org.openbravo.retail.posterminal.CashupHook;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.OrderGroupingProcessor;
import org.openbravo.retail.posterminal.UpdateCashup;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for processing the POS cash close (end-of-day) operation.
 *
 * <p>This is the equivalent of
 * {@code org.openbravo.retail.posterminal.process.ProcessCashClose}
 * (JSONProcessSimple) but exposed as a standard WebService endpoint.
 * All lookup/filter queries use native SQL (PreparedStatement). Openbravo
 * infrastructure calls (UpdateCashup, CashCloseProcessor, OrderGroupingProcessor,
 * CashupHook) are preserved as they require DAL entity objects.</p>
 *
 * <h3>POST JSON Body:</h3>
 * <pre>{@code
 * {
 *   "cashUpId": "UUID",
 *   "cashUpDate": "yyyy-MM-dd",
 *   "jsonCashup": { ... },
 *   "cashMgmtIds": ["UUID1", "UUID2"],
 *   "slaveCashupIds": ["UUID1", "UUID2"],
 *   "approvals": [
 *     {
 *       "userId": "UUID",
 *       "approvalType": "TYPE",
 *       "approvalMessage": "reason text",
 *       "approvalReasonId": "UUID (optional)"
 *     }
 *   ]
 * }
 * }</pre>
 */
public class ProcessCashClose implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      // 1. Parse JSON body
      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = request.getReader()) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
      }
      JSONObject jsonBody = new JSONObject(sb.toString());

      String cashUpId = jsonBody.getString("cashUpId");
      String cashUpDateStr = jsonBody.getString("cashUpDate");
      JSONObject jsonCashup = jsonBody.optJSONObject("jsonCashup");
      if (jsonCashup == null) {
        jsonCashup = new JSONObject();
      }
      JSONArray cashMgmtIds = jsonBody.optJSONArray("cashMgmtIds");
      if (cashMgmtIds == null) {
        cashMgmtIds = new JSONArray();
      }
      JSONArray slaveCashupIdsArray = jsonBody.optJSONArray("slaveCashupIds");
      List<String> slaveCashupIds = new ArrayList<>();
      if (slaveCashupIdsArray != null) {
        for (int i = 0; i < slaveCashupIdsArray.length(); i++) {
          slaveCashupIds.add(slaveCashupIdsArray.getString(i));
        }
      }

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      Date cashUpDate = sdf.parse(cashUpDateStr);

      Connection conn = OBDal.getInstance().getConnection();

      // 2. Check for draft reconciliations — native SQL
      //    Replaces: OBCriteria loop over PaymentMethodCashup → FinancialAccount → Reconciliation
      if (hasDraftReconciliations(conn, cashUpId)) {
        sendErrorResponse(response, HttpServletResponse.SC_CONFLICT,
            "There are draft reconciliations for financial accounts "
                + "linked to this cashup. Please complete or void them before closing.");
        return;
      }

      // 3. Get terminal ID from cashup — native SQL
      String terminalId = null;
      {
        String sql = "SELECT Obpos_Applications_ID FROM OBPOS_App_Cashup "
            + "WHERE Obpos_App_Cashup_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, cashUpId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              terminalId = rs.getString(1);
            }
          }
        }
      }

      if (terminalId == null) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "Cashup not found: " + cashUpId);
        return;
      }

      // 4. Load entities required by Openbravo infrastructure methods
      //    OBDal.get() is NOT HQL — it's a direct Hibernate session.get() by PK
      OBPOSApplications posTerminal = OBDal.getInstance()
          .get(OBPOSApplications.class, terminalId);
      OBPOSAppCashup cashup = UpdateCashup.getAndUpdateCashUp(cashUpId, jsonCashup, cashUpDate);

      // 5. Process approvals if present — native SQL INSERT
      JSONArray approvals = jsonBody.optJSONArray("approvals");
      if (approvals != null && approvals.length() > 0) {
        processApprovals(conn, approvals, cashUpId);
      }

      // 6. For master terminals: accumulate payment methods from slave cashups
      boolean isMaster = false;
      {
        String sql = "SELECT Ismaster FROM OBPOS_APPLICATIONS "
            + "WHERE Obpos_Applications_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, terminalId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              isMaster = "Y".equals(rs.getString(1));
            }
          }
        }
      }

      if (isMaster && !slaveCashupIds.isEmpty()) {
        accumulateSlavePaymentMethods(conn, cashUpId, slaveCashupIds);
      }

      // 7. Group orders — Openbravo utility (requires entity objects)
      OrderGroupingProcessor orderGrouping = new OrderGroupingProcessor();
      JSONObject orderGroupResult = orderGrouping.groupOrders(posTerminal, cashUpId, cashUpDate);

      // 8. Execute CashupHook pre-processing via CDI
      try {
        javax.enterprise.inject.Instance<CashupHook> hooks =
            CDI.current().select(CashupHook.class);
        if (hooks != null) {
          for (CashupHook hook : hooks) {
            hook.exec(posTerminal, cashup, jsonCashup);
          }
        }
      } catch (Exception hookEx) {
        log.warn("Error executing CashupHook: {}", hookEx.getMessage());
      }

      // 9. Process the cash close — Openbravo infrastructure (requires entity objects)
      CashCloseProcessor processor = new CashCloseProcessor();
      JSONObject closeResult = processor.processCashClose(
          posTerminal, jsonCashup, cashMgmtIds, cashUpDate, slaveCashupIds);

      // 10. Flush all changes
      OBDal.getInstance().flush();

      // 11. Build response
      JSONObject responseJson = new JSONObject();
      responseJson.put("status", 0);
      responseJson.put("cashupId", cashUpId);
      if (closeResult != null) {
        responseJson.put("closeResult", closeResult);
      }
      if (orderGroupResult != null) {
        responseJson.put("orderGroupResult", orderGroupResult);
      }
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in ProcessCashClose WebService", cause);
      try {
        OBDal.getInstance().rollbackAndClose();
      } catch (Exception rollbackEx) {
        log.error("Error during rollback", rollbackEx);
      }
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error processing cash close: " + cause.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  // ============================================
  // Native SQL Queries
  // ============================================

  /**
   * Checks for draft reconciliations on financial accounts linked to the cashup.
   * Replaces the original multi-level OBCriteria loop with a single native SQL query.
   */
  private boolean hasDraftReconciliations(Connection conn, String cashUpId) throws Exception {
    String sql = "SELECT 1 FROM FIN_Reconciliation r "
        + "JOIN FIN_Financial_Account fa "
        + "  ON r.FIN_Financial_Account_ID = fa.Fin_Financial_Account_ID "
        + "JOIN OBPOS_APP_PAYMENT ap "
        + "  ON ap.FIN_Financial_Account_ID = fa.Fin_Financial_Account_ID "
        + "JOIN OBPOS_paymentmethodcashup pmc "
        + "  ON pmc.Obpos_App_Payment_ID = ap.Obpos_App_Payment_ID "
        + "WHERE pmc.Obpos_App_Cashup_ID = ? "
        + "  AND r.Docstatus = 'DR' "
        + "LIMIT 1";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, cashUpId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Processes cashup approval records via native SQL INSERT.
   * Creates OBPOS_Cashup_Approval records without using OBCriteria or HQL.
   */
  private void processApprovals(Connection conn, JSONArray approvals, String cashUpId)
      throws Exception {
    String sql = "INSERT INTO OBPOS_Cashup_Approval "
        + "(Obpos_Cashup_Approval_ID, AD_Client_ID, AD_Org_ID, Isactive, "
        + " Created, Createdby, Updated, Updatedby, "
        + " Obpos_App_Cashup_ID, AD_User_ID, Approval_Type, Approval_Message) "
        + "VALUES (?, ?, ?, 'Y', NOW(), ?, NOW(), ?, ?, ?, ?, ?)";

    String clientId = OBContext.getOBContext().getCurrentClient().getId();
    String orgId = OBContext.getOBContext().getCurrentOrganization().getId();
    String currentUserId = OBContext.getOBContext().getUser().getId();

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (int i = 0; i < approvals.length(); i++) {
        JSONObject approval = approvals.getJSONObject(i);
        String approvalId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String userId = approval.optString("userId", currentUserId);
        String approvalType = approval.optString("approvalType", "");
        String approvalMessage = approval.optString("approvalMessage", "");

        ps.setString(1, approvalId);
        ps.setString(2, clientId);
        ps.setString(3, orgId);
        ps.setString(4, currentUserId);
        ps.setString(5, currentUserId);
        ps.setString(6, cashUpId);
        ps.setString(7, userId);
        ps.setString(8, approvalType);
        ps.setString(9, approvalMessage);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  /**
   * Accumulates payment method data from slave cashups into the master cashup.
   * Uses native SQL to aggregate slave data and update the master's payment method records.
   *
   * <p>For each payment type in the master cashup, sums the corresponding values
   * (sales, returns, deposits, drops) from all slave cashups linked via parent cashup.</p>
   */
  private void accumulateSlavePaymentMethods(Connection conn, String masterCashupId,
      List<String> slaveCashupIds) throws Exception {
    if (slaveCashupIds.isEmpty()) {
      return;
    }

    // Build placeholders for IN clause
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < slaveCashupIds.size(); i++) {
      if (i > 0) {
        placeholders.append(", ");
      }
      placeholders.append("?");
    }

    // Aggregate slave payment data grouped by payment method (Obpos_App_Payment_ID)
    String sqlAgg = "SELECT pmc.Obpos_App_Payment_ID AS paymenttype, "
        + "  SUM(pmc.Totalsales) AS totalsales, "
        + "  SUM(pmc.Totalreturns) AS totalreturns, "
        + "  SUM(pmc.Totaldeposits) AS totaldeposits, "
        + "  SUM(pmc.Totaldrops) AS totaldrops "
        + "FROM OBPOS_paymentmethodcashup pmc "
        + "WHERE pmc.Obpos_App_Cashup_ID IN (" + placeholders + ") "
        + "GROUP BY pmc.Obpos_App_Payment_ID";

    try (PreparedStatement psFetch = conn.prepareStatement(sqlAgg)) {
      int idx = 1;
      for (String slaveId : slaveCashupIds) {
        psFetch.setString(idx++, slaveId);
      }
      try (ResultSet rs = psFetch.executeQuery()) {
        // For each aggregated slave payment type, update the master's corresponding record
        String sqlUpdate = "UPDATE OBPOS_paymentmethodcashup SET "
            + "  Totalsales = Totalsales + ?, "
            + "  Totalreturns = Totalreturns + ?, "
            + "  Totaldeposits = Totaldeposits + ?, "
            + "  Totaldrops = Totaldrops + ? "
            + "WHERE Obpos_App_Cashup_ID = ? AND Obpos_App_Payment_ID = ?";

        try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
          while (rs.next()) {
            psUpdate.setBigDecimal(1, rs.getBigDecimal("totalsales"));
            psUpdate.setBigDecimal(2, rs.getBigDecimal("totalreturns"));
            psUpdate.setBigDecimal(3, rs.getBigDecimal("totaldeposits"));
            psUpdate.setBigDecimal(4, rs.getBigDecimal("totaldrops"));
            psUpdate.setString(5, masterCashupId);
            psUpdate.setString(6, rs.getString("paymenttype"));
            psUpdate.addBatch();
          }
          psUpdate.executeBatch();
        }
      }
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
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "GET method not supported. Use POST instead.");
  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "DELETE method not supported. Use POST instead.");
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "PUT method not supported. Use POST instead.");
  }
}
