/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;

/**
 * Native SQL query builder for Terminal configuration data. Returns only the requested fields based
 * on the original Terminal.java HQL.
 */
public class TerminalModel {

  private static final Logger log = LogManager.getLogger();

  public NativeQuery<?> createQuery(JSONObject jsonParams) {
    String terminalSearchKey = jsonParams.optString("terminalSearchKey", null);
    int sessionTimeout;
    try {
      String sessionShouldExpire = Preferences.getPreferenceValue("OBPOS_SessionTimeout", true,
          OBContext.getOBContext().getCurrentClient(),
          OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
          OBContext.getOBContext().getRole(), null);
      try {
        sessionTimeout = sessionShouldExpire == null ? 0 : Integer.parseInt(sessionShouldExpire);
      } catch (NumberFormatException nfe) {
        sessionTimeout = 0;
      }
    } catch (PropertyException e) {
      sessionTimeout = 0;
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ");
    sql.append(getDefaultSelect());
    sql.append(" " + sessionTimeout + " as \"sessionTimeout\"");
    sql.append(" FROM OBPOS_APPLICATIONS t ");
    sql.append(" LEFT JOIN AD_ORG o ON t.AD_ORG_ID = o.AD_ORG_ID ");
    sql.append(" LEFT JOIN AD_CLIENT c ON t.AD_CLIENT_ID = c.AD_CLIENT_ID ");
    sql.append(
        " LEFT JOIN OBPOS_TERMINALTYPE tt ON tt.OBPOS_TERMINALTYPE_ID = t.OBPOS_TERMINALTYPE_ID ");
    sql.append(" LEFT JOIN AD_ORGINFO oi ON oi.AD_ORG_ID = o.AD_ORG_ID ");
    sql.append(" LEFT JOIN C_LOCATION loc ON loc.C_LOCATION_ID = oi.C_LOCATION_ID ");
    // sql.append(" LEFT JOIN C_COUNTRY country ON country.C_COUNTRY_ID = loc.C_COUNTRY_ID ");
    // sql.append(" LEFT JOIN C_REGION region ON region.C_REGION_ID = loc.C_REGION_ID ");
    sql.append(" LEFT JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = oi.C_BPARTNER_ID ");
    sql.append(" LEFT JOIN M_PRICELIST pl ON pl.M_PRICELIST_ID = t.M_PRICELIST_ID ");
    sql.append(" LEFT JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = pl.C_CURRENCY_ID ");
    sql.append(" LEFT JOIN M_WAREHOUSE w ON w.AD_ORG_ID = o.AD_ORG_ID AND w.ISACTIVE = 'Y' ");
    sql.append(" WHERE t.VALUE = :terminalSearchKey ");
    sql.append(" AND  t.ISACTIVE = 'Y'");
    sql.append(" LIMIT 1");

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());

    if (terminalSearchKey != null && !terminalSearchKey.isEmpty()) {
      query.setParameter("terminalSearchKey", terminalSearchKey);
    }

    return query;
  }

