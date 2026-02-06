/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.ORDER_BASE_JOINS;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.replaceComputedProperties;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.rowToJson;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.sanitizeOrderBy;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.sanitizeSelectList;

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
    sql.append(ORDER_BASE_JOINS);
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
