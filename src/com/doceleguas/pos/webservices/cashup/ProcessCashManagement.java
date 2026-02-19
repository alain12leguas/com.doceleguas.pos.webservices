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
import java.util.Date;
import java.util.UUID;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.advpaymentmngt.dao.TransactionsDao;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.currency.ConversionRateDoc;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.financialmgmt.gl.GLItem;
import org.openbravo.model.financialmgmt.payment.FIN_FinaccTransaction;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSAppPayment;
import org.openbravo.retail.posterminal.OBPOSPaymentMethodCashup;
import org.openbravo.retail.posterminal.OBPOSPaymentcashupEvents;
import org.openbravo.retail.posterminal.ProcessCashMgmtHook;
import org.openbravo.retail.posterminal.UpdateCashup;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

/**
 * WebService endpoint for processing a cash management transaction (deposit or drop).
 *
 * <p>This is the equivalent of
 * {@code org.openbravo.retail.posterminal.process.ProcessCashMgmt}
 * (JSONProcessSimple) but exposed as a standard WebService endpoint.
 * All lookup queries use native SQL (PreparedStatement). Entity creation uses
 * DAL (OBProvider + OBDal.save()) to ensure proper Openbravo infrastructure
 * compatibility (triggers, hooks, security context).</p>
 *
 * <h3>POST JSON Body:</h3>
 * <pre>{@code
 * {
 *   "cashUpId": "UUID",
 *   "cashUpDate": "yyyy-MM-dd",
 *   "paymentMethodId": "UUID (OBPOS_APP_PAYMENT ID)",
 *   "amount": 100.00,
 *   "origAmount": 100.00,
 *   "type": "deposit" | "drop",
 *   "description": "Cash deposit description",
 *   "reasonId": "UUID (OBRETCO_CMEvents ID, optional)",
 *   "rate": 1.0,
 *   "isocode": "ZAR",
 *   "foreignCurrencyId": "UUID (optional, for multi-currency)",
 *   "foreignAmount": 100.00 (optional)
 * }
 * }</pre>
 */
