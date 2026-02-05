/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import java.util.ArrayList;
import java.util.List;
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
 * Native SQL query model for Orders (C_Order table).
 * 
 * This class constructs and executes native SQL queries against the C_Order table,
 * following the same pattern as OCProduct and other Model implementations used by
 * MasterDataWebService.
 * 
 * @see com.doceleguas.pos.webservices.GetOrdersFilter
 * @see com.doceleguas.pos.webservices.Model
 */
public class OrdersFilterModel extends Model {

  private static final Logger log = LogManager.getLogger();
  
  // ============================================
  // Computed Property SQL Expressions
  // ============================================
  
  /**
   * SQL subquery to calculate deliveryMode from order lines.
   * Returns the maximum delivery mode, defaulting to 'PickAndCarry'.
   * Equivalent to PaidReceiptsFilterProperties.deliveryMode
   */
  private static final String DELIVERY_MODE_SQL = 
      "(SELECT COALESCE(MAX(ol.em_obrdm_delivery_mode), 'PickAndCarry') "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id "
      + "AND COALESCE(ol.em_obpos_isdeleted, 'N') = 'N')";
  
  /**
   * SQL subquery to calculate deliveryDate from order lines.
   * Returns the minimum combined delivery date/time.
   * Equivalent to PaidReceiptsFilterProperties.deliveryDate
   */
  private static final String DELIVERY_DATE_SQL = 
      "(SELECT MIN(CASE "
      + "WHEN ol.em_obrdm_delivery_date IS NULL OR ol.em_obrdm_delivery_time IS NULL THEN NULL "
      + "ELSE (ol.em_obrdm_delivery_date + ol.em_obrdm_delivery_time) "
      + "END) "
      + "FROM c_orderline ol "
      + "WHERE ol.c_order_id = ord.c_order_id)";
  
