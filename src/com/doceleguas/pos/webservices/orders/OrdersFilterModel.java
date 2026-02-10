/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.DELIVERY_DATE_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.DELIVERY_MODE_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.HAS_NEGATIVE_LINES_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.HAS_VERIFIED_RETURN_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.INVOICE_CREATED_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.IS_QUOTATION_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.ORDER_BASE_JOINS;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.ORDER_TYPE_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.PAID_AMOUNT_SQL;
import static com.doceleguas.pos.webservices.orders.OrderQueryHelper.STATUS_SQL;
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

  /** Default ORDER BY column when none specified or invalid */
  private static final String DEFAULT_SORT_COLUMN = "ord.created";
  private static final String DEFAULT_SORT_DIRECTION = "DESC";

  /**
   * Creates a native SQL query for orders based on the provided parameters.
   * 
   * <p>Uses <strong>compound keyset (cursor-based) pagination</strong> for efficient
   * server-side pagination with arbitrary ORDER BY columns. This is more efficient
   * than OFFSET-based pagination because PostgreSQL doesn't need to scan and discard rows.</p>
   * 
   * <h3>Compound Keyset Pagination</h3>
   * <p>The query always orders by two columns: the sort column (from {@code orderBy} parameter
   * or {@code ord.created DESC} by default) followed by {@code ord.c_order_id} in the same
   * direction as a tiebreaker. The cursor tracks two values ({@code __lastid} and
   * {@code __lastsortvalue}) and uses a compound WHERE condition to correctly skip
   * already-returned rows without losing or duplicating any records.</p>
   * 
   * <p>For DESC ordering:</p>
   * <pre>
   * WHERE (sortCol &lt; :lastSortValue
   *    OR (sortCol = :lastSortValue AND c_order_id &lt; :lastId))
   * ORDER BY sortCol DESC, c_order_id DESC
   * </pre>
   * 
   * <p>For ASC ordering:</p>
   * <pre>
   * WHERE (sortCol &gt; :lastSortValue
   *    OR (sortCol = :lastSortValue AND c_order_id &gt; :lastId))
   * ORDER BY sortCol ASC, c_order_id ASC
   * </pre>
   * 
   * @param jsonParams JSON object containing:
   *   <ul>
   *     <li>selectList: SQL SELECT columns (required)</li>
   *     <li>filters: JSONArray of {column, value} objects</li>
   *     <li>orderType: Order type filter (ORD, RET, LAY, etc.)</li>
   *     <li>limit: Maximum rows to return</li>
   *     <li>lastId: c_order_id of the last row from previous page (optional)</li>
   *     <li>lastSortValue: value of the sort column from the last row (optional, required with lastId)</li>
   *     <li>orderBy: Sort column + direction, e.g. "ord.created DESC" (optional, defaults to ord.created DESC)</li>
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
    String lastId = jsonParams.optString("lastId", null);
    String lastSortValue = jsonParams.optString("lastSortValue", null);
    
    // Parse and resolve the ORDER BY column and direction
    String orderByParam = jsonParams.optString("orderBy", "").trim();
    String sortColumn = DEFAULT_SORT_COLUMN;
    String sortDirection = DEFAULT_SORT_DIRECTION;
    
    if (!orderByParam.isEmpty()) {
      String sanitized = sanitizeOrderBy(orderByParam);
      // Extract column and direction from sanitized orderBy (e.g. "ord.created DESC")
      String[] parts = sanitized.trim().split("\\s+");
      if (parts.length >= 1 && !parts[0].isEmpty()) {
        sortColumn = parts[0];
        if (parts.length >= 2 && parts[1].toUpperCase().matches("ASC|DESC")) {
          sortDirection = parts[1].toUpperCase();
        }
      }
    }
    
    // Resolve computed property aliases in the sort column (e.g. @status -> SQL expression)
    String sortColumnSql = replaceComputedProperties(sortColumn);
    
    boolean isDesc = "DESC".equals(sortDirection);
    boolean hasCursor = lastId != null && !lastId.isEmpty() 
        && lastSortValue != null && !lastSortValue.isEmpty();
    
    // Build WHERE clause and collect filter parameters
    QueryComponents components = buildQueryComponents(jsonParams);
    
    // Build the SQL query
    // Always include __lastid and __lastsortvalue for compound cursor tracking
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ord.c_order_id AS __lastid, ");
    sql.append(sortColumnSql).append(" AS __lastsortvalue, ");
    sql.append(selectList);
    sql.append(" FROM c_order ord");
    sql.append(ORDER_BASE_JOINS);
    sql.append(components.whereClause);
    
    // Compound keyset pagination: skip rows already returned in previous pages
    if (hasCursor) {
      // For DESC: (col < :lastSortValue OR (col = :lastSortValue AND id < :lastId))
      // For ASC:  (col > :lastSortValue OR (col = :lastSortValue AND id > :lastId))
      String cmp = isDesc ? "<" : ">";
      sql.append(" AND (").append(sortColumnSql).append(" ").append(cmp).append(" :lastSortValue");
      sql.append(" OR (").append(sortColumnSql).append(" = :lastSortValue");
      sql.append(" AND ord.c_order_id ").append(cmp).append(" :lastId))");
    }
    
    // ORDER BY: sort column + c_order_id as tiebreaker, both in the same direction
    sql.append(" ORDER BY ").append(sortColumnSql).append(" ").append(sortDirection);
    sql.append(", ord.c_order_id ").append(sortDirection);
    
    sql.append(" LIMIT :limit");
    
    log.debug("OrdersFilterModel SQL: {}", sql.toString());
    
    // Create and configure the query
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    
    // Set parameters
    query.setParameter("clientId", components.clientId);
    query.setParameter("organizationId", components.organizationId);
    query.setParameter("limit", limit);
    
    if (hasCursor) {
      query.setParameter("lastId", lastId);
      query.setParameter("lastSortValue", lastSortValue);
    }
    
    // Set filter parameters
    for (FilterParam fp : components.filterParams) {
      query.setParameter(fp.name, fp.value);
    }
    
    return query;
  }
  
  /**
   * Creates a COUNT query to get total number of matching records without pagination.
   * 
   * <p>This is used for proper pagination support - the consumer needs to know
   * the total number of records that match the filters to implement lazy loading.</p>
   * 
   * @param jsonParams JSON object with filters (same as createQuery)
   * @return The total count of matching records
   * @throws JSONException if JSON parsing fails
   */
  public long getTotalCount(JSONObject jsonParams) throws JSONException {
    // Build WHERE clause and collect filter parameters
    QueryComponents components = buildQueryComponents(jsonParams);
    
    // Build the COUNT query (no ORDER BY, LIMIT, OFFSET needed)
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT COUNT(*) FROM c_order ord");
    sql.append(ORDER_BASE_JOINS);
    sql.append(components.whereClause);
    
    log.debug("OrderModel COUNT SQL: {}", sql.toString());
    
    // Create and configure the query
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    
    // Set parameters
    query.setParameter("clientId", components.clientId);
    query.setParameter("organizationId", components.organizationId);
    
    // Set filter parameters
    for (FilterParam fp : components.filterParams) {
      query.setParameter(fp.name, fp.value);
    }
    
    // Execute and return count
    Object result = query.uniqueResult();
    if (result instanceof Number) {
      return ((Number) result).longValue();
    }
    return 0L;
  }
  
  /**
   * Builds the common query components (WHERE clause, parameters) used by both
   * the data query and the count query.
   * 
   * @param jsonParams JSON object with filters and security parameters
   * @return QueryComponents containing WHERE clause and parameters
   * @throws JSONException if JSON parsing fails
   */
  private QueryComponents buildQueryComponents(JSONObject jsonParams) throws JSONException {
    String clientId = jsonParams.getString("client");
    String organizationId = jsonParams.getString("organization");
    
    // Parse filters
    JSONArray filters = jsonParams.optJSONArray("filters");
    String orderTypeFilter = null;
    
    // Build dynamic WHERE clause
    StringBuilder whereClause = new StringBuilder();
    List<FilterParam> filterParams = new ArrayList<>();
    
    // Base WHERE conditions
    whereClause.append(" WHERE ord.ad_client_id = :clientId");
    whereClause.append(" AND ord.ad_org_id = :organizationId");
    whereClause.append(" AND ord.em_obpos_isdeleted = 'N'");
    whereClause.append(" AND ord.em_obpos_applications_id IS NOT NULL");
    whereClause.append(" AND ord.docstatus NOT IN ('CJ', 'CA', 'NC', 'AE', 'ME')");
    
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
        } else if (isComputedProperty(column)) {
          // Computed properties: replace with their SQL expression
          String computedSql = getComputedPropertySql(column);
          whereClause.append(" AND ").append(computedSql).append(" = :").append(column);
          filterParams.add(new FilterParam(column, value));
        } else {
          // All other columns use equals
          whereClause.append(" AND ord.").append(column).append(" = :").append(column);
          filterParams.add(new FilterParam(column, value));
        }
      }
    }
    
    // Add order type specific conditions
    whereClause.append(getOrderTypeSql(orderTypeFilter));
    
    return new QueryComponents(whereClause.toString(), filterParams, clientId, organizationId);
  }
  
  /**
   * Container class for query components shared between data and count queries.
   */
  private static class QueryComponents {
    final String whereClause;
    final List<FilterParam> filterParams;
    final String clientId;
    final String organizationId;
    
    QueryComponents(String whereClause, List<FilterParam> filterParams, 
                    String clientId, String organizationId) {
      this.whereClause = whereClause;
      this.filterParams = filterParams;
      this.clientId = clientId;
      this.organizationId = organizationId;
    }
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
   * Checks if the given column name is a computed property.
   * 
   * <p>Computed properties are virtual columns calculated from SQL expressions
   * rather than actual database columns. They use the @alias pattern in selectList
   * but can also be used as filters without the @ prefix.</p>
   * 
   * @param column The column name to check (lowercase)
   * @return true if the column is a computed property
   */
  private boolean isComputedProperty(String column) {
    switch (column) {
      case "status":
      case "paidamount":
      case "invoicecreated":
      case "hasverifiedreturn":
      case "hasnegativelines":
      case "isquotation":
      case "deliverymode":
      case "deliverydate":
      // Note: ordertype is handled separately in the filter logic
        return true;
      default:
        return false;
    }
  }
  
  /**
   * Gets the SQL expression for a computed property.
   * 
   * <p>Returns the appropriate SQL constant from OrderQueryHelper
   * that corresponds to the computed property name.</p>
   * 
   * @param column The computed property name (lowercase)
   * @return The SQL expression for the computed property
   * @throws IllegalArgumentException if the column is not a valid computed property
   */
  private String getComputedPropertySql(String column) {
    switch (column) {
      case "status":
        return STATUS_SQL;
      case "paidamount":
        return PAID_AMOUNT_SQL;
      case "invoicecreated":
        return INVOICE_CREATED_SQL;
      case "hasverifiedreturn":
        return HAS_VERIFIED_RETURN_SQL;
      case "hasnegativelines":
        return HAS_NEGATIVE_LINES_SQL;
      case "isquotation":
        return IS_QUOTATION_SQL;
      case "deliverymode":
        return DELIVERY_MODE_SQL;
      case "deliverydate":
        return DELIVERY_DATE_SQL;
      case "ordertype":
        return ORDER_TYPE_SQL;
      default:
        throw new IllegalArgumentException("Unknown computed property: " + column);
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