public class ProcessCashManagement implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    OBContext.setAdminMode(true);
    try {
      // 1. Parse the JSON body
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
      String paymentMethodId = jsonBody.getString("paymentMethodId");
      BigDecimal amount = new BigDecimal(jsonBody.getString("amount"));
      BigDecimal origAmount = jsonBody.has("origAmount")
          ? new BigDecimal(jsonBody.getString("origAmount")) : amount;
      String type = jsonBody.getString("type"); // "deposit" or "drop"
      String description = jsonBody.optString("description", "");
      String reasonId = jsonBody.optString("reasonId", null);
      BigDecimal rate = jsonBody.has("rate")
          ? new BigDecimal(jsonBody.getString("rate")) : BigDecimal.ONE;
      String isocode = jsonBody.optString("isocode", "");
      String foreignCurrencyId = jsonBody.optString("foreignCurrencyId", null);
      BigDecimal foreignAmount = jsonBody.has("foreignAmount")
          ? new BigDecimal(jsonBody.getString("foreignAmount")) : null;

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      Date cashUpDate = sdf.parse(cashUpDateStr);

      Connection conn = OBDal.getInstance().getConnection();

      // 2. Update/get the cashup entity — Openbravo utility, returns entity needed by hooks
      OBPOSAppCashup cashup = UpdateCashup.getAndUpdateCashUp(cashUpId, jsonBody, cashUpDate);

      // 3. Find PaymentMethodCashup ID by native SQL
      //    Replaces: OBCriteria<OBPOSPaymentMethodCashup> with Restrictions
      String paymentMethodCashupId = null;
      {
        String sql = "SELECT Obpos_Paymentmethodcashup_ID "
            + "FROM OBPOS_paymentmethodcashup "
            + "WHERE Obpos_App_Payment_ID = ? AND Obpos_App_Cashup_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, paymentMethodId);
          ps.setString(2, cashUpId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              paymentMethodCashupId = rs.getString(1);
            }
          }
        }
      }

      if (paymentMethodCashupId == null) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "Payment method cashup not found for payment=" + paymentMethodId
                + " cashup=" + cashUpId);
        return;
      }

      // 4. Get financial account ID by native SQL
      //    Replaces: appPayment.getFinancialAccount()
      String financialAccountId = null;
      String financialAccountCurrencyId = null;
      {
        String sql = "SELECT ap.FIN_Financial_Account_ID, fa.C_Currency_ID "
            + "FROM OBPOS_APP_PAYMENT ap "
            + "JOIN FIN_Financial_Account fa "
            + "  ON ap.FIN_Financial_Account_ID = fa.Fin_Financial_Account_ID "
            + "WHERE ap.Obpos_App_Payment_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, paymentMethodId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              financialAccountId = rs.getString(1);
              financialAccountCurrencyId = rs.getString(2);
            }
          }
        }
      }

      if (financialAccountId == null) {
        sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
            "Financial account not found for payment method: " + paymentMethodId);
        return;
      }

      // 5. Get GL Item ID by native SQL — from payment type configuration
      String glItemId = null;
      {
        String sql = "SELECT apt.C_Glitem_Dropdep_ID "
            + "FROM OBPOS_APP_PAYMENT ap "
            + "JOIN OBPOS_App_Payment_Type apt "
            + "  ON ap.Obpos_App_Payment_Type_ID = apt.Obpos_App_Payment_Type_ID "
            + "WHERE ap.Obpos_App_Payment_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, paymentMethodId);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
              glItemId = rs.getString(1);
            }
          }
        }
      }

      // 6. Load entities needed by Openbravo utilities and hooks via OBDal.get() (not HQL)
      OBPOSAppPayment appPayment = OBDal.getInstance()
          .get(OBPOSAppPayment.class, paymentMethodId);
      FIN_FinancialAccount finAccount = OBDal.getInstance()
          .get(FIN_FinancialAccount.class, financialAccountId);
      OBPOSPaymentMethodCashup pmCashup = OBDal.getInstance()
          .get(OBPOSPaymentMethodCashup.class, paymentMethodCashupId);
      GLItem glItem = glItemId != null
          ? (GLItem) OBDal.getInstance().get(GLItem.class, glItemId) : null;

      // 7. Get max line number for the financial account
      Long maxLineNo = TransactionsDao.getTransactionMaxLineNo(finAccount);
      long nextLine = (maxLineNo != null ? maxLineNo : 0L) + 10L;

      // 8. Create FIN_FinaccTransaction entity
      //    Uses DAL (not HQL) to ensure proper trigger/validator execution
      FIN_FinaccTransaction transaction = OBProvider.getInstance()
          .get(FIN_FinaccTransaction.class);
      transaction.setId(UUID.randomUUID().toString().replace("-", "").toUpperCase());
      transaction.setNewOBObject(true);
      transaction.setOrganization(cashup.getOrganization());
      transaction.setAccount(finAccount);
      transaction.setCurrency(finAccount.getCurrency());
      transaction.setLineNo(nextLine);
      transaction.setGLItem(glItem);
      transaction.setDescription(description);
      transaction.setDateAcct(cashUpDate);
      transaction.setTransactionDate(cashUpDate);
      transaction.setProcessed(true);
      transaction.setStatus("RPPC"); // Remitted Payment Pending Clearing

      if ("deposit".equalsIgnoreCase(type)) {
        transaction.setDepositAmount(amount);
        transaction.setPaymentAmount(BigDecimal.ZERO);
        transaction.setTransactionType("BPD");
      } else {
        transaction.setPaymentAmount(amount);
        transaction.setDepositAmount(BigDecimal.ZERO);
        transaction.setTransactionType("BPW");
      }

      // Multi-currency support
      if (foreignCurrencyId != null && !foreignCurrencyId.isEmpty()) {
        Currency foreignCurrency = OBDal.getInstance()
            .get(Currency.class, foreignCurrencyId);
        transaction.setForeignCurrency(foreignCurrency);
        transaction.setForeignConversionRate(rate);
        transaction.setForeignAmount(foreignAmount != null ? foreignAmount : origAmount);
      }

      // Link to cashup
      transaction.setObposAppCashup(cashup);

      OBDal.getInstance().save(transaction);

      // 9. Create OBPOSPaymentcashupEvents entity (needed by hooks)
      OBPOSPaymentcashupEvents cashupEvent = OBProvider.getInstance()
          .get(OBPOSPaymentcashupEvents.class);
      cashupEvent.setId(UUID.randomUUID().toString().replace("-", "").toUpperCase());
      cashupEvent.setNewOBObject(true);
      cashupEvent.setOrganization(cashup.getOrganization());
      cashupEvent.setObposPaymentmethodcashup(pmCashup);
      cashupEvent.setName(description);
      cashupEvent.setRate(rate);
      cashupEvent.setCurrency(isocode);
      cashupEvent.setAmount(amount);
      cashupEvent.setType(type);
      cashupEvent.setFINFinaccTransaction(transaction);

      OBDal.getInstance().save(cashupEvent);

      // 10. Create ConversionRateDoc if needed (multi-currency)
      if (foreignCurrencyId != null && !foreignCurrencyId.isEmpty()
          && !foreignCurrencyId.equals(financialAccountCurrencyId)) {
        ConversionRateDoc convRateDoc = OBProvider.getInstance()
            .get(ConversionRateDoc.class);
        convRateDoc.setId(UUID.randomUUID().toString().replace("-", "").toUpperCase());
        convRateDoc.setNewOBObject(true);
        convRateDoc.setOrganization(cashup.getOrganization());
        convRateDoc.setCurrency(finAccount.getCurrency());
        convRateDoc.setToCurrency(
            OBDal.getInstance().get(Currency.class, foreignCurrencyId));
        convRateDoc.setRate(rate);
        convRateDoc.setForeignAmount(foreignAmount != null ? foreignAmount : origAmount);
        convRateDoc.setFINFinancialAccountTransaction(transaction);
        OBDal.getInstance().save(convRateDoc);
      }

      // 11. Flush before executing hooks
      OBDal.getInstance().flush();

      // 12. Execute ProcessCashMgmtHook via CDI lookup
      try {
        javax.enterprise.inject.Instance<ProcessCashMgmtHook> hooks =
            CDI.current().select(ProcessCashMgmtHook.class,
                new javax.enterprise.inject.literal.InjectLiteral());
        if (hooks != null) {
          for (ProcessCashMgmtHook hook : hooks) {
            hook.exec(jsonBody, type, appPayment, cashup, cashupEvent, amount, origAmount);
          }
        }
      } catch (Exception hookEx) {
        // Hooks are optional extensions; log but don't fail the transaction
        log.warn("Error executing ProcessCashMgmtHook: {}", hookEx.getMessage());
      }

      OBDal.getInstance().flush();

      // 13. Return success response
      JSONObject responseJson = new JSONObject();
      responseJson.put("status", 0);
      responseJson.put("transactionId", transaction.getId());
      responseJson.put("eventId", cashupEvent.getId());
      responseJson.put("cashupId", cashUpId);
      response.getWriter().write(responseJson.toString());

    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in ProcessCashManagement WebService", cause);
      try {
        OBDal.getInstance().rollbackAndClose();
      } catch (Exception rollbackEx) {
        log.error("Error during rollback", rollbackEx);
      }
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error processing cash management: " + cause.getMessage());
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
