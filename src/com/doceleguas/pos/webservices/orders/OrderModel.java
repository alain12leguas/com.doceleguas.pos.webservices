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
}