  /**
   * SQL CASE expression to determine orderType.
   * Equivalent to PaidReceiptsFilterProperties.getOrderType() default case.
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
   * Creates a native SQL query for orders based on the provided parameters.
   * 
   * @param jsonParams JSON object containing:
   *   <ul>
   *     <li>selectList: SQL SELECT columns (required)</li>
   *     <li>filters: JSONArray of {column, value} objects</li>
   *     <li>orderType: Order type filter (ORD, RET, LAY, etc.)</li>
   *     <li>limit: Maximum rows to return</li>
   *     <li>offset: Rows to skip</li>
   *     <li>orderBy: ORDER BY clause</li>
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
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String orderBy = jsonParams.optString("orderBy", "ord.created DESC");
    String clientId = jsonParams.getString("client");
    String organizationId = jsonParams.getString("organization");
    
    // Parse filters
    JSONArray filters = jsonParams.optJSONArray("filters");
    String orderTypeFilter = null;
    
    // Build dynamic WHERE clause
    StringBuilder whereClause = new StringBuilder();
    List<FilterParam> filterParams = new ArrayList<>();
    
    if (filters != null) {
      for (int i = 0; i < filters.length(); i++) {
        JSONObject filter = filters.getJSONObject(i);
        String column = filter.getString("column").toLowerCase();
        String value = filter.getString("value");
        
        if (column.equals("ordertype")) {
          // Special handling for orderType - processed separately
          orderTypeFilter = value;
        } else if (column.equals("documentno")) {
          // documentno uses ILIKE (contains)
          whereClause.append(" AND UPPER(ord.documentno) LIKE UPPER(:").append(column).append(")");
          filterParams.add(new FilterParam(column, "%" + value + "%"));
        } else if (column.equals("dateordered")) {
          // Date uses equals with DATE cast
          whereClause.append(" AND ord.dateordered = :").append(column);
          filterParams.add(new FilterParam(column, java.sql.Date.valueOf(value)));
        } else if (column.equals("datefrom")) {
          // Date range - from
          whereClause.append(" AND ord.dateordered >= :").append(column);
          filterParams.add(new FilterParam(column, java.sql.Date.valueOf(value)));
        } else if (column.equals("dateto")) {
          // Date range - to
          whereClause.append(" AND ord.dateordered <= :").append(column);
          filterParams.add(new FilterParam(column, java.sql.Date.valueOf(value)));
        } else {
          // All other columns use equals
          whereClause.append(" AND ord.").append(column).append(" = :").append(column);
          filterParams.add(new FilterParam(column, value));
        }
      }
    }
    
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
    sql.append(" AND ord.ad_org_id IN (SELECT ad_org_id FROM ad_org WHERE ad_org_id = :organizationId)");
    sql.append(" AND ord.em_obpos_isdeleted = 'N'");
    sql.append(" AND ord.em_obpos_applications_id IS NOT NULL");
    sql.append(" AND ord.docstatus NOT IN ('CJ', 'CA', 'NC', 'AE', 'ME')");
    
    // Add order type specific conditions
    sql.append(getOrderTypeSql(orderTypeFilter));
    
    // Add dynamic filters
    sql.append(whereClause);
    
    // Add ORDER BY and pagination
    sql.append(" ORDER BY ").append(sanitizeOrderBy(orderBy));
    sql.append(" LIMIT :limit OFFSET :offset");
    
    log.debug("OrderModel SQL: {}", sql.toString());
    
    // Create and configure the query
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    
    // Set parameters
    query.setParameter("clientId", clientId);
    query.setParameter("organizationId", organizationId);
    query.setParameter("limit", limit);
    query.setParameter("offset", offset);
    
    // Set filter parameters
    for (FilterParam fp : filterParams) {
      query.setParameter(fp.name, fp.value);
    }
    
    return query;
  }
  
  /**
   * Generates SQL WHERE clause fragment for order type filtering.
   * 
   * <p>Translates logical order types to their SQL equivalents using
   * actual database column names from C_Order and C_DocType tables.</p>
   * 
   * @param orderType The order type filter value
   * @return SQL WHERE clause fragment (starts with " AND ")
   */
  private String getOrderTypeSql(String orderType) {
    if (orderType == null || orderType.isEmpty()) {
      // Default: exclude closed orders unless cancelled
      return " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
    }
    
    switch (orderType) {
      case "RET":
        // Returns: document type is marked as return
        return " AND doctype.isreturn = 'Y'"
            + " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
      case "LAY":
        // Layaways
        return " AND ord.em_obpos_islayaway = 'Y'"
            + " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
      case "ORD":
        // Regular orders: not return, not quotation, not layaway
        return " AND doctype.isreturn = 'N'"
            + " AND COALESCE(doctype.docsubtypeso, '') <> 'OB'"
            + " AND COALESCE(ord.em_obpos_islayaway, 'N') = 'N'"
            + " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
      case "verifiedReturns":
        // Verified returns: regular orders without cancelled order reference
        return " AND doctype.isreturn = 'N'"
            + " AND COALESCE(doctype.docsubtypeso, '') <> 'OB'"
            + " AND COALESCE(ord.em_obpos_islayaway, 'N') = 'N'"
            + " AND ord.em_obpos_cancelledorder_id IS NULL"
            + " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
      case "payOpenTickets":
        // Open tickets with positive amount
        return " AND ord.grandtotal > 0"
            + " AND COALESCE(doctype.docsubtypeso, '') <> 'OB'"
            + " AND ord.docstatus <> 'CL'";
      default:
        return " AND (ord.docstatus <> 'CL' OR ord.iscancelled = 'Y')";
    }
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
   * </ul>
   * 
   * <p>Example selectList:</p>
   * <pre>
   * ord.documentno as "documentNo", @orderType as "orderType", @deliveryMode as "deliveryMode"
   * </pre>
   * 
   * @param selectList The SELECT list potentially containing computed property aliases
   * @return The SELECT list with aliases replaced by SQL expressions
   */
  private String replaceComputedProperties(String selectList) {
    String result = selectList;
    
    // Replace @deliveryMode with the subquery
    result = result.replaceAll("(?i)@deliveryMode", DELIVERY_MODE_SQL);
    
    // Replace @deliveryDate with the subquery
    result = result.replaceAll("(?i)@deliveryDate", DELIVERY_DATE_SQL);
    
    // Replace @orderType with the CASE expression
    result = result.replaceAll("(?i)@orderType", ORDER_TYPE_SQL);
    
    return result;
  }
  
  /**
   * Sanitizes the ORDER BY clause to prevent SQL injection.
   * 
   * @param orderBy Raw ORDER BY clause from request
   * @return Sanitized ORDER BY clause
   */
  private String sanitizeOrderBy(String orderBy) {
    if (orderBy == null || orderBy.isEmpty()) {
      return "ord.created DESC";
    }
    // Only allow alphanumeric, dots, underscores, spaces, commas, and asc/desc
    return orderBy.replaceAll("[^a-zA-Z0-9_.,\\s]", "").trim();
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
  
  /**
   * Internal class to hold filter parameter name and value.
   */
  private static class FilterParam {
    final String name;
    final Object value;
    
    FilterParam(String name, Object value) {
      this.name = name;
      this.value = value;
    }
  }
}
