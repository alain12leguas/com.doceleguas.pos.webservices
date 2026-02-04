/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.mobile.core.model.HQLPropertyList;
import org.openbravo.mobile.core.model.ModelExtension;
import org.openbravo.mobile.core.model.ModelExtensionUtils;
import org.openbravo.retail.posterminal.ProcessHQLQueryValidated;

/**
 * Order filter that extends ProcessHQLQueryValidated.
 * Follows the same architectural pattern as PaidReceiptsFilter.
 * 
 * This class provides direct DAL access to query orders, eliminating the
 * HTTP overhead of the previous proxy implementation.
 * 
 * Properties are extensible via CDI by implementing ModelExtension with
 * the qualifier {@link #EXTENSION_QUALIFIER}.
 * 
 * Supported filters (via remoteFilters):
 * - id: Filter by order UUID
 * - documentNo: Filter by document number
 * - organization: Filter by organization name or ID
 * - orderDate: Filter by specific order date
 * - dateFrom/dateTo: Filter by date range
 * 
 * @see GetOrdersFilterProperties
 * @see GetOrdersWebService
 */
public class GetOrdersFilter extends ProcessHQLQueryValidated {

  private static final Logger log = LogManager.getLogger();

  /**
   * CDI qualifier for extending order filter properties.
   * Other modules can add properties by creating a class that:
   * 1. Extends ModelExtension
   * 2. Is annotated with @Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)
   * 3. Implements getHQLProperties() returning additional HQLProperty objects
   */
  public static final String EXTENSION_QUALIFIER = "GetOrdersFilter_Extension";

  @Inject
  @Any
  @Qualifier(EXTENSION_QUALIFIER)
  private Instance<ModelExtension> extensions;

  @Override
  protected List<HQLPropertyList> getHqlProperties(JSONObject jsonsent) {
    List<HQLPropertyList> propertiesList = new ArrayList<>();
    HQLPropertyList orderProperties = ModelExtensionUtils.getPropertyExtensions(extensions,
        jsonsent);
    propertiesList.add(orderProperties);
    return propertiesList;
  }

  @Override
  protected String getFilterEntity() {
    return "OrderFilter";
  }

  @Override
  protected List<String> getQueryValidated(JSONObject jsonsent) throws JSONException {
    HQLPropertyList orderProperties = ModelExtensionUtils.getPropertyExtensions(extensions,
        jsonsent);

    String orderTypeHql = getOrderTypeHql(jsonsent);
    boolean isPayOpenTicket = "payOpenTickets".equals(getFilterValue(jsonsent, "orderType"));

    StringBuilder hql = new StringBuilder();
    hql.append("SELECT ").append(orderProperties.getHqlSelect());
    hql.append(" FROM Order AS ord");
    hql.append(" LEFT JOIN ord.obposApplications AS obpos");
    hql.append(" LEFT JOIN ord.organization AS org");
    hql.append(" LEFT JOIN obpos.organization AS trxOrg");
    hql.append(" LEFT JOIN ord.businessPartner AS bp");
    hql.append(" LEFT JOIN ord.salesRepresentative AS salesRep");
    hql.append(" LEFT JOIN ord.documentType AS docType");
    hql.append(" WHERE $filtersCriteria AND $hqlCriteria");
    hql.append(orderTypeHql);
    hql.append(" AND ord.client.id = $clientId");
    hql.append(" AND ord.$orgId");
    hql.append(" AND ord.obposIsDeleted = false");
    hql.append(" AND ord.obposApplications IS NOT NULL");
    hql.append(" AND ord.documentStatus NOT IN ('CJ', 'CA', 'NC', 'AE', 'ME')");
    if (!isPayOpenTicket) {
      hql.append(" AND (ord.documentStatus <> 'CL' OR ord.iscancelled = true)");
    }
    hql.append(addCustomWhereClause(jsonsent));
    hql.append(" $orderByCriteria");

    log.debug("GetOrdersFilter HQL: {}", hql.toString());

    return Arrays.asList(hql.toString());
  }

