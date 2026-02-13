/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Utility class with shared SQL expressions and helper methods for Order queries.
 * 
 * <p>This class centralizes common functionality used by both {@link OrderModel} 
 * and {@link OrdersFilterModel} to avoid code duplication.</p>
 * 
 * <h3>Shared SQL Constants:</h3>
 * <ul>
 *   <li>{@link #DELIVERY_MODE_SQL} - Subquery to calculate delivery mode from order lines</li>
 *   <li>{@link #DELIVERY_DATE_SQL} - Subquery to calculate delivery date from order lines</li>
 *   <li>{@link #ORDER_TYPE_SQL} - CASE expression to determine order type</li>
 *   <li>{@link #ORDER_BASE_JOINS} - Common JOIN clauses for order queries</li>
 * </ul>
 * 
 * <h3>Shared Helper Methods:</h3>
 * <ul>
 *   <li>{@link #sanitizeSelectList(String)} - SQL injection prevention for SELECT</li>
 *   <li>{@link #sanitizeOrderBy(String)} - SQL injection prevention for ORDER BY</li>
 *   <li>{@link #replaceComputedProperties(String)} - Replace @alias with SQL expressions</li>
 *   <li>{@link #rowToJson(Map)} - Convert result row to JSONObject</li>
 * </ul>
 * 
 * @see OrderModel
 * @see OrdersFilterModel
 */
public final class OrderQueryHelper {
  
  // ============================================
  // Computed Property SQL Expressions
  // ============================================
  
  /**
   * SQL subquery to calculate deliveryMode from order lines.
   * Returns the maximum delivery mode, defaulting to 'PickAndCarry'.
   * Equivalent to PaidReceiptsFilterProperties.deliveryMode
   */
  public static final String DELIVERY_MODE_SQL = 
      "(SELECT COALESCE(MAX(ol.em_obrdm_delivery_mode), 'PickAndCarry') "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id "
      + "AND COALESCE(ol.em_obpos_isdeleted, 'N') = 'N')";
  
  /**
   * SQL subquery to calculate deliveryDate from order lines.
   * Returns the minimum combined delivery date/time using to_timestamp for PostgreSQL compatibility.
   * Equivalent to PaidReceiptsFilterProperties.deliveryDate
   */
  public static final String DELIVERY_DATE_SQL = 
      "(SELECT MIN(CASE "
      + "WHEN ol.em_obrdm_delivery_date IS NULL OR ol.em_obrdm_delivery_time IS NULL THEN NULL "
      + "ELSE to_timestamp("
      + "to_char(ol.em_obrdm_delivery_date, 'YYYY') || '-' || "
      + "to_char(ol.em_obrdm_delivery_date, 'MM') || '-' || "
      + "to_char(ol.em_obrdm_delivery_date, 'DD') || ' ' || "
      + "to_char(ol.em_obrdm_delivery_time, 'HH24') || ':' || "
      + "to_char(ol.em_obrdm_delivery_time, 'MI'), 'YYYY-MM-DD HH24:MI') "
      + "END) "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id)";
  
  /**
   * SQL CASE expression to determine orderType.
   * Equivalent to PaidReceiptsFilterProperties.getOrderType() default case.
   * 
   * <p>Order types:</p>
   * <ul>
   *   <li>RET - Return (doctype.isreturn = 'Y')</li>
   *   <li>QT - Quotation (doctype.docsubtypeso = 'OB')</li>
   *   <li>LAY - Layaway (ord.em_obpos_islayaway = 'Y')</li>
   *   <li>ORD - Regular Order (default)</li>
   * </ul>
   */
  public static final String ORDER_TYPE_SQL = 
      "(CASE "
      + "WHEN doctype.isreturn = 'Y' THEN 'RET' "
      + "WHEN doctype.docsubtypeso = 'OB' THEN 'QT' "
      + "WHEN COALESCE(ord.em_obpos_islayaway, 'N') = 'Y' THEN 'LAY' "
      + "ELSE 'ORD' END)";
  
  /**
   * SQL subquery to calculate paidAmount from payment schedules.
   * Returns the sum of paid amounts for the order.
   * HQL equivalent: (select coalesce(sum(fps.paidAmount), 0) from FIN_Payment_Schedule fps where fps.order = ord)
   */
  public static final String PAID_AMOUNT_SQL = 
      "(SELECT COALESCE(SUM(fps.paidamt), 0) "
      + "FROM fin_payment_schedule fps "
      + "WHERE fps.c_order_id = ord.c_order_id)";
  
  /**
   * SQL CASE expression to determine order status.
   * Calculates status based on document type, cancellation, and payment state.
   * 
   * <p>Status values:</p>
   * <ul>
   *   <li>Refunded - Return document type</li>
   *   <li>UnderEvaluation - Quotation document (sOSubType = 'OB')</li>
   *   <li>Cancelled - Order is cancelled</li>
   *   <li>UnPaid - grandTotal > 0 and no payments</li>
   *   <li>PartiallyPaid - grandTotal > 0 and partial payment</li>
   *   <li>Paid - Fully paid (default)</li>
   * </ul>
   */
  public static final String STATUS_SQL = 
      "(CASE "
      + "WHEN doctype.isreturn = 'Y' THEN 'Refunded' "
      + "WHEN doctype.docsubtypeso = 'OB' THEN 'Under Evaluation' "
      + "WHEN ord.iscancelled = 'Y' THEN 'Cancelled' "
      + "WHEN ord.grandtotal <= 0 THEN 'Paid' "
      + "ELSE (SELECT CASE "
      + "WHEN COALESCE(SUM(fps.paidamt), 0) = 0 THEN 'UnPaid' "
      + "WHEN COALESCE(SUM(fps.paidamt), 0) < ord.grandtotal THEN 'Partially Paid' "
      + "ELSE 'Paid' END "
      + "FROM fin_payment_schedule fps WHERE fps.c_order_id = ord.c_order_id) "
      + "END)";
  
  /**
   * SQL EXISTS expression to check if invoice was created.
   * Checks if any invoice line exists linked to this order's lines with simplified invoice sequence.
   */
  public static final String INVOICE_CREATED_SQL = 
      "(EXISTS(SELECT 1 FROM c_invoiceline il "
      + "JOIN c_invoice i ON il.c_invoice_id = i.c_invoice_id "
      + "JOIN c_orderline ol ON il.c_orderline_id = ol.c_orderline_id "
      + "WHERE ol.c_order_id = ord.c_order_id "
      + "AND i.em_obpos_sequencename = 'simplifiedinvoiceslastassignednum'))";
  
  /**
   * SQL expression to check if order has verified returns.
   * Checks if any order line has a goods shipment line linked to a return order line.
   */
  public static final String HAS_VERIFIED_RETURN_SQL = 
      "(SELECT CASE WHEN COUNT(ordLine.c_orderline_id) > 0 THEN true ELSE false END "
      + "FROM c_orderline ordLine "
      + "JOIN m_inoutline iol ON ordLine.m_inoutline_id = iol.m_inoutline_id "
      + "JOIN c_orderline retOrdLine ON iol.c_orderline_id = retOrdLine.c_orderline_id "
      + "WHERE retOrdLine.c_order_id = ord.c_order_id)";
  
  /**
   * SQL CASE expression to check if order has negative quantity lines.
   * Returns true if any order line has orderedQuantity < 0.
   */
  public static final String HAS_NEGATIVE_LINES_SQL = 
      "(CASE WHEN EXISTS (SELECT 1 FROM c_orderline ordLine "
      + "WHERE ordLine.c_order_id = ord.c_order_id "
      + "AND ordLine.qtyordered < 0) THEN true ELSE false END)";
  
  /**
   * SQL expression to check if order is a quotation (has quotation reference).
   * Returns true if ord.quotation_id is not null.
   */
  public static final String IS_QUOTATION_SQL = 
      "(CASE WHEN ord.quotation_id IS NOT NULL THEN true ELSE false END)";
  
  /**
   * Common JOIN clauses for order queries.
   * Joins c_order with related tables commonly needed in order queries.
   */
  public static final String ORDER_BASE_JOINS = 
      " LEFT JOIN obpos_applications obpos ON ord.em_obpos_applications_id = obpos.obpos_applications_id"
      + " LEFT JOIN ad_org org ON ord.ad_org_id = org.ad_org_id"
      + " LEFT JOIN ad_org trxorg ON obpos.ad_org_id = trxorg.ad_org_id"
      + " LEFT JOIN c_bpartner bp ON ord.c_bpartner_id = bp.c_bpartner_id"
      + " LEFT JOIN ad_user salesrep ON ord.salesrep_id = salesrep.ad_user_id"
      + " LEFT JOIN c_doctype doctype ON ord.c_doctypetarget_id = doctype.c_doctype_id";
  
  // ============================================
  // Individual JOIN clauses (for conditional inclusion)
  // ============================================
  
  private static final String JOIN_OBPOS = 
      " LEFT JOIN obpos_applications obpos ON ord.em_obpos_applications_id = obpos.obpos_applications_id";
  private static final String JOIN_ORG = 
      " LEFT JOIN ad_org org ON ord.ad_org_id = org.ad_org_id";
  private static final String JOIN_TRXORG = 
      " LEFT JOIN ad_org trxorg ON obpos.ad_org_id = trxorg.ad_org_id";
  private static final String JOIN_BP = 
      " LEFT JOIN c_bpartner bp ON ord.c_bpartner_id = bp.c_bpartner_id";
  private static final String JOIN_SALESREP = 
      " LEFT JOIN ad_user salesrep ON ord.salesrep_id = salesrep.ad_user_id";
  private static final String JOIN_DOCTYPE = 
      " LEFT JOIN c_doctype doctype ON ord.c_doctypetarget_id = doctype.c_doctype_id";
  
  // ============================================
  // Conditional JOIN Builder
  // ============================================
  
  /**
   * Builds a JOIN clause including only the JOINs whose table aliases are referenced
   * by the provided SQL fragments (SELECT list, WHERE clause, ORDER BY, etc.).
   * 
   * <p>This avoids joining tables that are never referenced in the query,
   * significantly reducing I/O — especially for COUNT queries where only
   * the WHERE clause matters.</p>
   * 
   * @param sqlParts One or more SQL text fragments to analyze for alias references
   * @return A string of LEFT JOIN clauses, including only those needed
   */
  public static String buildConditionalJoins(String... sqlParts) {
    StringBuilder combined = new StringBuilder();
    for (String part : sqlParts) {
      if (part != null) {
        combined.append(" ").append(part).append(" ");
      }
    }
    String sql = combined.toString().toLowerCase();
    
    StringBuilder joins = new StringBuilder();
    
    boolean needsObpos = usesAlias(sql, "obpos");
    boolean needsTrxorg = usesAlias(sql, "trxorg");
    boolean needsOrg = usesAlias(sql, "org");
    boolean needsBp = usesAlias(sql, "bp");
    boolean needsSalesrep = usesAlias(sql, "salesrep");
    boolean needsDoctype = usesAlias(sql, "doctype");
    
    // trxorg depends on obpos (trxorg joins through obpos table)
    if (needsTrxorg) {
      needsObpos = true;
    }
    
    if (needsObpos) joins.append(JOIN_OBPOS);
    if (needsOrg) joins.append(JOIN_ORG);
    if (needsTrxorg) joins.append(JOIN_TRXORG);
    if (needsBp) joins.append(JOIN_BP);
    if (needsSalesrep) joins.append(JOIN_SALESREP);
    if (needsDoctype) joins.append(JOIN_DOCTYPE);
    
    return joins.toString();
  }
  
  /**
   * Checks if the SQL text references a specific table alias (e.g. "bp.", "org.").
   * Uses word-boundary matching to avoid false positives — for example,
   * "trxorg.name" will NOT match alias "org".
   */
  private static boolean usesAlias(String sqlLower, String alias) {
    Pattern p = Pattern.compile("(?<![a-zA-Z0-9_])" + alias + "\\.");
    return p.matcher(sqlLower).find();
  }
  
  // ============================================
  // Private Constructor (Utility Class)
  // ============================================
  
  /**
   * Private constructor to prevent instantiation.
   * This is a utility class with only static members.
   */
  private OrderQueryHelper() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }
  
  // ============================================
  // Sanitization Methods
  // ============================================
  
  /**
   * Sanitizes the SELECT list to prevent SQL injection.
   * Removes dangerous SQL keywords while preserving valid column expressions.
   * 
   * @param selectList Raw SELECT list from request
   * @return Sanitized SELECT list
   */
  public static String sanitizeSelectList(String selectList) {
    // Remove dangerous keywords
    String regex = "(?i)\\b(update|delete|drop|insert|truncate|alter|exec|execute)\\b";
    return selectList.replaceAll(regex, "").trim().replaceAll(" +", " ");
  }
  
  /**
   * Sanitizes the ORDER BY clause to prevent SQL injection.
   * 
   * @param orderBy Raw ORDER BY clause from request
   * @return Sanitized ORDER BY clause
   */
  public static String sanitizeOrderBy(String orderBy) {
    if (orderBy == null || orderBy.isEmpty()) {
      return "ord.created DESC";
    }
    // Only allow alphanumeric, dots, underscores, spaces, commas, and asc/desc
    return orderBy.replaceAll("[^a-zA-Z0-9_.,\\s]", "").trim();
  }
  
  // ============================================
  // Computed Properties Replacement
  // ============================================
  
  /**
   * Replaces computed property aliases with their SQL expressions.
   * 
   * <p>This method allows clients to request computed properties using simple aliases
   * like "@deliveryMode", "@deliveryDate", "@orderType" instead of complex SQL subqueries.
   * The aliases are replaced with their corresponding SQL expressions before query execution.</p>
   * 
   * <p>Supported computed properties:</p>
   * <ul>
   *   <li>{@code @deliveryMode} - Maximum delivery mode from order lines (default: 'PickAndCarry')</li>
   *   <li>{@code @deliveryDate} - Minimum delivery date/time from order lines</li>
   *   <li>{@code @orderType} - Calculated order type: 'ORD', 'RET', 'LAY', or 'QT'</li>
   *   <li>{@code @paidAmount} - Sum of paid amounts from payment schedules</li>
   *   <li>{@code @status} - Order status: Refunded, UnderEvaluation, Cancelled, UnPaid, PartiallyPaid, Paid</li>
   *   <li>{@code @invoiceCreated} - Boolean: true if simplified invoice exists for this order</li>
   *   <li>{@code @hasVerifiedReturn} - Boolean: true if order has verified returns</li>
   *   <li>{@code @hasNegativeLines} - Boolean: true if order has lines with negative quantity</li>
   *   <li>{@code @isQuotation} - Boolean: true if order has quotation reference</li>
   * </ul>
   * 
   * <p>Example selectList:</p>
   * <pre>
   * ord.documentno as "documentNo", @orderType as "orderType", @status as "status", @paidAmount as "paidAmount"
   * </pre>
   * 
   * @param selectList The SELECT list potentially containing computed property aliases
   * @return The SELECT list with aliases replaced by SQL expressions
   */
  public static String replaceComputedProperties(String selectList) {
    String result = selectList;
    
    // Replace @deliveryMode with the subquery
    result = result.replaceAll("(?i)@deliveryMode", DELIVERY_MODE_SQL);
    
    // Replace @deliveryDate with the subquery
    result = result.replaceAll("(?i)@deliveryDate", DELIVERY_DATE_SQL);
    
    // Replace @orderType with the CASE expression
    result = result.replaceAll("(?i)@orderType", ORDER_TYPE_SQL);
    
    // Replace @paidAmount with the subquery
    result = result.replaceAll("(?i)@paidAmount", PAID_AMOUNT_SQL);
    
    // Replace @status with the CASE expression
    result = result.replaceAll("(?i)@status", STATUS_SQL);
    
    // Replace @invoiceCreated with the EXISTS expression
    result = result.replaceAll("(?i)@invoiceCreated", INVOICE_CREATED_SQL);
    
    // Replace @hasVerifiedReturn with the subquery
    result = result.replaceAll("(?i)@hasVerifiedReturn", HAS_VERIFIED_RETURN_SQL);
    
    // Replace @hasNegativeLines with the CASE expression
    result = result.replaceAll("(?i)@hasNegativeLines", HAS_NEGATIVE_LINES_SQL);
    
    // Replace @isQuotation with the CASE expression
    result = result.replaceAll("(?i)@isQuotation", IS_QUOTATION_SQL);
    
    return result;
  }
  
  // ============================================
  // Result Conversion
  // ============================================
  
  /**
   * Converts a result row Map to a JSON object.
   * Handles null values by converting them to JSONObject.NULL.
   * 
   * @param rowMap Map of column names to values from the query result
   * @return JSONObject with the row data
   * @throws JSONException if JSON construction fails
   */
  public static JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException {
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
}
