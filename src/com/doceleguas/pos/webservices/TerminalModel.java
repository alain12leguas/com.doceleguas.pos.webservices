/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.POSUtils;

/**
 * Native SQL query builder for Terminal configuration data. Returns only the requested fields based
 * on the original Terminal.java HQL.
 */
public class TerminalModel {

  private static final Logger log = LogManager.getLogger();

  public NativeQuery<?> createQuery(JSONObject jsonParams) {
    String terminalSearchKey = jsonParams.optString("terminalSearchKey", null);
    String priceListId = null;
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
    OBPOSApplications pOSTerminal = getTerminal(terminalSearchKey);
    org.openbravo.model.pricing.pricelist.PriceList priceList = org.openbravo.retail.posterminal.POSUtils
        .getPriceListByTerminalId(pOSTerminal.getId());

    final int lastDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "lastassignednum", false)
        .intValue();
    final int lastQuotationDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "quotationslastassignednum", false)
        .intValue();
    final int lastReturnDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "returnslastassignednum", false)
        .intValue();
    final int lastFullInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "fullinvoiceslastassignednum", true)
        .intValue();
    final int lastFullReturnInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "fullreturninvoiceslastassignednum", true)
        .intValue();
    final int lastSimplifiedInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "simplifiedinvoiceslastassignednum", true)
        .intValue();
    final int lastSimplifiedReturnInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(pOSTerminal, "simplifiedreturninvoiceslastassignednum",
            true)
        .intValue();

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT ");
    sql.append(getDefaultSelect());
    sql.append("," + lastDocumentNumber + " as \"lastDocumentNumber\", ");
    sql.append(lastQuotationDocumentNumber + " as \"lastQuotationDocumentNumber\", ");
    sql.append(lastReturnDocumentNumber + " as \"lastReturnDocumentNumber\", ");
    sql.append(lastFullInvoiceDocumentNumber + " as \"lastFullInvoiceDocumentNumber\", ");
    sql.append(lastSimplifiedReturnInvoiceDocumentNumber
        + " as \"lastSimplifiedReturnInvoiceDocumentNumber\", ");
    sql.append(
        lastSimplifiedInvoiceDocumentNumber + " as \"lastSimplifiedInvoiceDocumentNumber\", ");
    sql.append(
        lastFullReturnInvoiceDocumentNumber + " as \"lastFullReturnInvoiceDocumentNumber\", ");
    sql.append(sessionTimeout + " as \"sessionTimeout\"");
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
    sql.append(" LEFT JOIN M_PRICELIST pl ON pl.M_PRICELIST_ID = :priceListId ");
    sql.append(" LEFT JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = pl.C_CURRENCY_ID ");
    sql.append(" LEFT JOIN M_WAREHOUSE w ON w.AD_ORG_ID = o.AD_ORG_ID AND w.ISACTIVE = 'Y' ");
    sql.append(" LEFT JOIN AD_IMAGE img ON img.AD_IMAGE_ID = oi.YOUR_COMPANY_DOCUMENT_IMAGE ");
    sql.append(" WHERE t.VALUE = :terminalSearchKey ");
    sql.append(" AND  t.ISACTIVE = 'Y'");
    sql.append(" LIMIT 1");

    @SuppressWarnings("deprecation")
    NativeQuery<?> query = (NativeQuery<?>) OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql.toString())
        .setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

    if (terminalSearchKey != null && !terminalSearchKey.isEmpty()) {
      query.setParameter("terminalSearchKey", terminalSearchKey);
    }
    query.setParameter("priceListId", priceList.getId());

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
    select.append("o.NAME AS \"organizationName\", ");
    select.append("curr.C_CURRENCY_ID AS \"currency\", ");
    select.append("curr.ISO_CODE AS \"currencyISOCode\", ");
    select.append("curr.CURSYMBOL AS \"symbol\", ");
    select.append("curr.STDPRECISION AS \"currencyStandardPrecision\", ");
    select.append("curr.Issymbolrightside AS \"currencySymbolAtTheRight\", ");
    select.append("pl.M_PRICELIST_ID AS \"priceList\", ");
    select.append("pl.NAME AS \"pricelistName\", ");
    select.append("pl.IsTaxIncluded AS \"priceIncludesTax\", ");
    select.append("w.M_WAREHOUSE_ID AS \"warehouse\", ");
    // select.append("loc.ADDRESS1 AS storeAddressLine1, ");
    // select.append("loc.ADDRESS2 AS storeAddressLine2, ");
    // select.append("loc.CITYNAME AS storeCity, ");
    // select.append("loc.POSTALCODE AS storePostalCode, ");
    // select.append("region.NAME AS storeRegion, ");
    // select.append("country.NAME AS storeCountry, ");
    select.append("oi.TAXID AS \"organizationTaxId\", ");
    // select.append("bp.NAME AS legalOrganizationName, ");
    // select.append("bp.SOCIALNAME AS socialName, ");
    select
        .append("(COALESCE(t.C_BPARTNER_ID, o.EM_Obretco_C_Bpartner_ID)) AS \"businessPartner\", ");
    // select.append("oi.EM_PHMDF_TICKETHEADER AS \"phmdfTicketheader\", ");
    // select.append("oi.EM_PHMDF_TICKETFOOTER AS \"phmdfTicketfooter\", ");
    select.append("t.HARDWAREURL AS \"hardwareurl\", ");
    select.append("t.PRINTERTYPE AS \"printertype\", ");
    select.append("tt.ALLOWPAYONCREDIT AS \"allowpayoncredit\", ");
    select.append("oi.ISCASHVAT AS \"cashVat\", ");
    select.append("o.EM_OBPOS_CURRENCY_FORMAT AS \"currencyFormat\", ");
    select.append("TRIM(CONCAT(COALESCE(loc.ADDRESS1, ''), "
        + "CASE WHEN loc.CITY IS NOT NULL THEN ', ' || loc.CITY ELSE '' END, "
        + "CASE WHEN loc.POSTAL IS NOT NULL THEN ' ' || loc.POSTAL ELSE '' END)) "
        + "AS \"organizationAddressIdentifier\", ");
    select.append("img.BINARYDATA AS \"organizationImageData\", ");
    select.append("img.MIMETYPE AS \"organizationImageMime\" ");
    return select.toString();
  }

  public JSONObject buildTerminalJson(Map<String, Object> rowMap) {
    JSONObject terminal = new JSONObject();
    try {
      // Direct fields
      terminal.put("id", getStringValue(rowMap.get("id")));
      terminal.put("organization", getStringValue(rowMap.get("organization")));
      terminal.put("organizationCountryId", getStringValue(rowMap.get("organizationCountryId")));
      terminal.put("organizationRegionId", getStringValue(rowMap.get("organizationRegionId")));
      terminal.put("organization$_identifier", getStringValue(rowMap.get("organizationName")));
      terminal.put("organizationTaxId", getStringValue(rowMap.get("organizationTaxId")));
      terminal.put("organizationAddressIdentifier",
          getStringValue(rowMap.get("organizationAddressIdentifier")));
      terminal.put("priceIncludesTax", getBooleanValue(rowMap.get("priceIncludesTax")));

      // terminal.terminal
      JSONObject innerTerminal = new JSONObject();
      innerTerminal.put("id", getStringValue(rowMap.get("id")));
      innerTerminal.put("organization", getStringValue(rowMap.get("organization")));
      innerTerminal.put("organizationCountryId",
          getStringValue(rowMap.get("organizationCountryId")));
      innerTerminal.put("organizationRegionId", getStringValue(rowMap.get("organizationRegionId")));
      innerTerminal.put("_identifier", getStringValue(rowMap.get("_identifier")));
      innerTerminal.put("searchKey", getStringValue(rowMap.get("searchKey")));
      innerTerminal.put("organization$_identifier", getStringValue(rowMap.get("organizationName")));
      innerTerminal.put("businessPartner", getStringValue(rowMap.get("businessPartner")));
      innerTerminal.put("allowpayoncredit", getBooleanValue(rowMap.get("allowpayoncredit")));
      innerTerminal.put("sessionTimeout", getIntValue(rowMap.get("sessionTimeout")));
      innerTerminal.put("hardwareurl", getStringValue(rowMap.get("hardwareurl")));
      innerTerminal.put("lastDocumentNumber", getIntValue(rowMap.get("lastDocumentNumber")));
      innerTerminal.put("lastQuotationDocumentNumber",
          getIntValue(rowMap.get("lastQuotationDocumentNumber")));
      innerTerminal.put("lastReturnDocumentNumber",
          getIntValue(rowMap.get("lastReturnDocumentNumber")));
      innerTerminal.put("lastFullInvoiceDocumentNumber",
          getIntValue(rowMap.get("lastFullInvoiceDocumentNumber")));
      innerTerminal.put("lastSimplifiedReturnInvoiceDocumentNumber",
          getIntValue(rowMap.get("lastSimplifiedReturnInvoiceDocumentNumber")));
      innerTerminal.put("lastSimplifiedInvoiceDocumentNumber",
          getIntValue(rowMap.get("lastSimplifiedInvoiceDocumentNumber")));
      innerTerminal.put("lastFullReturnInvoiceDocumentNumber",
          getIntValue(rowMap.get("lastFullReturnInvoiceDocumentNumber")));
      terminal.put("terminal", innerTerminal);

      // terminal.currency
      JSONObject currency = new JSONObject();
      currency.put("id", getStringValue(rowMap.get("currency")));
      currency.put("iSOCode", getStringValue(rowMap.get("currencyISOCode")));
      currency.put("symbol", getStringValue(rowMap.get("symbol")));
      Object stdPrec = rowMap.get("currencyStandardPrecision");
      currency.put("standardPrecision", stdPrec instanceof Number ? stdPrec : JSONObject.NULL);
      currency.put("currencySymbolAtTheRight",
          getBooleanValue(rowMap.get("currencySymbolAtTheRight")));
      currency.put("_identifier", getStringValue(rowMap.get("currencyISOCode")));
      terminal.put("currency", currency);

      // terminal.pricelist
      JSONObject pricelist = new JSONObject();
      pricelist.put("id", getStringValue(rowMap.get("priceList")));
      pricelist.put("name", getStringValue(rowMap.get("pricelistName")));
      pricelist.put("priceIncludesTax", getBooleanValue(rowMap.get("priceIncludesTax")));
      pricelist.put("currency$_identifier", getStringValue(rowMap.get("currencyISOCode")));
      terminal.put("pricelist", pricelist);

      // Organization image
      Object imageData = rowMap.get("organizationImageData");
      Object imageMime = rowMap.get("organizationImageMime");
      if (imageData instanceof byte[] && imageMime != null) {
        terminal.put("organizationImage",
            "data:" + imageMime + ";base64," + Base64.encodeBase64String((byte[]) imageData));
      } else {
        terminal.put("organizationImage", JSONObject.NULL);
      }
    } catch (JSONException e) {
      log.error("Error building terminal JSON", e);
    }
    return terminal;
  }

  public JSONArray getPayments(String terminalId) {
    JSONArray payments = new JSONArray();

//@formatter:off
    String sql = "SELECT p.OBPOS_APP_PAYMENT_ID AS \"id\", "
        + "p.VALUE AS \"paymentValue\", "
        + "p.ISACTIVE AS \"isActive\", "
        + "p.name AS \"commercialName\", "
        + "p.ALLOWVARIABLEAMOUNT AS \"allowVariableAmount\", "
        + "COALESCE(fin_curr.ISO_CODE, pmt_curr.ISO_CODE, org_curr.ISO_CODE) AS \"isoCode\", "
        + "COALESCE(fin_curr.CURSYMBOL, pmt_curr.CURSYMBOL, org_curr.CURSYMBOL) AS \"symbol\", "
        + "COALESCE(fin_curr.Issymbolrightside, pmt_curr.Issymbolrightside, org_curr.Issymbolrightside) AS \"currencySymbolAtTheRight\", "
        + "COALESCE(f.CURRENTBALANCE, 0) AS \"currentBalance\", "
        + "COALESCE(pmt.ISCASH, 'N') AS \"iscash\", "
        + "pmt.C_CURRENCY_ID AS \"currency\", "
        + "COALESCE(pmt.ALLOWOVERPAYMENT, 'N') AS \"allowOverPayment\", "
        + "pmt.OBPOS_APP_PAYMENT_TYPE_ID AS \"paymentMethod\", "
        + "OBPOS_CURRENCY_RATE(COALESCE(fin_curr.C_CURRENCY_ID, pmt_curr.C_CURRENCY_ID, org_curr.C_CURRENCY_ID), org_curr.C_CURRENCY_ID, null, null, app.AD_CLIENT_ID, app.AD_ORG_ID) AS \"rate\", "
        + "app_org.NAME AS \"organization$_identifier\" "
        + "FROM OBPOS_APP_PAYMENT p "
        + "LEFT JOIN OBPOS_APP_PAYMENT_TYPE pmt ON pmt.OBPOS_APP_PAYMENT_TYPE_ID = p.OBPOS_APP_PAYMENT_TYPE_ID "
        + "LEFT JOIN FIN_FINANCIAL_ACCOUNT f ON f.FIN_FINANCIAL_ACCOUNT_ID = p.FIN_Financial_Account_ID "
        + "LEFT JOIN C_CURRENCY fin_curr ON fin_curr.C_CURRENCY_ID = f.C_CURRENCY_ID "
        + "LEFT JOIN C_CURRENCY pmt_curr ON pmt_curr.C_CURRENCY_ID = pmt.C_CURRENCY_ID "
        + "LEFT JOIN OBPOS_PAYMENTGROUP pg ON pg.OBPOS_PAYMENTGROUP_ID = pmt.OBPOS_PAYMENTGROUP_ID "
        + "JOIN OBPOS_APPLICATIONS app ON app.OBPOS_APPLICATIONS_ID = p.OBPOS_APPLICATIONS_ID "
        + "JOIN AD_ORG app_org ON app_org.AD_ORG_ID = app.AD_ORG_ID "
        + "LEFT JOIN AD_ORG org ON org.AD_ORG_ID = app.AD_ORG_ID "
        + "LEFT JOIN M_PRICELIST pl ON pl.M_PRICELIST_ID = org.EM_Obretco_Pricelist_ID "
        + "LEFT JOIN C_CURRENCY org_curr ON org_curr.C_CURRENCY_ID = pl.C_CURRENCY_ID "
        + "WHERE p.OBPOS_APPLICATIONS_ID = :terminalId "
        + "AND p.ISACTIVE = 'Y' "
        + "AND (pmt.ISACTIVE IS NULL OR pmt.ISACTIVE = 'Y') "
        + "ORDER BY p.LINE, p.NAME";
//@formatter:on
    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
    query.setParameter("terminalId", terminalId);

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject entry = new JSONObject();
      try {
        JSONObject pay = new JSONObject();
        pay.put("id", getStringValue(row.get("id")));
        pay.put("paymentValue", getStringValue(row.get("paymentValue")));
        pay.put("paymentName", getStringValue(row.get("paymentName")));
        pay.put("isActive", getBooleanValue(row.get("isActive")));
        pay.put("commercialName", getStringValue(row.get("commercialName")));
        pay.put("allowVariableAmount", getBooleanValue(row.get("allowVariableAmount")));
        pay.put("organization$_identifier", getStringValue(row.get("organization$_identifier")));
        entry.put("payment", pay);

        entry.put("isoCode", getStringValue(row.get("isoCode")));
        entry.put("symbol", getStringValue(row.get("symbol")));
        entry.put("currencySymbolAtTheRight", getBooleanValue(row.get("currencySymbolAtTheRight")));
        entry.put("currentBalance", getStringValue(row.get("currentBalance")));

        JSONObject paymentMethod = new JSONObject();
        paymentMethod.put("iscash", getBooleanValue(row.get("iscash")));
        paymentMethod.put("currency", getStringValue(row.get("currency")));
        paymentMethod.put("allowOverPayment", getBooleanValue(row.get("allowOverPayment")));
        paymentMethod.put("paymentMethod", getStringValue(row.get("paymentMethod")));
        entry.put("paymentMethod", paymentMethod);

        String rateStr = getStringValue(row.get("rate"));
        BigDecimal rate = (rateStr != null && !rateStr.isEmpty()) ? new BigDecimal(rateStr)
            : BigDecimal.ZERO;
        BigDecimal mulrate = BigDecimal.ZERO;
        if (rate.compareTo(BigDecimal.ZERO) != 0) {
          mulrate = BigDecimal.ONE.divide(rate, 12, RoundingMode.HALF_UP);
        }
        entry.put("rate", rate.toPlainString());
        entry.put("mulrate", mulrate.toPlainString());

      } catch (JSONException e) {
        log.error("Error building payment JSON", e);
      }
      payments.put(entry);
    }

    return payments;
  }

  public JSONArray getPriceLists(String organizationId) {
    JSONArray priceLists = new JSONArray();
//@formatter:off
    String sql = "SELECT pl.M_PRICELIST_ID AS \"id\", " 
        + "pl.NAME AS \"name\", "         
        + "curr.C_CURRENCY_ID AS \"currency\", "
        + "curr.ISO_CODE AS \"currency$_identifier\" "
        + "FROM M_PRICELIST pl "
        + "LEFT JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = pl.C_CURRENCY_ID "
        + "WHERE pl.ISACTIVE = 'Y' "
        + "AND pl.IsSOPriceList = 'Y' " 
        + "ORDER BY pl.NAME";
  //@formatter:on
    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject pricelist = new JSONObject();
      try {
        pricelist.put("id", getStringValue(row.get("id")));
        pricelist.put("name", getStringValue(row.get("name")));
        pricelist.put("description", getStringValue(row.get("description")));
        pricelist.put("priceIncludesTax", getBooleanValue(row.get("priceIncludesTax")));
        pricelist.put("isDefault", getBooleanValue(row.get("isDefault")));
        pricelist.put("isActive", getBooleanValue(row.get("isActive")));
        pricelist.put("salesPriceList", getBooleanValue(row.get("salesPriceList")));
        pricelist.put("currency", getStringValue(row.get("currency")));
        pricelist.put("currency$_identifier", getStringValue(row.get("currency$_identifier")));
        pricelist.put("iSOCode", getStringValue(row.get("iSOCode")));
        pricelist.put("symbol", getStringValue(row.get("symbol")));
      } catch (JSONException e) {
        log.error("Error building pricelist JSON", e);
      }
      priceLists.put(pricelist);
    }

    return priceLists;
  }

  public JSONArray getCurrencyPanel() {
    JSONArray result = new JSONArray();

    String sql = "SELECT e.LINE AS \"lineNo\", "
        + "e.C_CURRENCY_ID AS \"currency\", "
        + "e.BACKCOLOR AS \"backcolor\", "
        + "e.BORDERCOLOR AS \"bordercolor\", "
        + "e.AMOUNT AS \"amount\" "
        + "FROM OBPOS_CURRENCY_PANEL e "
        + "WHERE e.ISACTIVE = 'Y' "
        + "ORDER BY e.LINE ASC";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject entry = new JSONObject();
      try {
        entry.put("lineNo", getIntValue(row.get("lineNo")));
        entry.put("currency", getStringValue(row.get("currency")));
        entry.put("backcolor", getStringValue(row.get("backcolor")));
        entry.put("bordercolor", getStringValue(row.get("bordercolor")));
        entry.put("amount", getStringValue(row.get("amount")));
      } catch (JSONException e) {
        log.error("Error building currencyPanel JSON", e);
      }
      result.put(entry);
    }

    return result;
  }

  public JSONArray getCashMgmtDepositEvents(String terminalId) {
    OBPOSApplications pOSTerminal = getTerminal(terminalId);
    JSONArray events = new JSONArray();
    OBContext.getOBContext()
        .getOrganizationStructureProvider(pOSTerminal.getClient().getId())
        .getNaturalTree(pOSTerminal.getOrganization().getId());
    String sql = "SELECT ev.Obretco_Cmevents_ID AS \"id\", " //
        + "ev.NAME AS \"name\", " //
        + "'deposit' as \"type\", "//
        + "ev.FIN_Paymentmethod_ID as \"paymentmethod\", " //
        + "CUR.ISO_Code as \"iscode\" "//
        + "FROM OBRETCO_CMEvents ev " //
        + "JOIN C_CURRENCY CUR ON CUR.C_CURRENCY_ID = EV.C_CURRENCY_ID "
        + "AND ev.EVENTTYPE like '%IN%' " //
        + "AND ev.ad_org_id IN (:orgs) " //
        + "AND ev.ISACTIVE = 'Y' " //
        + "ORDER BY ev.NAME";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE)
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(pOSTerminal.getOrganization().getId()));

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject event = new JSONObject();
      try {
        event.put("id", getStringValue(row.get("id")));
        event.put("name", getStringValue(row.get("name")));
        event.put("type", getStringValue(row.get("type")));
        event.put("paymentmethod", getStringValue(row.get("paymentmethod")));
        event.put("iscode", getStringValue(row.get("iscode")));

      } catch (JSONException e) {
        log.error("Error building deposit event JSON", e);
      }
      events.put(event);
    }

    return events;
  }

  public JSONArray getCashMgmtDropEvents(String terminalId) {
    OBPOSApplications pOSTerminal = getTerminal(terminalId);
    JSONArray events = new JSONArray();
    OBContext.getOBContext()
        .getOrganizationStructureProvider(pOSTerminal.getClient().getId())
        .getNaturalTree(pOSTerminal.getOrganization().getId());
    String sql = "SELECT ev.Obretco_Cmevents_ID AS \"id\", " //
        + "ev.NAME AS \"name\", " //
        + "'drop' as \"type\", "//
        + "ev.FIN_Paymentmethod_ID as \"paymentmethod\", " //
        + "CUR.ISO_Code as \"iscode\" "//
        + "FROM OBRETCO_CMEvents ev " //
        + "JOIN C_CURRENCY CUR ON CUR.C_CURRENCY_ID = EV.C_CURRENCY_ID "
        + "AND ev.EVENTTYPE like '%OUT%' " //
        + "AND ev.ad_org_id IN (:orgs) " //
        + "AND ev.ISACTIVE = 'Y' " //
        + "ORDER BY ev.NAME";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE)
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(pOSTerminal.getOrganization().getId()));

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject event = new JSONObject();
      try {
        event.put("id", getStringValue(row.get("id")));
        event.put("name", getStringValue(row.get("name")));
        event.put("type", getStringValue(row.get("type")));
        event.put("paymentmethod", getStringValue(row.get("paymentmethod")));
        event.put("iscode", getStringValue(row.get("iscode")));

      } catch (JSONException e) {
        log.error("Error building deposit event JSON", e);
      }
      events.put(event);
    }

    return events;
  }

  public JSONArray getExchangeRates() {
    JSONArray rates = new JSONArray();

    String sql = "SELECT curr.ISO_CODE AS \"isoCode\", "
        + "curr.CURSYMBOL AS \"currSymbol\", "
        + "r.C_CURRENCY_ID AS \"currId\", "
        + "tocurr.ISO_CODE AS \"toIsoCode\", "
        + "tocurr.CURSYMBOL AS \"toCurrSymbol\", "
        + "r.C_CURRENCY_ID_TO AS \"toCurrId\", "
        + "r.VALIDFROM AS \"validFrom\", "
        + "r.VALIDTO AS \"validTo\", "
        + "r.MULTIPLYRATE AS \"multRate\" "
        + "FROM C_CONVERSION_RATE r "
        + "JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = r.C_CURRENCY_ID "
        + "JOIN C_CURRENCY tocurr ON tocurr.C_CURRENCY_ID = r.C_CURRENCY_ID_TO "
        + "WHERE r.VALIDFROM <= CURRENT_DATE "
        + "AND r.VALIDTO >= CURRENT_DATE";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject rate = new JSONObject();
      try {
        rate.put("isoCode", getStringValue(row.get("isoCode")));
        rate.put("currSymbol", getStringValue(row.get("currSymbol")));
        rate.put("currId", getStringValue(row.get("currId")));
        rate.put("toIsoCode", getStringValue(row.get("toIsoCode")));
        rate.put("toCurrSymbol", getStringValue(row.get("toCurrSymbol")));
        rate.put("toCurrId", getStringValue(row.get("toCurrId")));
        rate.put("validFrom", getStringValue(row.get("validFrom")));
        rate.put("validTo", getStringValue(row.get("validTo")));
        rate.put("multRate", getStringValue(row.get("multRate")));
      } catch (JSONException e) {
        log.error("Error building rate JSON", e);
      }
      rates.put(rate);
    }

    return rates;
  }

  public JSONArray getHardwareUrl(String terminalId) {
    JSONArray result = new JSONArray();
    OBPOSApplications terminal = getTerminal(terminalId);

    String sql = "SELECT p.OBPOS_HARDWAREURL_ID AS \"id\", " + "hwm.NAME AS \"_identifier\", "
        + "hwm.HARDWAREURL AS \"hardwareURL\", " + "hwm.ISRECEIPTPRINTER AS \"hasReceiptPrinter\", "
        + "hwm.ISPDFPRINTER AS \"hasPDFPrinter\", " + "hwm.BARCODE AS \"barcode\" "
        + "FROM OBPOS_HARDWAREURL p "
        + "JOIN OBPOS_HARDWAREMNG hwm ON hwm.OBPOS_HARDWAREMNG_ID = p.OBPOS_HARDWAREMNG_ID "
        + "WHERE p.OBPOS_TERMINALTYPE_ID = :terminalTypeId " + "AND p.ISACTIVE = 'Y' "
        + "ORDER BY hwm.NAME";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
    query.setParameter("terminalTypeId", terminal.getObposTerminaltype().getId());

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject entry = new JSONObject();
      try {
        entry.put("id", getStringValue(row.get("id")));
        entry.put("_identifier", getStringValue(row.get("_identifier")));
        entry.put("hardwareURL", getStringValue(row.get("hardwareURL")));
        entry.put("hasReceiptPrinter", getBooleanValue(row.get("hasReceiptPrinter")));
        entry.put("hasPDFPrinter", getBooleanValue(row.get("hasPDFPrinter")));
        entry.put("barcode", getStringValue(row.get("barcode")));
      } catch (JSONException e) {
        log.error("Error building hardwareUrl JSON", e);
      }
      result.put(entry);
    }

    return result;
  }

  public JSONArray getDeliveryModes() {
    JSONArray result = new JSONArray();
    String language = OBContext.getOBContext().getLanguage().getLanguage();

    String sql = "SELECT list.VALUE AS \"id\", " + "COALESCE(trl.NAME, list.NAME) AS \"name\" "
        + "FROM AD_REF_LIST list "
        + "LEFT JOIN AD_REF_LIST_TRL trl ON trl.AD_REF_LIST_ID = list.AD_REF_LIST_ID "
        + "  AND trl.AD_LANGUAGE = :language "
        + "WHERE list.AD_REFERENCE_ID = '41D44C94D0AC41DEBA9A2BEB0EAF059C' "
        + "AND list.ISACTIVE = 'Y' " + "ORDER BY list.SEQNO";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
    query.setParameter("language", language);

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject entry = new JSONObject();
      try {
        entry.put("id", getStringValue(row.get("id")));
        entry.put("name", getStringValue(row.get("name")));
      } catch (JSONException e) {
        log.error("Error building deliveryModes JSON", e);
      }
      result.put(entry);
    }

    return result;
  }

  public JSONObject getCurrency(String currencyId) {
    JSONObject currency = new JSONObject();

    String sql = "SELECT c.C_CURRENCY_ID AS \"id\", " //
        + "c.ISO_CODE AS \"iSOCode\", " //
        + "c.C_CURRSYMBOL AS \"symbol\", " //
        + "c.STDPRECISION AS \"standardPrecision\", " //
        + "c.PRICEPRICE AS \"pricePrecision\" " //
        + "FROM C_CURRENCY c " //
        + "WHERE c.C_CURRENCY_ID = :currencyId " //
        + "AND c.ISACTIVE = 'Y'";

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
    query.setParameter("currencyId", currencyId);

    List<Map<String, Object>> results = query.getResultList();

    if (!results.isEmpty()) {
      Map<String, Object> row = results.get(0);
      try {
        currency.put("id", getStringValue(row.get("id")));
        currency.put("iSOCode", getStringValue(row.get("iSOCode")));
        currency.put("symbol", getStringValue(row.get("symbol")));
        currency.put("standardPrecision", getStringValue(row.get("standardPrecision")));
        currency.put("pricePrecision", getStringValue(row.get("pricePrecision")));
      } catch (JSONException e) {
        log.error("Error building currency JSON", e);
      }
    }

    return currency;
  }

  private String getStringValue(Object value) {
    return value != null ? value.toString() : "";
  }

  private int getIntValue(Object value) {
    return value instanceof Number ? ((Number) value).intValue() : 0;
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

  private OBPOSApplications getTerminal(String term) {
    OBCriteria<OBPOSApplications> terminalCriteria = OBDal.getInstance()
        .createCriteria(OBPOSApplications.class);
    terminalCriteria
        .add(Restrictions.or(Restrictions.eq(OBPOSApplications.PROPERTY_SEARCHKEY, term),
            Restrictions.eq(OBPOSApplications.PROPERTY_ID, term)));
    terminalCriteria.setMaxResults(1);
    return (OBPOSApplications) terminalCriteria.uniqueResult();
  }
}