  private String getDefaultSelect() {
    StringBuilder select = new StringBuilder();
    select.append("t.OBPOS_APPLICATIONS_ID AS \"id\", ");
    select.append("t.VALUE AS \"searchKey\", ");
    select.append("t.NAME AS \"_identifier\", ");
    select.append("o.AD_ORG_ID AS \"organization\", ");
    select.append("loc.C_COUNTRY_ID AS \"organizationCountryId\", ");
    select.append("loc.C_REGION_ID AS \"organizationRegionId\", ");
    // select.append("c.AD_CLIENT_ID AS client, ");
    // select.append("c.NAME AS client_identifier, ");
    select.append("curr.C_CURRENCY_ID AS \"currency\", ");
    select.append("curr.NAME AS \"currency_identifier\", ");
    select.append("curr.C_CURRSYMBOL AS \"symbol\", ");
    // select.append("curr.ISO_CODE AS iSOCode, ");
    // select.append("curr.STDPRECISION AS standardPrecision, ");
    // select.append("curr.PRICEPRICE AS pricePrecision, ");
    // select.append("curr.CURRSYMBOLATTHERIGHT AS currencySymbolAtTheRight, ");
    select.append("pl.M_PRICELIST_ID AS \"priceList\", ");
    // select.append("pl.NAME AS priceListName, ");
    // select.append("pl.PRICEINCLUDESTAX AS priceIncludesTax, ");
    select.append("w.M_WAREHOUSE_ID AS \"warehouse\", ");
    // select.append("loc.ADDRESS1 AS storeAddressLine1, ");
    // select.append("loc.ADDRESS2 AS storeAddressLine2, ");
    // select.append("loc.CITYNAME AS storeCity, ");
    // select.append("loc.POSTALCODE AS storePostalCode, ");
    // select.append("region.NAME AS storeRegion, ");
    // select.append("country.NAME AS storeCountry, ");
    select.append("oi.TAXID AS \"organizationTaxId\", ");
    // select.append("bp.NAME AS legalOrganizationName, ");
    select.append("bp.SOCIALNAME AS socialName, ");
    // select.append("t.ORDERDOCNO_PREFIX AS docNoPrefix, ");
    select.append("t.LASTASSIGNEDNUM AS \"lastDocumentNumber\", ");
    select.append("t.QUOTATIONDOCNO_PREFIX AS \"docNoPrefixQuotation\", ");
    select.append("t.QUOTATIONSDOCNO_PREFIX AS \"lastQuotationDocumentNumber\", ");
    select.append("t.RETURNDOCNO_PREFIX AS \"docNoPrefixReturn\", ");
    select.append("t.RETURNSLASTASSIGNEDNUM AS \"lastReturnDocumentNumber\", ");
    select.append("t.SIMPINVDOCNO_PREFIX AS \"docNoPrefixSimplifiedInvoice\", ");
    select.append("t.SIMPINVLASTASSIGNEDNUM AS \"lastSimplifiedInvoiceDocumentNumber\", ");
    select.append("t.FULLINVDOCNO_PREFIX AS \"docNoPrefixFullInvoice\", ");
    select.append("t.FULLINVLASTASSIGNEDNUM AS \"lastFullInvoiceDocumentNumber\", ");
    select.append("t.SIMPRETINVDOCNO_PREFIX AS \"docNoPrefixSimplifiedReturnInvoice\", ");
    select.append("t.SIMPRETINVLASTASSIGNEDNUM AS \"lastSimplifiedReturnInvoiceDocumentNumber\", ");
    select.append("t.FULLRETINVDOCNO_PREFIX AS \"docNoPrefixFullReturnInvoice\", ");
    select.append("t.FULLRETINVLASTASSIGNEDNUM AS \"lastFullReturnInvoiceDocumentNumber\", ");
    select
        .append("(COALESCE(t.C_BPARTNER_ID, o.EM_Obretco_C_Bpartner_ID)) AS \"businessPartner\", ");
    select.append("oi.EM_PHMDF_TICKETHEADER AS \"phmdfTicketheader\", ");
    select.append("oi.EM_PHMDF_TICKETFOOTER AS \"phmdfTicketfooter\", ");
    select.append("t.HARDWAREURL AS \"hardwareurl\", ");
    select.append("t.PRINTERTYPE AS \"printertype\", ");
    select.append("tt.ALLOWPAYONCREDIT AS \"allowpayoncredit\", ");
    return select.toString();
  }

  @SuppressWarnings("unchecked")
  public JSONObject rowToJson(Map<String, Object> rowMap) {
    JSONObject json = new JSONObject();

    try {
      for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
        String key = entry.getKey().toLowerCase();
        Object value = entry.getValue();

        if (value == null) {
          json.put(key, JSONObject.NULL);
        } else if (value instanceof Number) {
          json.put(key, value);
        } else if (value instanceof Boolean) {
          json.put(key, value);
        } else {
          json.put(key, value.toString());
        }
      }
    } catch (JSONException e) {
      log.error("Error building JSON from row", e);
    }

