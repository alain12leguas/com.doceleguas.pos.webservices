/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.service.OBDal;

import com.doceleguas.pos.webservices.Model;

/**
 * Native SQL query model for retrieving a single Order by ID.
 * 
 * This class constructs and executes native SQL queries to retrieve a single order
 * from the C_Order table, following the same pattern as PaidReceipts.
 * 
 * @see com.doceleguas.pos.webservices.GetOrder
 * @see com.doceleguas.pos.webservices.Model
 */
public class OrderModel extends Model {

  private static final Logger log = LogManager.getLogger();
  
  // ============================================
  // Computed Property SQL Expressions
  // ============================================
  
  /**
   * SQL subquery to calculate deliveryMode from order lines.
   * Returns the maximum delivery mode, defaulting to 'PickAndCarry'.
   */
  private static final String DELIVERY_MODE_SQL = 
      "(SELECT COALESCE(MAX(ol.em_obrdm_delivery_mode), 'PickAndCarry') "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id "
      + "AND COALESCE(ol.em_obpos_isdeleted, 'N') = 'N')";
  
  /**
   * SQL subquery to calculate deliveryDate from order lines.
   * Returns the minimum combined delivery date/time.
   */
  private static final String DELIVERY_DATE_SQL = 
      "(SELECT MIN(CASE "
      + "WHEN ol.em_obrdm_delivery_date IS NULL OR ol.em_obrdm_deliverytime IS NULL THEN NULL "
      + "ELSE (ol.em_obrdm_delivery_date + ol.em_obrdm_deliverytime) "
      + "END) "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id)";
  
  /**
   * SQL CASE expression to determine orderType.
   */
  private static final String ORDER_TYPE_SQL = 
      "(CASE "
      + "WHEN doctype.isreturn = 'Y' THEN 'RET' "
      + "WHEN doctype.docsubtypeso = 'OB' THEN 'QT' "
      + "WHEN COALESCE(ord.em_obpos_islayaway, 'N') = 'Y' THEN 'LAY' "
      + "ELSE 'ORD' END)";

  @Override
  public String getName() {
    return "Order";
  }

  /**
   * Creates a native SQL query for a single order based on the provided parameters.
   * 
   * @param jsonParams JSON object containing:
   *   <ul>
   *     <li>selectList: SQL SELECT columns (required)</li>
   *     <li>orderId: Order UUID to retrieve (required if no documentNo)</li>
   *     <li>documentNo: Document number to search (required if no orderId)</li>
   *     <li>client: Client ID for security filter</li>
   *     <li>organization: Organization ID for security filter</li>
   *   </ul>
   * @return Configured NativeQuery ready for execution
   * @throws JSONException if JSON parsing fails
   */
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {
    // Parse parameters
    String selectList = sanitizeSelectList(jsonParams.getString("selectList"));
    
    // Replace computed property aliases with their SQL expressions
    selectList = replaceComputedProperties(selectList);
    String clientId = jsonParams.getString("client");
    String organizationId = jsonParams.getString("organization");
    String orderId = jsonParams.optString("orderId", null);
    String documentNo = jsonParams.optString("documentNo", null);
    
    // Build the SQL query
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ").append(selectList);
    sql.append(" FROM c_order ord");
    sql.append(" LEFT JOIN obpos_applications obpos ON ord.em_obpos_applications_id = obpos.obpos_applications_id");
    sql.append(" LEFT JOIN ad_org org ON ord.ad_org_id = org.ad_org_id");
    sql.append(" LEFT JOIN ad_org trxorg ON obpos.ad_org_id = trxorg.ad_org_id");
    sql.append(" LEFT JOIN c_bpartner bp ON ord.c_bpartner_id = bp.c_bpartner_id");
    sql.append(" LEFT JOIN ad_user salesrep ON ord.salesrep_id = salesrep.ad_user_id");
    sql.append(" LEFT JOIN c_doctype doctype ON ord.c_doctypetarget_id = doctype.c_doctype_id");
    sql.append(" WHERE ord.ad_client_id = :clientId");
    
    // Add order identifier filter
    if (orderId != null && !orderId.isEmpty()) {
      sql.append(" AND ord.c_order_id = :orderId");
    } else if (documentNo != null && !documentNo.isEmpty()) {
      sql.append(" AND ord.documentno = :documentNo");
    }
    
    // Limit to 1 result
    sql.append(" LIMIT 1");
    
    log.debug("OrderModel SQL: {}", sql.toString());
    
    // Create and configure the query
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    
    // Set parameters
    query.setParameter("clientId", clientId);
    
    if (orderId != null && !orderId.isEmpty()) {
      query.setParameter("orderId", orderId);
    } else if (documentNo != null && !documentNo.isEmpty()) {
      query.setParameter("documentNo", documentNo);
    }
    
    return query;
  }
  
  /**
   * Sanitizes the SELECT list to prevent SQL injection.
   * Removes dangerous SQL keywords while preserving valid column expressions.
   * 
   * @param selectList Raw SELECT list from request
   * @return Sanitized SELECT list
   */
  private String sanitizeSelectList(String selectList) {
    // Remove dangerous keywords
    String regex = "(?i)\\b(update|delete|drop|insert|truncate|alter|exec|execute)\\b";
    return selectList.replaceAll(regex, "").trim().replaceAll(" +", " ");
  }
  
  /**
   * Replaces computed property aliases with their SQL expressions.
   * Supports @orderType, @deliveryMode, @deliveryDate.
   * 
   * @param selectList The SELECT list potentially containing computed property aliases
   * @return The SELECT list with aliases replaced by SQL expressions
   */
  private String replaceComputedProperties(String selectList) {
    String result = selectList;
    result = result.replaceAll("(?i)@deliveryMode", DELIVERY_MODE_SQL);
    result = result.replaceAll("(?i)@deliveryDate", DELIVERY_DATE_SQL);
    result = result.replaceAll("(?i)@orderType", ORDER_TYPE_SQL);
    return result;
  }
  
  /**
   * Converts a result row Map to a JSON object.
   * Handles null values and maintains column name casing.
   * 
   * @param rowMap Map of column names to values from the query result
   * @return JSONObject with the row data
   * @throws JSONException if JSON construction fails
   */
  @Override
  public JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException {
    JSONObject json = new JSONObject();
    for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
      Object value = entry.getValue();
      // Handle null values
      if (value == null) {
        json.put(entry.getKey(), JSONObject.NULL);
      } else {
        json.put(entry.getKey(), value);
      }
    }
    return json;
  }
  
  // ============================================
  // Computed Array Properties
  // ============================================
  
  /**
   * Retrieves the order lines (receiptLines) for the given order.
   * Each line includes taxes and promotions arrays.
   * Equivalent to PaidReceipts.receiptLines.
   * 
   * @param orderId The order UUID
   * @return JSONArray of order lines with taxes and promotions
   * @throws JSONException if JSON construction fails
   */
  @SuppressWarnings("unchecked")
  public JSONArray getReceiptLines(String orderId) throws JSONException {
    JSONArray receiptLines = new JSONArray();
    
    // Query order lines
    String sqlLines = "SELECT "
        + "ol.c_orderline_id as \"lineId\", "
        + "ol.line as \"lineNo\", "
        + "ol.m_product_id as \"product\", "
        + "p.name as \"productName\", "
        + "p.value as \"productSearchKey\", "
        + "ol.qtyordered as \"quantity\", "
        + "ol.priceactual as \"unitPrice\", "
        + "ol.pricelist as \"listPrice\", "
        + "ol.linenetamt as \"lineNetAmount\", "
        + "ol.line_gross_amount as \"lineGrossAmount\", "
        + "ol.c_tax_id as \"tax\", "
        + "t.name as \"taxName\", "
        + "t.rate as \"taxRate\", "
        + "ol.description as \"description\", "
        + "ol.m_warehouse_id as \"warehouse\", "
        + "ol.m_attributesetinstance_id as \"attributeSetInstance\", "
        + "ol.em_obpos_isdeleted as \"isDeleted\", "
        + "ol.c_return_reason_id as \"returnReason\", "
        + "rr.name as \"returnReasonName\", "
        + "ol.c_order_discount_id as \"orderDiscount\", "
        + "ol.discount as \"discount\", "
        + "(SELECT COALESCE(ol.em_obrdm_delivery_mode, 'PickAndCarry')) as \"deliveryMode\", "
        + "ol.em_obrdm_delivery_date as \"deliveryDate\" "
        + "FROM c_orderline ol "
        + "LEFT JOIN m_product p ON ol.m_product_id = p.m_product_id "
        + "LEFT JOIN c_tax t ON ol.c_tax_id = t.c_tax_id "
        + "LEFT JOIN c_return_reason rr ON ol.c_return_reason_id = rr.c_return_reason_id "
        + "WHERE ol.c_order_id = :orderId "
        + "AND COALESCE(ol.em_obpos_isdeleted, 'N') = 'N' "
        + "ORDER BY ol.line";
    
    NativeQuery<?> queryLines = OBDal.getInstance().getSession().createNativeQuery(sqlLines);
    queryLines.setParameter("orderId", orderId);
    queryLines.setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    
    java.util.List<?> lines = queryLines.list();
    for (Object lineObj : lines) {
      java.util.Map<String, Object> lineMap = (java.util.Map<String, Object>) lineObj;
      JSONObject lineJson = rowToJson(lineMap);
      
      String lineId = lineJson.optString("lineId");
      if (lineId != null && !lineId.isEmpty()) {
        // Add taxes for this line
        JSONArray taxes = getLineTaxes(lineId);
        lineJson.put("taxes", taxes);
        
        // Add promotions for this line
        JSONArray promotions = getLinePromotions(lineId);
        lineJson.put("promotions", promotions);
      }
      
      receiptLines.put(lineJson);
    }
    
    return receiptLines;
  }
  
  /**
   * Retrieves taxes for a specific order line.
   * Equivalent to PaidReceipts taxes per line.
   * 
   * @param lineId The order line UUID
   * @return JSONArray of taxes for the line
   * @throws JSONException if JSON construction fails
   */
  @SuppressWarnings("unchecked")
  private JSONArray getLineTaxes(String lineId) throws JSONException {
    JSONArray taxes = new JSONArray();
    
    String sqlTaxes = "SELECT "
        + "olt.c_tax_id as \"taxId\", "
        + "t.name as \"identifier\", "
        + "olt.taxamt as \"taxAmount\", "
        + "olt.taxbaseamt as \"taxableAmount\", "
        + "t.rate as \"taxRate\", "
        + "t.doctaxamount as \"docTaxAmount\", "
        + "olt.line as \"lineNo\", "
        + "t.cascade as \"cascade\", "
        + "t.isspecialtax as \"isSpecialTax\" "
        + "FROM c_orderlinetax olt "
        + "JOIN c_tax t ON olt.c_tax_id = t.c_tax_id "
        + "WHERE olt.c_orderline_id = :lineId "
        + "ORDER BY olt.line";
    
    NativeQuery<?> queryTaxes = OBDal.getInstance().getSession().createNativeQuery(sqlTaxes);
    queryTaxes.setParameter("lineId", lineId);
    queryTaxes.setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    
    java.util.List<?> taxResults = queryTaxes.list();
    for (Object taxObj : taxResults) {
      java.util.Map<String, Object> taxMap = (java.util.Map<String, Object>) taxObj;
      taxes.put(rowToJson(taxMap));
    }
    
    return taxes;
  }
  
  /**
   * Retrieves promotions (discounts) for a specific order line.
   * Equivalent to PaidReceipts promotions per line.
   * 
   * @param lineId The order line UUID
   * @return JSONArray of promotions for the line
   * @throws JSONException if JSON construction fails
   */
  @SuppressWarnings("unchecked")
  private JSONArray getLinePromotions(String lineId) throws JSONException {
    JSONArray promotions = new JSONArray();
    
    String sqlPromotions = "SELECT "
        + "olo.m_offer_id as \"ruleId\", "
        + "o.name as \"name\", "
        + "o.print_name as \"printName\", "
        + "o.m_offer_type_id as \"discountType\", "
        + "olo.totalamt as \"totalAmount\", "
        + "olo.displayedtotalamt as \"displayedTotalAmount\", "
        + "olo.em_obdisc_qtyoffer as \"qtyOffer\", "
        + "olo.em_obdisc_identifier as \"discIdentifier\", "
        + "olo.line as \"lineNo\" "
        + "FROM c_orderline_offer olo "
        + "JOIN m_offer o ON olo.m_offer_id = o.m_offer_id "
        + "WHERE olo.c_orderline_id = :lineId "
        + "ORDER BY olo.line";
    
    NativeQuery<?> queryPromotions = OBDal.getInstance().getSession().createNativeQuery(sqlPromotions);
    queryPromotions.setParameter("lineId", lineId);
    queryPromotions.setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    
    java.util.List<?> promoResults = queryPromotions.list();
    for (Object promoObj : promoResults) {
      java.util.Map<String, Object> promoMap = (java.util.Map<String, Object>) promoObj;
      JSONObject promoJson = rowToJson(promoMap);
      
      // Use printName if available, otherwise use name
      String name = promoJson.optString("printName");
      if (name == null || name.isEmpty() || "null".equals(name)) {
        name = promoJson.optString("name");
      }
      promoJson.put("name", name);
      
      // Check if hidden (displayedTotalAmount is 0)
      Object displayedAmt = promoMap.get("displayedTotalAmount");
      boolean hidden = displayedAmt == null || 
          (displayedAmt instanceof Number && ((Number) displayedAmt).doubleValue() == 0);
      promoJson.put("hidden", hidden);
      
      promotions.put(promoJson);
    }
    
    return promotions;
  }
  
  /**
   * Retrieves payments (receiptPayments) for the given order.
   * Equivalent to PaidReceipts.receiptPayments.
   * 
   * @param orderId The order UUID
   * @return JSONArray of payments for the order
   * @throws JSONException if JSON construction fails
   */
  @SuppressWarnings("unchecked")
  public JSONArray getReceiptPayments(String orderId) throws JSONException {
    JSONArray receiptPayments = new JSONArray();
    
    String sqlPayments = "SELECT "
        + "fp.fin_payment_id as \"paymentId\", "
        + "fp.documentno as \"documentNo\", "
        + "fp.paymentdate as \"paymentDate\", "
        + "fp.amount as \"amount\", "
        + "SUM(pd.amount) as \"paymentAmount\", "
        + "fp.finacc_txn_amount as \"financialTransactionAmount\", "
        + "fa.fin_financial_account_id as \"account\", "
        + "fa.name as \"accountName\", "
        + "pm.name as \"paymentMethod\", "
        + "pm.fin_paymentmethod_id as \"paymentMethodId\", "
        + "c.iso_code as \"isocode\", "
        + "fp.em_obpos_app_cashup_id as \"cashup\", "
        + "fp.em_obpos_applications_id as \"posTerminal\", "
        + "pos.value as \"posTerminalSearchKey\", "
        + "fp.description as \"comment\", "
        + "fp.em_obpos_paymentdata as \"paymentData\", "
        + "fp.fin_rev_payment_id as \"reversedPayment\" "
        + "FROM fin_payment fp "
        + "JOIN fin_payment_detail pd ON fp.fin_payment_id = pd.fin_payment_id "
        + "JOIN fin_payment_scheduledetail psd ON pd.fin_payment_detail_id = psd.fin_payment_detail_id "
        + "JOIN fin_payment_schedule ps ON psd.fin_payment_schedule_order = ps.fin_payment_schedule_id "
        + "JOIN fin_financial_account fa ON fp.fin_financial_account_id = fa.fin_financial_account_id "
        + "JOIN fin_paymentmethod pm ON fp.fin_paymentmethod_id = pm.fin_paymentmethod_id "
        + "JOIN c_currency c ON fa.c_currency_id = c.c_currency_id "
        + "LEFT JOIN obpos_applications pos ON fp.em_obpos_applications_id = pos.obpos_applications_id "
        + "WHERE ps.c_order_id = :orderId "
        + "GROUP BY fp.fin_payment_id, fp.documentno, fp.paymentdate, fp.amount, "
        + "fp.finacc_txn_amount, fa.fin_financial_account_id, fa.name, pm.name, "
        + "pm.fin_paymentmethod_id, c.iso_code, fp.em_obpos_app_cashup_id, "
        + "fp.em_obpos_applications_id, pos.value, fp.description, fp.em_obpos_paymentdata, "
        + "fp.fin_rev_payment_id "
        + "ORDER BY fp.documentno";
    
    NativeQuery<?> queryPayments = OBDal.getInstance().getSession().createNativeQuery(sqlPayments);
    queryPayments.setParameter("orderId", orderId);
    queryPayments.setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    
    java.util.List<?> paymentResults = queryPayments.list();
    for (Object paymentObj : paymentResults) {
      java.util.Map<String, Object> paymentMap = (java.util.Map<String, Object>) paymentObj;
      JSONObject paymentJson = rowToJson(paymentMap);
      
      // Parse paymentData JSON string if present
      String paymentDataStr = paymentJson.optString("paymentData");
      if (paymentDataStr != null && !paymentDataStr.isEmpty() && !"null".equals(paymentDataStr)) {
        try {
          paymentJson.put("paymentData", new JSONObject(paymentDataStr));
        } catch (JSONException e) {
          // Keep as string if not valid JSON
          log.debug("Could not parse paymentData as JSON: {}", paymentDataStr);
        }
      }
      
      // Add flag for reversed payments
      if (paymentJson.has("reversedPaymentId") && 
          !JSONObject.NULL.equals(paymentJson.get("reversedPaymentId"))) {
        paymentJson.put("isReversed", true);
      }
      
      receiptPayments.put(paymentJson);
    }
    
    return receiptPayments;
  }
  
  /**
   * Retrieves tax summary (receiptTaxes) for the given order.
   * Equivalent to PaidReceipts.receiptTaxes.
   * 
   * @param orderId The order UUID
   * @return JSONArray of tax summary for the order
   * @throws JSONException if JSON construction fails
   */
  @SuppressWarnings("unchecked")
  public JSONArray getReceiptTaxes(String orderId) throws JSONException {
    JSONArray receiptTaxes = new JSONArray();
    
    String sqlTaxes = "SELECT "
        + "ot.c_tax_id as \"taxid\", "
        + "t.rate as \"rate\", "
        + "ot.taxbaseamt as \"net\", "
        + "ot.taxamt as \"amount\", "
        + "t.name as \"name\", "
        + "(ot.taxbaseamt + ot.taxamt) as \"gross\", "
        + "t.cascade as \"cascade\", "
        + "t.doctaxamount as \"docTaxAmount\", "
        + "ot.line as \"lineNo\", "
        + "t.c_taxcategory_id as \"taxBase\", "
        + "t.isspecialtax as \"isSpecialTax\" "
        + "FROM c_ordertax ot "
        + "JOIN c_tax t ON ot.c_tax_id = t.c_tax_id "
        + "WHERE ot.c_order_id = :orderId "
        + "ORDER BY ot.line";
    
    NativeQuery<?> queryTaxes = OBDal.getInstance().getSession().createNativeQuery(sqlTaxes);
    queryTaxes.setParameter("orderId", orderId);
    queryTaxes.setResultTransformer(org.hibernate.transform.Transformers.ALIAS_TO_ENTITY_MAP);
    
    java.util.List<?> taxResults = queryTaxes.list();
    for (Object taxObj : taxResults) {
      java.util.Map<String, Object> taxMap = (java.util.Map<String, Object>) taxObj;
      receiptTaxes.put(rowToJson(taxMap));
    }
    
    return receiptTaxes;
  }
}