  /**
   * Generates the orderType-specific WHERE clause.
   */
  private String getOrderTypeHql(JSONObject jsonsent) {
    String orderType = getFilterValue(jsonsent, "orderType");
    switch (orderType) {
      case "RET":
        return " AND ord.documentType.return = true";
      case "LAY":
        return " AND ord.obposIslayaway = true";
      case "ORD":
        return " AND ord.documentType.return = false AND ord.documentType.sOSubType <> 'OB' AND ord.obposIslayaway = false";
      case "verifiedReturns":
        return " AND ord.documentType.return = false AND ord.documentType.sOSubType <> 'OB' AND ord.obposIslayaway = false AND cancelledorder IS NULL";
      case "payOpenTickets":
        return " AND ord.grandTotalAmount > 0 AND ord.documentType.sOSubType <> 'OB' AND ord.documentStatus <> 'CL'";
      default:
        return "";
    }
  }

  /**
   * Gets a filter value from remoteFilters by column name.
   */
  public static String getFilterValue(JSONObject jsonsent, String column) {
    try {
      if (jsonsent.has("remoteFilters")) {
        JSONArray filters = jsonsent.getJSONArray("remoteFilters");
        for (int i = 0; i < filters.length(); i++) {
          JSONObject filter = filters.getJSONObject(i);
          JSONArray columns = filter.getJSONArray("columns");
          for (int j = 0; j < columns.length(); j++) {
            if (column.equals(columns.getString(j))) {
              return filter.optString("value", "");
            }
          }
        }
      }
    } catch (JSONException e) {
      log.debug("Error getting filter value for column: {}", column);
    }
    return "";
  }

  /**
   * Adds custom WHERE clauses based on filter parameters.
   * 
   * @param jsonsent The JSON request containing parameters
   * @return Additional WHERE clause string
   * @throws JSONException if JSON parsing fails
   */
  protected String addCustomWhereClause(JSONObject jsonsent) throws JSONException {
    StringBuilder where = new StringBuilder();

    // Check for date parameters in parameters object
    if (jsonsent.has("parameters")) {
      JSONObject params = jsonsent.getJSONObject("parameters");
      
      // Handle exact date filter
      if (params.has("orderDate")) {
        where.append(" AND ord.orderDate = :orderDate");
      }
      
      // Handle date range filter
      if (params.has("dateFrom") && params.has("dateTo")) {
        where.append(" AND ord.orderDate >= :dateFrom AND ord.orderDate <= :dateTo");
      }
    }

    return where.toString();
  }

  @Override
  protected Map<String, Object> getParameterValues(JSONObject jsonsent) throws JSONException {
    Map<String, Object> params = super.getParameterValues(jsonsent);

    // Add date parameters if present
    if (jsonsent.has("parameters")) {
      JSONObject jsonParams = jsonsent.getJSONObject("parameters");
      
      // Handle exact date parameter
      if (jsonParams.has("orderDate")) {
        if (params == null) {
          params = new java.util.HashMap<>();
        }
        params.put("orderDate", java.sql.Date.valueOf(jsonParams.getString("orderDate")));
      }
      
      // Handle date range parameters
      if (jsonParams.has("dateFrom")) {
        if (params == null) {
          params = new java.util.HashMap<>();
        }
        params.put("dateFrom", java.sql.Date.valueOf(jsonParams.getString("dateFrom")));
      }
      if (jsonParams.has("dateTo")) {
        if (params == null) {
          params = new java.util.HashMap<>();
        }
        params.put("dateTo", java.sql.Date.valueOf(jsonParams.getString("dateTo")));
      }
    }

    return params;
  }

  @Override
  protected boolean mustHaveRemoteFilters() {
    // Require at least one filter to execute the query
    return true;
  }

  @Override
  protected String messageWhenNoFilters() {
    return "At least one filter is required (id, documentNo, organization, orderDate, etc.)";
  }

  private static final List<String> SUPPORTED_FILTERS = Arrays.asList(
      "id", "documentNo", "organization", "orgSearchKey", "orgName",
      "orderDate", "dateFrom", "dateTo", "businessPartner", "orderType", "totalamount");

  @Override
  protected boolean hasRelevantRemoteFilters(JSONArray remoteFilters) {
    try {
      for (int i = 0; i < remoteFilters.length(); i++) {
        JSONObject filter = remoteFilters.getJSONObject(i);
        if (filter.has("columns")) {
          JSONArray columns = filter.getJSONArray("columns");
          for (int j = 0; j < columns.length(); j++) {
            if (SUPPORTED_FILTERS.contains(columns.getString(j))) {
              return true;
            }
          }
        }
      }
    } catch (JSONException e) {
      log.error("Error parsing remoteFilters", e);
    }
    return false;
  }
}