    return json;
  }

  public JSONArray getPayments(String terminalId) {
    JSONArray payments = new JSONArray();
//@formatter:off    
    String sql = "SELECT p.OBPOS_APP_PAYMENT_ID AS id, " 
        + "p.VALUE AS paymentValue, "
        + "p.NAME AS paymentName, " 
        + "p.ISACTIVE AS isActive, "
        + "p.COMMERCIALNAME AS commercialName, " 
        + "p.ALLOWVARIABLEAMOUNT AS allowVariableAmount, "
        + "curr.ISO_CODE AS isoCode, " 
        + "curr.C_CURRSYMBOL AS symbol, "
        + "curr.CURRSYMBOLATTHERIGHT AS currencySymbolAtTheRight, "
        + "f.CURRENTBALANCE AS currentBalance, "
        // "pg.OBPOS_PAYMENTGROUP_ID AS providerGroupId, " +
        // "pg.NAME AS providerGroupName, " +
        + "COALESCE(pmt.ISCASH, 'N') AS isCash, " 
        + "pmt.C_CURRENCY_ID AS currency, "
        + "COALESCE(pmt.ALLOWOVERPAYMENT, 'N') AS allowOverPayment " 
        + "FROM OBPOS_APP_PAYMENT p "
        + "LEFT JOIN OBPOS_APP_PAYMENT_TYPE pmt ON pmt.OBPOS_APP_PAYMENT_TYPE_ID = p.OBPOS_APP_PAYMENT_TYPE_ID "
        + "LEFT JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = COALESCE(pmt.C_CURRENCY_ID, (SELECT pl.C_CURRENCY_ID FROM M_PRICELIST pl WHERE pl.M_PRICELIST_ID = (SELECT t.M_PRICELIST_ID FROM OBPOS_APPLICATIONS t WHERE t.OBPOS_APPLICATIONS_ID = p.OBPOS_APPLICATIONS_ID))) "
        + "LEFT JOIN FIN_FINANCIAL_ACCOUNT f ON f.FIN_FINANCIAL_ACCOUNT_ID = p.FINANCIALACCOUNT "
        + "LEFT JOIN OBPOS_PAYMENTGROUP pg ON pg.OBPOS_PAYMENTGROUP_ID = pmt.OBPOS_PAYMENTGROUP_ID "
        + "WHERE p.OBPOS_APPLICATIONS_ID = :terminalId " 
        + "AND p.ISACTIVE = 'Y' "
        + "AND (pmt.ISACTIVE IS NULL OR pmt.ISACTIVE = 'Y') " 
        + "ORDER BY p.LINE, p.NAME";
//@formatter:on
    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("terminalId", terminalId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject payment = new JSONObject();
      try {
        payment.put("id", getStringValue(row[0]));
        payment.put("paymentValue", getStringValue(row[1]));
        payment.put("paymentName", getStringValue(row[2]));
        payment.put("isActive", getBooleanValue(row[3]));
        payment.put("commercialName", getStringValue(row[4]));
        payment.put("allowVariableAmount", getBooleanValue(row[5]));
        payment.put("isoCode", getStringValue(row[6]));
        payment.put("symbol", getStringValue(row[7]));
        payment.put("currencySymbolAtTheRight", getBooleanValue(row[8]));
        payment.put("currentBalance", getStringValue(row[9]));
        // payment.put("providerGroupId", getStringValue(row[10]));
        // payment.put("providerGroupName", getStringValue(row[11]));

        JSONObject paymentMethod = new JSONObject();
        paymentMethod.put("isCash", getBooleanValue(row[10]));
        paymentMethod.put("currency", getStringValue(row[11]));
        paymentMethod.put("allowOverPayment", getBooleanValue(row[12]));
        payment.put("paymentMethod", paymentMethod);

      } catch (JSONException e) {
        log.error("Error building payment JSON", e);
      }
      payments.put(payment);
    }

    return payments;
  }

  public JSONArray getPriceLists(String organizationId) {
    JSONArray priceLists = new JSONArray();
//@formatter:off
    String sql = "SELECT pl.M_PRICELIST_ID AS id, " 
        + "pl.NAME AS name, "
        + "pl.DESCRIPTION AS description, " 
        + "pl.PRICEINCLUDESTAX AS priceIncludesTax, "
        + "pl.ISDEFAULT AS isDefault, " 
        + "pl.ISACTIVE AS isActive, "
        + "pl.SALESPRICELIST AS salesPriceList, " 
        + "curr.C_CURRENCY_ID AS currency, "
        + "curr.NAME AS currency_identifier, " 
        + "curr.ISO_CODE AS iSOCode, "
        + "curr.C_CURRSYMBOL AS symbol " 
        + "FROM M_PRICELIST pl "
        + "LEFT JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = pl.C_CURRENCY_ID "
        + "WHERE pl.AD_ORG_ID IN (0, :organizationId) " 
        + "AND pl.ISACTIVE = 'Y' "
        + "AND pl.SALESPRICELIST = 'Y' " 
        + "ORDER BY pl.ISDEFAULT DESC, pl.NAME";
  //@formatter:on
    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("organizationId", organizationId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject pricelist = new JSONObject();
      try {
        pricelist.put("id", getStringValue(row[0]));
        pricelist.put("name", getStringValue(row[1]));
        pricelist.put("description", getStringValue(row[2]));
        pricelist.put("priceIncludesTax", getBooleanValue(row[3]));
        pricelist.put("isDefault", getBooleanValue(row[4]));
        pricelist.put("isActive", getBooleanValue(row[5]));
        pricelist.put("salesPriceList", getBooleanValue(row[6]));
        pricelist.put("currency", getStringValue(row[7]));
        pricelist.put("currency_identifier", getStringValue(row[8]));
        pricelist.put("iSOCode", getStringValue(row[9]));
        pricelist.put("symbol", getStringValue(row[10]));
      } catch (JSONException e) {
        log.error("Error building pricelist JSON", e);
      }
      priceLists.put(pricelist);
    }

    return priceLists;
  }

  public JSONArray getCurrencyPanel(String terminalId) {
    JSONArray denominations = new JSONArray();

    String sql = "SELECT cd.OBPOS_CURRENCYDENOMINATION_ID AS id, " + "cd.COINBILL AS coinBill, "
        + "cd.AMOUNT AS amount, " + "cd.ISACTIVE AS isActive, " + "cd.ISSHOW AS isShow "
        + "FROM OBPOS_CURRENCYPANEL cp "
        + "JOIN OBPOS_CURRENCYDENOMINATION cd ON cd.OBPOS_CURRENCYPANEL = cp.OBPOS_CURRENCYPANEL_ID "
        + "WHERE cp.AD_ORG_ID IN (SELECT AD_ORG_ID FROM OBPOS_APPLICATIONS WHERE OBPOS_APPLICATIONS_ID = :terminalId) "
        + "AND cd.ISACTIVE = 'Y' " + "ORDER BY cd.COINBILL DESC, cd.AMOUNT DESC";

    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("terminalId", terminalId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject denom = new JSONObject();
      try {
        denom.put("id", getStringValue(row[0]));
        denom.put("coinBill", getStringValue(row[1]));
        denom.put("amount", getStringValue(row[2]));
        denom.put("isActive", getBooleanValue(row[3]));
        denom.put("isShow", getBooleanValue(row[4]));
      } catch (JSONException e) {
        log.error("Error building denomination JSON", e);
      }
      denominations.put(denom);
    }

    return denominations;
  }

  public JSONArray getCashMgmtDepositEvents(String terminalId) {
    JSONArray events = new JSONArray();

    String sql = "SELECT ev.OBPOS_CASHMGMT_EVENT_ID AS id, " + "ev.NAME AS name, "
        + "ev.DESCRIPTION AS description " + "FROM OBPOS_CASHMGMT_EVENT ev "
        + "JOIN OBPOS_APPLICATIONS t ON t.AD_ORG_ID = ev.AD_ORG_ID "
        + "WHERE t.OBPOS_APPLICATIONS_ID = :terminalId " + "AND ev.EVENTTYPE = 'DEPOSIT' "
        + "AND ev.ISACTIVE = 'Y' " + "ORDER BY ev.SEQUENCE";

    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("terminalId", terminalId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject event = new JSONObject();
      try {
        event.put("id", getStringValue(row[0]));
        event.put("name", getStringValue(row[1]));
        event.put("description", getStringValue(row[2]));
      } catch (JSONException e) {
        log.error("Error building deposit event JSON", e);
      }
      events.put(event);
    }

    return events;
  }

  public JSONArray getCashMgmtDropEvents(String terminalId) {
    JSONArray events = new JSONArray();

    String sql = "SELECT ev.OBPOS_CASHMGMT_EVENT_ID AS id, " + "ev.NAME AS name, "
        + "ev.DESCRIPTION AS description " + "FROM OBPOS_CASHMGMT_EVENT ev "
        + "JOIN OBPOS_APPLICATIONS t ON t.AD_ORG_ID = ev.AD_ORG_ID "
        + "WHERE t.OBPOS_APPLICATIONS_ID = :terminalId " + "AND ev.EVENTTYPE = 'DROP' "
        + "AND ev.ISACTIVE = 'Y' " + "ORDER BY ev.SEQUENCE";

    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("terminalId", terminalId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject event = new JSONObject();
      try {
        event.put("id", getStringValue(row[0]));
        event.put("name", getStringValue(row[1]));
        event.put("description", getStringValue(row[2]));
      } catch (JSONException e) {
        log.error("Error building drop event JSON", e);
      }
      events.put(event);
    }

    return events;
  }

  public JSONArray getExchangeRates(String organizationId, String currencyId) {
    JSONArray rates = new JSONArray();

    String sql = "SELECT r.C_CONVERSION_RATE_ID AS id, " + "r.C_CURRENCY_ID AS toCurrencyId, "
        + "curr.ISO_CODE AS toCurrencyISOCode, " + "curr.C_CURRSYMBOL AS toCurrencySymbol, "
        + "r.RATE AS rate, " + "r.VALIDFROM AS validFrom " + "FROM C_CONVERSION_RATE r "
        + "JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = r.C_CURRENCY_ID "
        + "WHERE r.AD_ORG_ID IN (0, :organizationId) " + "AND r.C_CURRENCY_TO_ID = :currencyId "
        + "AND r.ISACTIVE = 'Y' " + "AND r.VALIDFROM <= CURRENT_DATE "
        + "ORDER BY r.VALIDFROM DESC";

    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("organizationId", organizationId);
    query.setParameter("currencyId", currencyId);

    List<Object[]> results = query.getResultList();

    for (Object[] row : results) {
      JSONObject rate = new JSONObject();
      try {
        rate.put("id", getStringValue(row[0]));
        rate.put("toCurrencyId", getStringValue(row[1]));
        rate.put("toCurrencyISOCode", getStringValue(row[2]));
        rate.put("toCurrencySymbol", getStringValue(row[3]));
        rate.put("rate", getStringValue(row[4]));
        rate.put("validFrom", getStringValue(row[5]));
      } catch (JSONException e) {
        log.error("Error building rate JSON", e);
      }
      rates.put(rate);
    }

    return rates;
  }

  public JSONObject getCurrency(String currencyId) {
    JSONObject currency = new JSONObject();

    String sql = "SELECT c.C_CURRENCY_ID AS \"id\", " + "c.ISO_CODE AS \"iSOCode\", "
        + "c.C_CURRSYMBOL AS \"symbol\", " + "c.STDPRECISION AS \"standardPrecision\", "
        + "c.PRICEPRICE AS \"pricePrecision\" " + "FROM C_CURRENCY c "
        + "WHERE c.C_CURRENCY_ID = :currencyId " + "AND c.ISACTIVE = 'Y'";

    @SuppressWarnings("unchecked")
    NativeQuery<Object[]> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setParameter("currencyId", currencyId);

    List<Object[]> results = query.getResultList();

    if (!results.isEmpty()) {
      Object[] row = results.get(0);
      try {
        currency.put("id", getStringValue(row[0]));
        currency.put("iSOCode", getStringValue(row[1]));
        currency.put("symbol", getStringValue(row[2]));
        currency.put("standardPrecision", getStringValue(row[3]));
        currency.put("pricePrecision", getStringValue(row[4]));
      } catch (JSONException e) {
        log.error("Error building currency JSON", e);
      }
    }

    return currency;
  }

  private String getStringValue(Object value) {
    return value != null ? value.toString() : null;
  }

  private Boolean getBooleanValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return "Y".equalsIgnoreCase(value.toString());
  }
}
