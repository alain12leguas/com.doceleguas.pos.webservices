/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.cashup;

import java.io.BufferedReader;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.UpdateCashup;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for registering a new (unprocessed) POS cashup on the backend.
 *
 * <p>Called by the OCRE-POS frontend immediately after a cash close completes.
 * While {@link ProcessCashClose} handles closing and reconciling the <em>old</em>
 * cashup, this endpoint registers the <em>new</em> (empty, unprocessed) cashup so
 * the backend has a record of it — enabling recovery via {@code GetCashup?isprocessed=N}
 * if the frontend's local state is lost.
 *
 * <p>This mirrors the original Openbravo POS behavior where
 * {@code CompleteCashupAndCreateNew.js} sends a second message containing the new
 * cashup to the backend. In that architecture both messages go to
 * {@code ProcessCashClose} via the ImportEntry queue; here we use a dedicated
 * endpoint for clarity.
 *
 * <p>The only backend call is {@link UpdateCashup#getAndUpdateCashUp}, which will:
 * <ul>
 *   <li>Create a new {@code OBPOS_App_Cashup} record if the given UUID does not exist</li>
 *   <li>Update it if it already exists (idempotent — safe for retries)</li>
 *   <li>Create/update {@code OBPOS_PaymentMethodCashup} rows with starting cash</li>
 * </ul>
 * No reconciliation is performed (unlike ProcessCashClose).
 *
 * <h3>POST JSON Body:</h3>
 * <pre>{@code
 * {
 *   "cashUpId": "UUID",
 *   "jsonCashup": {
 *     "id": "UUID",
 *     "posterminal": "OBPOS_Applications.id",
 *     "userId": "AD_User.id",
 *     "trxOrganization": "AD_Org.id",
 *     "isprocessed": "N",
 *     "creationDate": "ISO-8601",
 *     "cashUpDate": "ISO-8601",
 *     "netSales": "0.00",
 *     "grossSales": "0.00",
 *     "netReturns": "0.00",
 *     "grossReturns": "0.00",
 *     "totalRetailTransactions": "0.00",
 *     "totalStartings": "0.00",
 *     "cashPaymentMethodInfo": [...],
 *     "cashCloseInfo": [],
 *     "cashTaxInfo": [],
 *     "cashMgmtIds": []
 *   }
 * }
 * }</pre>
 */
public class OpenCashup implements WebService {

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
      JSONObject jsonCashup = jsonBody.optJSONObject("jsonCashup");
      if (jsonCashup == null) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required field: jsonCashup");
        return;
      }

      // Use the creation date as the cashup date for the new (open) cashup
      Date cashUpDate = new Date();

      // 2. Delegate to UpdateCashup — creates the record if it doesn't exist,
      //    updates it if it does (idempotent for retry safety).
      //    Because isprocessed="N", ProcessCashClose's reconciliation branch
      //    will NOT be triggered — UpdateCashup only persists the data.
      OBPOSAppCashup cashUp = UpdateCashup.getAndUpdateCashUp(cashUpId, jsonCashup, cashUpDate);

      // 3. Flush
      OBDal.getInstance().flush();

      // 4. Build success response
      JSONObject responseJson = new JSONObject();
      responseJson.put("status", 0);
      responseJson.put("cashupId", cashUpId);
      responseJson.put("isprocessed", cashUp.isProcessed() ? "Y" : "N");
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in OpenCashup WebService", cause);
      try {
        OBDal.getInstance().rollbackAndClose();
      } catch (Exception rollbackEx) {
        log.error("Error during rollback", rollbackEx);
      }
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error registering new cashup: " + cause.getMessage());
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
