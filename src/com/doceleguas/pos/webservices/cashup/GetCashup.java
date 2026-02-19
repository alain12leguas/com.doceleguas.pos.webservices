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
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

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
 * WebService endpoint for retrieving the current or last processed cashup for a POS terminal.
 *
 * <p>This is the equivalent of {@code org.openbravo.retail.posterminal.master.Cashup}
 * (JSONProcessSimple) but exposed as a standard WebService endpoint using native SQL queries
 * with real database column names.</p>
 *
 * <h3>Required Parameters (GET):</h3>
 * <ul>
 *   <li>{@code pos} - Terminal ID (UUID)</li>
 *   <li>{@code isprocessed} - "Y" or "N" to filter processed state</li>
 * </ul>
 *
 * <h3>Optional Parameters:</h3>
 * <ul>
 *   <li>{@code isprocessedbo} - "Y" or "N" to filter back-office processed state</li>
 *   <li>{@code client} - Client ID (defaults to current context)</li>
 *   <li>{@code organization} - Organization ID (defaults to current context)</li>
 * </ul>
 *
 * @see org.openbravo.retail.posterminal.master.Cashup Original implementation
 */
public class GetCashup implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      String posId = getRequiredParam(request, "pos");
      String isprocessed = getRequiredParam(request, "isprocessed");

      // Check for cashup errors in the terminal (native SQL equivalent of POSUtils)
      if (cashupErrorsExist(posId)) {
        sendErrorResponse(response, HttpServletResponse.SC_CONFLICT,
            "There are cashup errors in this terminal");
        return;
      }

      String isprocessedbo = request.getParameter("isprocessedbo");

      // Set OBContext if client/org are provided
      String client = request.getParameter("client");
      String organization = request.getParameter("organization");
      if (client != null && organization != null) {
        OBContext.setOBContext(
            OBContext.getOBContext().getUser().getId(),
            OBContext.getOBContext().getRole().getId(),
            client, organization);
      }

      String clientId = OBContext.getOBContext().getCurrentClient().getId();

      // Main cashup query — native SQL on OBPOS_App_Cashup
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT c.Obpos_App_Cashup_ID AS id, ");
      sql.append("  c.Netsales AS netsales, c.Grosssales AS grosssales, ");
      sql.append("  c.Netreturns AS netreturns, c.Grossreturns AS grossreturns, ");
      sql.append("  c.Totalretailtransactions AS totalretailtransactions, ");
      sql.append("  c.Created AS creationdate, c.Createdby AS userid, ");
      sql.append("  c.Isprocessed AS isprocessed, ");
      sql.append("  c.Obpos_Applications_ID AS posterminal, ");
      sql.append("  c.AD_Org_ID AS organization ");
      sql.append("FROM OBPOS_App_Cashup c ");
      sql.append("WHERE c.Isprocessed = ? ");
      sql.append("  AND c.Obpos_Applications_ID = ? ");
      sql.append("  AND c.AD_Client_ID = ? ");
      if (isprocessedbo != null && !isprocessedbo.trim().isEmpty()) {
        sql.append("  AND c.Isprocessedbo = ? ");
      }
      sql.append("ORDER BY c.Created DESC ");
      sql.append("LIMIT 1");

      Connection conn = OBDal.getInstance().getConnection();
      JSONObject responseJson = new JSONObject();
      JSONArray respArray = new JSONArray();

      try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
        int idx = 1;
        ps.setString(idx++, isprocessed.equalsIgnoreCase("Y") ? "Y" : "N");
        ps.setString(idx++, posId);
        ps.setString(idx++, clientId);
        if (isprocessedbo != null && !isprocessedbo.trim().isEmpty()) {
          ps.setString(idx++, isprocessedbo.equalsIgnoreCase("Y") ? "Y" : "N");
        }

        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            JSONObject cashupJSON = new JSONObject();
            String cashupId = rs.getString("id");
            cashupJSON.put("id", cashupId);
            cashupJSON.put("netSales", rs.getBigDecimal("netsales"));
            cashupJSON.put("grossSales", rs.getBigDecimal("grosssales"));
            cashupJSON.put("netReturns", rs.getBigDecimal("netreturns"));
            cashupJSON.put("grossReturns", rs.getBigDecimal("grossreturns"));
            cashupJSON.put("totalRetailTransactions",
                rs.getBigDecimal("totalretailtransactions"));

            Timestamp created = rs.getTimestamp("creationdate");
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            df.setTimeZone(tz);
            cashupJSON.put("creationDate", created != null ? df.format(created) : JSONObject.NULL);
            cashupJSON.put("userId", rs.getString("userid"));
            cashupJSON.put("isprocessed",
                "Y".equals(rs.getString("isprocessed")) ? "Y" : "N");
            cashupJSON.put("posterminal", rs.getString("posterminal"));
            cashupJSON.put("organization", rs.getString("organization"));

            // Get payment methods info
            cashupJSON.put("cashPaymentMethodInfo", getPayments(conn, cashupId, posId));

            // Get tax info
            cashupJSON.put("cashTaxInfo", getTaxes(conn, cashupId));

            // Get cash management transactions
            cashupJSON.put("cashMgmInfo", getCashMgmt(conn, cashupId, posId));

            respArray.put(cashupJSON);
          }
        }
      }

      responseJson.put("data", respArray);
      responseJson.put("status", 0);
      response.getWriter().write(responseJson.toString());

    } catch (MissingParameterException e) {
      log.warn("Missing parameter in GetCashup: {}", e.getMessage());
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in GetCashup WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error retrieving cashup: " + cause.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  // ============================================
  // Cashup Errors Check
  // ============================================

  /**
   * Checks if there are cashup-related errors for the terminal.
   * Native SQL equivalent of {@code POSUtils.cashupErrorsExistInTerminal()}.
   */
  private boolean cashupErrorsExist(String posId) throws Exception {
    Connection conn = OBDal.getInstance().getConnection();
    String sql = "SELECT 1 FROM OBPOS_Errors "
        + "WHERE Obpos_Applications_ID = ? "
        + "  AND Typeofdata = 'OBPOS_App_Cashup' "
        + "  AND Orderstatus = 'N' "
        + "LIMIT 1";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, posId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return true;
        }
      }
    }
    // Also check Import Entry errors
    String sqlIE = "SELECT 1 FROM C_Import_Entry "
        + "WHERE EM_Obpos_Applications_ID = ? "
        + "  AND Typeofdata = 'OBPOS_App_Cashup' "
        + "  AND Importstatus = 'Error' "
        + "LIMIT 1";
    try (PreparedStatement ps = conn.prepareStatement(sqlIE)) {
      ps.setString(1, posId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  // ============================================
  // Payment Methods
  // ============================================

  /**
   * Retrieves all payment method cashup records for a given cashup.
   * Native SQL joining OBPOS_paymentmethodcashup, OBPOS_APP_PAYMENT, and
   * OBPOS_App_Payment_Type for line number and countPerAmount flag.
   */
  private JSONArray getPayments(Connection conn, String cashupId, String posId) throws Exception {
    JSONArray respArray = new JSONArray();

    String sql = "SELECT pmc.Obpos_Paymentmethodcashup_ID AS id, "
        + "  pmc.Obpos_App_Payment_ID AS paymenttype, "
        + "  pmc.Searchkey AS searchkey, pmc.Name AS name, "
        + "  pmc.Startingcash AS startingcash, pmc.Totalsales AS totalsales, "
        + "  pmc.Totalreturns AS totalreturns, pmc.Rate AS rate, "
        + "  pmc.Isocode AS isocode, pmc.Amounttokeep AS amounttokeep, "
        + "  pmc.Totaldeposits AS totaldeposits, pmc.Totaldrops AS totaldrops, "
        + "  pmc.Totalcounted AS totalcounted, pmc.Initialcounted AS initialcounted, "
        + "  pmc.Obpos_App_Cashup_ID AS cashup_id, "
        + "  ap.Line AS lineno, "
        + "  apt.Countperamount AS countperamount "
        + "FROM OBPOS_paymentmethodcashup pmc "
        + "LEFT JOIN OBPOS_APP_PAYMENT ap ON pmc.Obpos_App_Payment_ID = ap.Obpos_App_Payment_ID "
        + "LEFT JOIN OBPOS_App_Payment_Type apt ON ap.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "WHERE pmc.Obpos_App_Cashup_ID = ? "
        + "ORDER BY ap.Line";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, cashupId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          JSONObject pmJSON = new JSONObject();
          String pmcId = rs.getString("id");
          pmJSON.put("id", pmcId);
          pmJSON.put("cashup_id", rs.getString("cashup_id"));
          pmJSON.put("searchKey", rs.getString("searchkey"));
          pmJSON.put("name", rs.getString("name"));
          pmJSON.put("paymentmethod_id", rs.getString("paymenttype"));
          pmJSON.put("paymentType", rs.getString("paymenttype"));
          pmJSON.put("paymentTypeId", rs.getString("paymenttype"));
          pmJSON.put("startingCash", rs.getBigDecimal("startingcash"));
          pmJSON.put("totalSales", rs.getBigDecimal("totalsales"));
          pmJSON.put("totalReturns", rs.getBigDecimal("totalreturns"));
          pmJSON.put("rate", rs.getBigDecimal("rate"));
          pmJSON.put("isocode", rs.getString("isocode"));
          pmJSON.put("amountToKeep", rs.getBigDecimal("amounttokeep"));
          pmJSON.put("totalDeposits", rs.getBigDecimal("totaldeposits"));
          pmJSON.put("totalDrops", rs.getBigDecimal("totaldrops"));
          pmJSON.put("totalCounted", rs.getBigDecimal("totalcounted"));
          pmJSON.put("initialCounted", rs.getBigDecimal("initialcounted"));
          pmJSON.put("lineNo", rs.getLong("lineno"));

          // Add countPerAmount if the payment type supports it
          if ("Y".equals(rs.getString("countperamount"))) {
            pmJSON.put("countPerAmount", getCountPerAmount(conn, pmcId));
          }

          respArray.put(pmJSON);
        }
      }
    }
    return respArray;
  }

  /**
   * Retrieves count-per-amount denominations for a payment method cashup.
   * Native SQL on obpos_pmcashup_amntcnt.
   */
  private JSONObject getCountPerAmount(Connection conn, String paymentMethodCashupId)
      throws Exception {
    JSONObject countObj = new JSONObject();
    String sql = "SELECT Amount, Expected "
        + "FROM obpos_pmcashup_amntcnt "
        + "WHERE Obpos_Paymentmethodcashup_ID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, paymentMethodCashupId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          countObj.put(rs.getBigDecimal("amount").toString(), rs.getLong("expected"));
        }
      }
    }
    return countObj;
  }

  // ============================================
  // Taxes
  // ============================================

  /**
   * Retrieves all tax cashup records for a given cashup.
   * Native SQL on OBPOS_taxcashup.
   */
  private JSONArray getTaxes(Connection conn, String cashupId) throws Exception {
    JSONArray respArray = new JSONArray();
    String sql = "SELECT Obpos_Taxcashup_ID AS id, Name AS name, "
        + "  Amount AS amount, Ordertype AS ordertype, "
        + "  Obpos_App_Cashup_ID AS cashup_id "
        + "FROM OBPOS_taxcashup "
        + "WHERE Obpos_App_Cashup_ID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, cashupId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          JSONObject taxJSON = new JSONObject();
          taxJSON.put("id", rs.getString("id"));
          taxJSON.put("name", rs.getString("name"));
          taxJSON.put("amount", rs.getBigDecimal("amount"));
          taxJSON.put("orderType", rs.getString("ordertype"));
          taxJSON.put("cashup_id", rs.getString("cashup_id"));
          respArray.put(taxJSON);
        }
      }
    }
    return respArray;
  }

  // ============================================
  // Cash Management Transactions
  // ============================================

  /**
   * Retrieves all cash management transactions (deposits/drops) for the cashup.
   * Uses native SQL with JOINs replacing the original multi-step HQL approach
   * that used ExtendsCashManagementPaymentTypeHook.
   *
   * <p>The GL Items are resolved directly from the OBPOS_App_Payment_Type table columns:
   * C_Glitem_Dropdep_ID, C_Glitem_Writeoff_ID, C_Glitem_Diff_ID,
   * C_Glitem_Deposits_ID, C_Glitem_Drops_ID.</p>
   */
  private JSONArray getCashMgmt(Connection conn, String cashupId, String posId) throws Exception {
    JSONArray respArray = new JSONArray();

    // Single query that finds FIN_Finacc_Transaction records for this cashup
    // joined with OBPOS_APP_PAYMENT to resolve payment method and reason,
    // filtering by GL Items from the terminal's payment type configuration.
    String sql = "SELECT ft.Fin_Finacc_Transaction_ID AS id, "
        + "  ft.Description AS description, "
        + "  ft.Paymentamt AS paymentamount, "
        + "  ft.Depositamt AS depositamount, "
        + "  ft.Created AS creationdate, "
        + "  ft.Createdby AS userid, "
        + "  u.Name AS username, "
        + "  ft.C_Glitem_ID AS glitem, "
        + "  cur.ISO_Code AS isocode, "
        + "  ap.Obpos_App_Payment_ID AS paymentmethodid, "
        + "  ap.Obretco_Cmevents_ID AS reasonid, "
        + "  ft.EM_Obpos_App_Cashup_ID AS cashup_id "
        + "FROM FIN_Finacc_Transaction ft "
        + "JOIN OBPOS_APP_PAYMENT ap "
        + "  ON ft.Fin_Financial_Account_ID = ap.FIN_Financial_Account_ID "
        + "  AND ap.Isactive = 'Y' "
        + "  AND ap.Obpos_Applications_ID = ? "
        + "LEFT JOIN FIN_Financial_Account fa "
        + "  ON ft.Fin_Financial_Account_ID = fa.Fin_Financial_Account_ID "
        + "LEFT JOIN C_Currency cur ON fa.C_Currency_ID = cur.C_Currency_ID "
        + "LEFT JOIN AD_User u ON ft.Createdby = u.AD_User_ID "
        + "WHERE ft.EM_Obpos_App_Cashup_ID = ? "
        + "  AND ft.C_Glitem_ID IN ( "
        + "    SELECT DISTINCT glid FROM ( "
        + "      SELECT apt.C_Glitem_Dropdep_ID AS glid "
        + "        FROM OBPOS_App_Payment_Type apt "
        + "        JOIN OBPOS_APP_PAYMENT ap2 ON ap2.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "        WHERE ap2.Isactive = 'Y' AND ap2.Obpos_Applications_ID = ? "
        + "      UNION "
        + "      SELECT apt.C_Glitem_Writeoff_ID "
        + "        FROM OBPOS_App_Payment_Type apt "
        + "        JOIN OBPOS_APP_PAYMENT ap2 ON ap2.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "        WHERE ap2.Isactive = 'Y' AND ap2.Obpos_Applications_ID = ? "
        + "      UNION "
        + "      SELECT apt.C_Glitem_Diff_ID "
        + "        FROM OBPOS_App_Payment_Type apt "
        + "        JOIN OBPOS_APP_PAYMENT ap2 ON ap2.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "        WHERE ap2.Isactive = 'Y' AND ap2.Obpos_Applications_ID = ? "
        + "      UNION "
        + "      SELECT apt.C_Glitem_Deposits_ID "
        + "        FROM OBPOS_App_Payment_Type apt "
        + "        JOIN OBPOS_APP_PAYMENT ap2 ON ap2.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "        WHERE ap2.Isactive = 'Y' AND ap2.Obpos_Applications_ID = ? "
        + "      UNION "
        + "      SELECT apt.C_Glitem_Drops_ID "
        + "        FROM OBPOS_App_Payment_Type apt "
        + "        JOIN OBPOS_APP_PAYMENT ap2 ON ap2.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
        + "        WHERE ap2.Isactive = 'Y' AND ap2.Obpos_Applications_ID = ? "
        + "    ) items WHERE glid IS NOT NULL "
        + "  )";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      // Main query params
      ps.setString(1, posId);   // ap.Obpos_Applications_ID
      ps.setString(2, cashupId); // ft.EM_Obpos_App_Cashup_ID
      // Subquery params (one per UNION branch)
      ps.setString(3, posId);
      ps.setString(4, posId);
      ps.setString(5, posId);
      ps.setString(6, posId);
      ps.setString(7, posId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          JSONObject result = new JSONObject();
          float paymentAmt = rs.getFloat("paymentamount");
          float depositAmt = rs.getFloat("depositamount");
          float totalamt = paymentAmt + depositAmt;

          result.put("id", rs.getString("id"));
          result.put("description", rs.getString("description"));
          result.put("amount", String.valueOf(totalamt));
          result.put("origAmount", String.valueOf(totalamt));
          result.put("type", paymentAmt == 0 ? "deposit" : "drop");
          result.put("reasonId", rs.getString("reasonid"));
          result.put("paymentMethodId", rs.getString("paymentmethodid"));
          Timestamp ts = rs.getTimestamp("creationdate");
          result.put("creationDate", ts != null ? ts.toString() : "");
          result.put("timezoneOffset", "0");
          result.put("userId", rs.getString("userid"));
          result.put("user", rs.getString("username"));
          result.put("isocode", rs.getString("isocode"));
          result.put("cashup_id", rs.getString("cashup_id"));
          result.put("glItem", rs.getString("glitem"));
          result.put("_idx", "");
          respArray.put(result);
        }
      }
    }
    return respArray;
  }

  // ============================================
  // Utility Methods
  // ============================================

  private String getRequiredParam(HttpServletRequest request, String paramName)
      throws MissingParameterException {
    String value = request.getParameter(paramName);
    if (value == null || value.trim().isEmpty()) {
      throw new MissingParameterException("Missing required parameter: '" + paramName + "'");
    }
    return value.trim();
  }

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

  private static class MissingParameterException extends Exception {
    private static final long serialVersionUID = 1L;
    public MissingParameterException(String message) { super(message); }
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
