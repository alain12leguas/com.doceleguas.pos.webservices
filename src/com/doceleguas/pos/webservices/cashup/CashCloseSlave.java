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
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.UpdateCashup;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for slave terminal cash close association.
 *
 * <p>This is the equivalent of the slave terminal's step during the master-slave
 * cash close flow. When a slave terminal initiates a cash close, this endpoint
 * calls {@code UpdateCashup.associateMasterSlave()} to link the slave's cashup
 * with its master terminal's parent cashup.</p>
 *
 * <h3>Required Parameters (GET):</h3>
 * <ul>
 *   <li>{@code pos} - Slave Terminal ID (UUID)</li>
 *   <li>{@code cashup} - Slave Cashup ID (UUID)</li>
 * </ul>
 *
 * <h3>Response:</h3>
 * Returns JSON indicating whether the association was successful and whether
 * the slave cashup now has a parent cashup.
 */
public class CashCloseSlave implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      String posId = request.getParameter("pos");
      String cashupId = request.getParameter("cashup");

      if (posId == null || posId.trim().isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'pos'");
        return;
      }
      if (cashupId == null || cashupId.trim().isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'cashup'");
        return;
      }

      Connection conn = OBDal.getInstance().getConnection();

      // 1. Verify cashup exists via native SQL
      boolean cashupExists;
      {
        String sql = "SELECT 1 FROM OBPOS_App_Cashup "
            + "WHERE Obpos_App_Cashup_ID = ? AND Obpos_Applications_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, cashupId);
          ps.setString(2, posId);
          try (ResultSet rs = ps.executeQuery()) {
            cashupExists = rs.next();
          }
        }
      }

      if (!cashupExists) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "Cashup not found for the specified terminal");
        return;
      }

      // 2. Associate slave cashup with master — requires DAL entity objects
      //    OBDal.get() is NOT HQL, it's a direct Hibernate session.get() by PK
      OBPOSAppCashup appCashup = OBDal.getInstance().get(OBPOSAppCashup.class, cashupId);
      OBPOSApplications posTerminal = OBDal.getInstance().get(OBPOSApplications.class, posId);

      if (appCashup != null && posTerminal != null) {
        UpdateCashup.associateMasterSlave(appCashup, posTerminal);
        OBDal.getInstance().flush();
        // Evict and reload to get the updated state
        OBDal.getInstance().getSession().evict(appCashup);
      }

      // 3. Check if parent cashup was assigned — native SQL
      boolean hasMaster;
      String parentCashupId = null;
      {
        String sql = "SELECT Obpos_Parent_Cashup_ID "
            + "FROM OBPOS_App_Cashup "
            + "WHERE Obpos_App_Cashup_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, cashupId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              parentCashupId = rs.getString(1);
              hasMaster = (parentCashupId != null);
            } else {
              hasMaster = false;
            }
          }
        }
      }

      JSONObject responseJson = new JSONObject();
      responseJson.put("status", 0);
      responseJson.put("hasMaster", hasMaster);
      responseJson.put("parentCashupId", hasMaster ? parentCashupId : JSONObject.NULL);
      responseJson.put("cashupId", cashupId);
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in CashCloseSlave WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error in cash close slave: " + cause.getMessage());
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
