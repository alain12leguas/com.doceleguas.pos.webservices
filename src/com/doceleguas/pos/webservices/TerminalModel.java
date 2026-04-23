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
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.openbravo.dal.core.OBContext;
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

  private final OBPOSApplications terminal;

  public TerminalModel(OBPOSApplications terminal) {
    this.terminal = terminal;
  }

  public NativeQuery<?> createQuery() {
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

    org.openbravo.model.pricing.pricelist.PriceList priceList = POSUtils
        .getPriceListByTerminalId(terminal.getId());

    final int lastDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "lastassignednum", false)
        .intValue();
    final int lastQuotationDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "quotationslastassignednum", false)
        .intValue();
    final int lastReturnDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "returnslastassignednum", false)
        .intValue();
    final int lastFullInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "fullinvoiceslastassignednum", true)
        .intValue();
    final int lastFullReturnInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "fullreturninvoiceslastassignednum", true)
        .intValue();
    final int lastSimplifiedInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "simplifiedinvoiceslastassignednum", true)
        .intValue();
    final int lastSimplifiedReturnInvoiceDocumentNumber = POSUtils
        .getLastTerminalDocumentSequence(terminal, "simplifiedreturninvoiceslastassignednum", true)
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
    sql.append(" WHERE t.OBPOS_APPLICATIONS_ID = :terminalId ");
    sql.append(" AND  t.ISACTIVE = 'Y'");
    sql.append(" LIMIT 1");

    @SuppressWarnings("deprecation")
    NativeQuery<?> query = (NativeQuery<?>) OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql.toString())
        .setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

    query.setParameter("terminalId", terminal.getId());
    query.setParameter("priceListId", priceList.getId());

    return query;
  }

  private String getDefaultSelect() {
    StringBuilder select = new StringBuilder();
    select.append("t.OBPOS_APPLICATIONS_ID AS \"id\", ");
    select.append("t.VALUE AS \"searchKey\", ");
    select.append("t.NAME AS \"_identifier\", ");
    select.append("o.AD_CLIENT_ID AS \"client\", ");
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
    select.append("o.EM_Obretco_Showtaxid AS \"bpShowtaxid\", ");
    select.append("o.EM_Obretco_Showbpcategory AS \"bpShowbpcategory\", ");
    select.append("t.ORDERDOCNO_PREFIX AS \"docNoPrefix\", ");
    select.append("t.RETURNDOCNO_PREFIX AS \"returnDocNoPrefix\", ");
    select.append("t.QUOTATIONDOCNO_PREFIX AS \"quotationDocNoPrefix\", ");
    select.append("t.FULLINVDOCNO_PREFIX AS \"fullInvoiceDocNoPrefix\", ");
    select.append("t.FULLRETINVDOCNO_PREFIX AS \"fullReturnInvoiceDocNoPrefix\", ");
    select.append("t.SIMPINVDOCNO_PREFIX AS \"simplifiedInvoiceDocNoPrefix\", ");
    select.append("t.SIMPRETINVDOCNO_PREFIX AS \"simplifiedReturnInvoiceDocNoPrefix\", ");
    select.append("tt.ISMULTICHANGE AS \"multiChange\", ");
    // select.append("o.EM_OBPOS_COUNTDIFFLIMIT AS \"organizationCountDiffLimit\", ");
    select.append("t.DEFAULTWEBPOSTAB AS \"defaultwebpostab\", ");
    select.append("tt.OBPOS_TERMINALTYPE_ID AS \"terminalType\", ");
    select.append("t.PRINTOFFLINE AS \"printoffline\", ");
    select.append("t.ISMASTER AS \"ismaster\", ");
    select.append("t.DOCUMENTNO_PADDING AS \"documentnoPadding\", ");
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
    JSONObject terminalJson = new JSONObject();
    try {
      // Direct fields
      terminalJson.put("id", getStringValue(rowMap.get("id")));

      // terminal.terminal
      JSONObject innerTerminal = new JSONObject();
      innerTerminal.put("id", getStringValue(rowMap.get("id")));
      innerTerminal.put("client", getStringValue(rowMap.get("client")));
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
      innerTerminal.put("bp_showtaxid", getStringValue(rowMap.get("bpShowtaxid")));
      innerTerminal.put("bp_showcategoryselector", getStringValue(rowMap.get("bpShowbpcategory")));
      innerTerminal.put("docNoPrefix", getStringValue(rowMap.get("docNoPrefix")));
      innerTerminal.put("returnDocNoPrefix", getStringValue(rowMap.get("returnDocNoPrefix")));
      innerTerminal.put("quotationDocNoPrefix", getStringValue(rowMap.get("quotationDocNoPrefix")));
      innerTerminal.put("fullInvoiceDocNoPrefix",
          getStringValue(rowMap.get("fullInvoiceDocNoPrefix")));
      innerTerminal.put("fullReturnInvoiceDocNoPrefix",
          getStringValue(rowMap.get("fullReturnInvoiceDocNoPrefix")));
      innerTerminal.put("simplifiedInvoiceDocNoPrefix",
          getStringValue(rowMap.get("simplifiedInvoiceDocNoPrefix")));
      innerTerminal.put("simplifiedReturnInvoiceDocNoPrefix",
          getStringValue(rowMap.get("simplifiedReturnInvoiceDocNoPrefix")));
      innerTerminal.put("multiChange", getBooleanValue(rowMap.get("multiChange")));
      // innerTerminal.put("organizationCountDiffLimit",
      // getStringValue(rowMap.get("organizationCountDiffLimit")));
      innerTerminal.put("defaultwebpostab", getStringValue(rowMap.get("defaultwebpostab")));
      innerTerminal.put("terminalType", getStringValue(rowMap.get("terminalType")));
      innerTerminal.put("printoffline", getBooleanValue(rowMap.get("printoffline")));
      innerTerminal.put("ismaster", getBooleanValue(rowMap.get("ismaster")));
      innerTerminal.put("documentnoPadding", getStringValue(rowMap.get("documentnoPadding")));
      terminalJson.put("terminal", innerTerminal);

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
      terminalJson.put("currency", currency);

      // terminal.pricelist
      JSONObject pricelist = new JSONObject();
      pricelist.put("id", getStringValue(rowMap.get("priceList")));
      pricelist.put("name", getStringValue(rowMap.get("pricelistName")));
      pricelist.put("priceIncludesTax", getBooleanValue(rowMap.get("priceIncludesTax")));
      pricelist.put("currency$_identifier", getStringValue(rowMap.get("currencyISOCode")));
      terminalJson.put("pricelist", pricelist);

      // Organization image
      Object imageData = rowMap.get("organizationImageData");
      Object imageMime = rowMap.get("organizationImageMime");
      if (imageData instanceof byte[] && imageMime != null) {
        terminalJson.put("organizationImage",
            "data:" + imageMime + ";base64," + Base64.encodeBase64String((byte[]) imageData));
      } else {
        terminalJson.put("organizationImage", JSONObject.NULL);
      }
    } catch (JSONException e) {
      log.error("Error building terminal JSON", e);
    }
    return terminalJson;
  }

  public JSONArray getPayments() {
    JSONArray payments = new JSONArray();

//@formatter:off
    String sql = "SELECT "
        // OBPOS_APP_PAYMENT fields
        + "p.OBPOS_APP_PAYMENT_ID AS \"id\", "
        + "p.VALUE AS \"searchKey\", "
        + "p.NAME AS \"commercialName\", "
        + "p.ISACTIVE AS \"active\", "
        + "p.LINE AS \"line\", "
        + "p.ALLOWVARIABLEAMOUNT AS \"allowVariableAmount\", "
        + "p.OVERRIDECONFIGURATION AS \"overrideconfiguration\", "
        + "p.C_GLITEM_DIFF_ID AS \"cashDifferences\", "
        + "p.C_GLITEM_DROPDEP_ID AS \"glItemForCashDropDeposit\", "
        + "p.AUTOMATEMOVEMENTTOOTHER AS \"automateMovementToOtherAccount\", "
        + "p.KEEPFIXEDAMOUNT AS \"keepFixedAmount\", "
        + "p.AMOUNT AS \"amount\", "
        + "p.ALLOWDONTMOVE AS \"allowNotToMove\", "
        + "p.ALLOWMOVEEVERYTHING AS \"allowMoveEverything\", "
        + "p.COUNTCASH AS \"countCash\", "
        // OBPOS_APP_PAYMENT_TYPE (paymentMethod) fields
        + "pmt.OBPOS_APP_PAYMENT_TYPE_ID AS \"paymentMethodId\", "
        + "pmt.NAME AS \"paymentMethodName\", "
        + "COALESCE(pmt.ISCASH, 'N') AS \"iscash\", "
        + "pmt.C_CURRENCY_ID AS \"currency\", "
        + "pmt_curr.iso_code AS \"isoCode\", "
        + "COALESCE(pmt.ALLOWOVERPAYMENT, 'N') AS \"allowOverPayment\", "
        // currency
        + "COALESCE(fin_curr.ISO_CODE, pmt_curr.ISO_CODE, org_curr.ISO_CODE) AS \"isocode\", "
        + "COALESCE(fin_curr.CURSYMBOL, pmt_curr.CURSYMBOL, org_curr.CURSYMBOL) AS \"symbol\", "
        + "COALESCE(fin_curr.Issymbolrightside, pmt_curr.Issymbolrightside, org_curr.Issymbolrightside) AS \"currencySymbolAtTheRight\", "
        + "COALESCE(pmt_curr.EM_OBPOS_POSPRECISION, pmt_curr.STDPRECISION) AS \"obposPosprecision\", "
        // financial account
        + "COALESCE(f.CURRENTBALANCE, 0) AS \"currentBalance\", "
        // rate
        + "OBPOS_CURRENCY_RATE(COALESCE(fin_curr.C_CURRENCY_ID, pmt_curr.C_CURRENCY_ID), org_curr.C_CURRENCY_ID, null, null, app.AD_CLIENT_ID, app.AD_ORG_ID) AS \"rate\", "
        // organization
        + "app_org.NAME AS \"organization$_identifier\", "
        // payment method image
        + "pm_img.BINARYDATA AS \"pmImageData\", "
        + "pm_img.MIMETYPE AS \"pmImageMime\", "
        // providerGroup
        + "pg.OBPOS_PAYMENTGROUP_ID AS \"providerGroupId\", "
        + "pg.NAME AS \"providerGroupName\", "
        + "pg_img.BINARYDATA AS \"pgImageData\", "
        + "pg_img.MIMETYPE AS \"pgImageMime\", "
        + "pg_color.HEX_COLOR AS \"pgColor\", "
        // paymentType
        + "pt.OBPOS_PAYMENTMETHOD_TYPE_ID AS \"paymentTypeId\", "
        + "pt.NAME AS \"paymentTypeName\", "
        // color
        + "pm_color.HEX_COLOR AS \"color\" "
        + "FROM OBPOS_APP_PAYMENT p "
        + "LEFT JOIN OBPOS_APP_PAYMENT_TYPE pmt ON pmt.OBPOS_APP_PAYMENT_TYPE_ID = p.OBPOS_APP_PAYMENT_TYPE_ID "
        + "LEFT JOIN FIN_FINANCIAL_ACCOUNT f ON f.FIN_FINANCIAL_ACCOUNT_ID = p.FIN_FINANCIAL_ACCOUNT_ID "
        + "LEFT JOIN C_CURRENCY fin_curr ON fin_curr.C_CURRENCY_ID = f.C_CURRENCY_ID "
        + "LEFT JOIN C_CURRENCY pmt_curr ON pmt_curr.C_CURRENCY_ID = pmt.C_CURRENCY_ID "
        + "LEFT JOIN OBPOS_PAYMENTGROUP pg ON pg.OBPOS_PAYMENTGROUP_ID = pmt.OBPOS_PAYMENTGROUP_ID "
        + "LEFT JOIN AD_IMAGE pm_img ON pm_img.AD_IMAGE_ID = pmt.AD_IMAGE_ID "
        + "LEFT JOIN AD_IMAGE pg_img ON pg_img.AD_IMAGE_ID = pg.AD_IMAGE_ID "
        + "LEFT JOIN AD_COLOR pm_color ON pm_color.AD_COLOR_ID = pmt.AD_COLOR_ID "
        + "LEFT JOIN AD_COLOR pg_color ON pg_color.AD_COLOR_ID = pg.AD_COLOR_ID "
        + "LEFT JOIN OBPOS_PAYMENTMETHOD_TYPE pt ON pt.OBPOS_PAYMENTMETHOD_TYPE_ID = pmt.OBPOS_PAYMENTMETHOD_TYPE_ID "
        + "LEFT JOIN OBPOS_PAY_METHOD_CATEGORY pmc ON pmc.OBPOS_PAY_METHOD_CATEGORY_ID = pmt.OBPOS_PAY_METHOD_CATEGORY_ID "
        + "JOIN OBPOS_APPLICATIONS app ON app.OBPOS_APPLICATIONS_ID = p.OBPOS_APPLICATIONS_ID "
        + "JOIN AD_ORG app_org ON app_org.AD_ORG_ID = app.AD_ORG_ID "
        + "LEFT JOIN AD_ORG org ON org.AD_ORG_ID = app.AD_ORG_ID "
        + "LEFT JOIN M_PRICELIST pl ON pl.M_PRICELIST_ID = org.EM_Obretco_Pricelist_ID "
        + "LEFT JOIN C_CURRENCY org_curr ON org_curr.C_CURRENCY_ID = pl.C_CURRENCY_ID "
        + "WHERE p.OBPOS_APPLICATIONS_ID = :terminalId "
        + "AND p.ISACTIVE = 'Y' "
        + "AND (pmt.ISACTIVE IS NULL OR pmt.ISACTIVE = 'Y') "
        + "ORDER BY COALESCE(pmc.SEQNO, p.LINE), p.NAME";
//@formatter:on
    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> query = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql);
    query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
    query.setParameter("terminalId", terminal.getId());

    List<Map<String, Object>> results = query.getResultList();

    for (Map<String, Object> row : results) {
      JSONObject entry = new JSONObject();
      try {
        // payment object
        JSONObject pay = new JSONObject();
        pay.put("id", getStringValue(row.get("id")));
        pay.put("searchKey", getStringValue(row.get("searchKey")));
        pay.put("commercialName", getStringValue(row.get("commercialName")));
        pay.put("active", getBooleanValue(row.get("active")));
        pay.put("line", getIntValue(row.get("line")));
        pay.put("allowVariableAmount", getBooleanValue(row.get("allowVariableAmount")));
        pay.put("overrideconfiguration", getBooleanValue(row.get("overrideconfiguration")));
        pay.put("cashDifferences", getStringValue(row.get("cashDifferences")));
        pay.put("gLItemForCashDropDeposit", getStringValue(row.get("glItemForCashDropDeposit")));
        pay.put("automateMovementToOtherAccount",
            getBooleanValue(row.get("automateMovementToOtherAccount")));
        pay.put("keepFixedAmount", getBooleanValue(row.get("keepFixedAmount")));
        pay.put("amount",
            row.get("amount") instanceof Number ? row.get("amount") : JSONObject.NULL);
        pay.put("allowNotToMove", getBooleanValue(row.get("allowNotToMove")));
        pay.put("allowMoveEverything", getBooleanValue(row.get("allowMoveEverything")));
        pay.put("countCash", getBooleanValue(row.get("countCash")));
        pay.put("organization$_identifier", getStringValue(row.get("organization$_identifier")));
        entry.put("payment", pay);

        // paymentMethod object
        JSONObject paymentMethod = new JSONObject();
        paymentMethod.put("id", getStringValue(row.get("paymentMethodId")));
        paymentMethod.put("name", getStringValue(row.get("paymentMethodName")));
        paymentMethod.put("iscash", getBooleanValue(row.get("iscash")));
        paymentMethod.put("currency", getStringValue(row.get("currency")));
        paymentMethod.put("currency$_identifier", getStringValue(row.get("isoCode")));
        paymentMethod.put("allowOverPayment", getBooleanValue(row.get("allowOverPayment")));
        if (Boolean.TRUE.equals(getBooleanValue(row.get("overrideconfiguration")))) {
          paymentMethod.put("cashDifferences", getStringValue(row.get("cashDifferences")));
          paymentMethod.put("glitemDropdep", getStringValue(row.get("glItemForCashDropDeposit")));
          paymentMethod.put("automatemovementtoother",
              getBooleanValue(row.get("automateMovementToOtherAccount")));
          paymentMethod.put("keepfixedamount", getBooleanValue(row.get("keepFixedAmount")));
          paymentMethod.put("amount",
              row.get("amount") instanceof Number ? row.get("amount") : JSONObject.NULL);
          paymentMethod.put("allowvariableamount", getBooleanValue(row.get("allowVariableAmount")));
          paymentMethod.put("allowdontmove", getBooleanValue(row.get("allowNotToMove")));
          paymentMethod.put("allowmoveeverything", getBooleanValue(row.get("allowMoveEverything")));
          paymentMethod.put("countcash", getBooleanValue(row.get("countCash")));
        }
        entry.put("paymentMethod", paymentMethod);

        // rate / mulrate
        String rateStr = getStringValue(row.get("rate"));
        BigDecimal rate = (rateStr != null && !rateStr.isEmpty()) ? new BigDecimal(rateStr)
            : BigDecimal.ZERO;
        BigDecimal mulrate = BigDecimal.ZERO;
        if (rate.compareTo(BigDecimal.ZERO) != 0) {
          mulrate = BigDecimal.ONE.divide(rate, 12, RoundingMode.HALF_UP);
        }
        entry.put("rate", rate.toPlainString());
        entry.put("mulrate", mulrate.toPlainString());

        // currency fields
        entry.put("isocode", getStringValue(row.get("isocode")));
        entry.put("symbol", getStringValue(row.get("symbol")));
        entry.put("currencySymbolAtTheRight", getBooleanValue(row.get("currencySymbolAtTheRight")));
        entry.put("currentBalance",
            row.get("currentBalance") instanceof Number ? row.get("currentBalance")
                : JSONObject.NULL);
        entry.put("obposPosprecision",
            row.get("obposPosprecision") instanceof Number ? row.get("obposPosprecision")
                : JSONObject.NULL);

        // payment method image
        Object pmImageData = row.get("pmImageData");
        Object pmImageMime = row.get("pmImageMime");
        if (pmImageData instanceof byte[] && pmImageMime != null) {
          entry.put("image",
              "data:" + pmImageMime + ";base64," + Base64.encodeBase64String((byte[]) pmImageData));
        } else {
          entry.put("image", JSONObject.NULL);
        }

        // providerGroup
        String providerGroupId = getStringValue(row.get("providerGroupId"));
        if (!providerGroupId.isEmpty()) {
          JSONObject providerGroup = new JSONObject();
          providerGroup.put("id", providerGroupId);
          providerGroup.put("name", getStringValue(row.get("providerGroupName")));
          providerGroup.put("color", getStringValue(row.get("pgColor")));
          Object pgImageData = row.get("pgImageData");
          Object pgImageMime = row.get("pgImageMime");
          if (pgImageData instanceof byte[] && pgImageMime != null) {
            providerGroup.put("image", "data:" + pgImageMime + ";base64,"
                + Base64.encodeBase64String((byte[]) pgImageData));
          } else {
            providerGroup.put("image", JSONObject.NULL);
          }
          entry.put("providerGroup", providerGroup);
        }

        // paymentType
        String paymentTypeId = getStringValue(row.get("paymentTypeId"));
        if (!paymentTypeId.isEmpty()) {
          JSONObject paymentType = new JSONObject();
          paymentType.put("id", paymentTypeId);
          paymentType.put("name", getStringValue(row.get("paymentTypeName")));
          entry.put("paymentType", paymentType);
        }

        // color
        entry.put("color", getStringValue(row.get("color")));

      } catch (JSONException e) {
        log.error("Error building payment JSON", e);
      }
      payments.put(entry);
    }

    return payments;
  }

  public JSONArray getPriceLists() {
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

  @SuppressWarnings("deprecation")
  public JSONArray getCurrencyPanel() {
    JSONArray result = new JSONArray();

    String sql = "SELECT e.LINE AS \"lineNo\", "//
        + "e.C_CURRENCY_ID AS \"currency\", "//
        + "e.BACKCOLOR AS \"backcolor\", " //
        + "e.BORDERCOLOR AS \"bordercolor\", "//
        + "e.AMOUNT AS \"amount\" " //
        + "FROM OBPOS_CURRENCY_PANEL e "//
        + "WHERE e.ISACTIVE = 'Y' " + "ORDER BY e.LINE ASC";

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

  public JSONArray getCashMgmtDepositEvents() {
    JSONArray events = new JSONArray();
    OBContext.getOBContext()
        .getOrganizationStructureProvider(terminal.getClient().getId())
        .getNaturalTree(terminal.getOrganization().getId());
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
                .getNaturalTree(terminal.getOrganization().getId()));

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

  public JSONArray getCashMgmtDropEvents() {
    JSONArray events = new JSONArray();
    OBContext.getOBContext()
        .getOrganizationStructureProvider(terminal.getClient().getId())
        .getNaturalTree(terminal.getOrganization().getId());
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
                .getNaturalTree(terminal.getOrganization().getId()));

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

    String sql = "SELECT curr.ISO_CODE AS \"isoCode\", "//
        + "curr.CURSYMBOL AS \"currSymbol\", "//
        + "r.C_CURRENCY_ID AS \"currId\", " //
        + "tocurr.ISO_CODE AS \"toIsoCode\", "//
        + "tocurr.CURSYMBOL AS \"toCurrSymbol\", " //
        + "r.C_CURRENCY_ID_TO AS \"toCurrId\", "//
        + "r.VALIDFROM AS \"validFrom\", " //
        + "r.VALIDTO AS \"validTo\", "//
        + "r.MULTIPLYRATE AS \"multRate\" " //
        + "FROM C_CONVERSION_RATE r "//
        + "JOIN C_CURRENCY curr ON curr.C_CURRENCY_ID = r.C_CURRENCY_ID "//
        + "JOIN C_CURRENCY tocurr ON tocurr.C_CURRENCY_ID = r.C_CURRENCY_ID_TO "//
        + "WHERE r.VALIDFROM <= CURRENT_DATE " + "AND r.VALIDTO >= CURRENT_DATE";//

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

  public JSONArray getHardwareUrl() {
    JSONArray result = new JSONArray();

    String sql = "SELECT p.OBPOS_HARDWAREURL_ID AS \"id\", " //
        + "hwm.NAME AS \"_identifier\", " //
        + "hwm.HARDWAREURL AS \"hardwareURL\", " //
        + "hwm.ISRECEIPTPRINTER AS \"hasReceiptPrinter\", "
        + "hwm.ISPDFPRINTER AS \"hasPDFPrinter\", " //
        + "hwm.BARCODE AS \"barcode\" " + "FROM OBPOS_HARDWAREURL p "//
        + "JOIN OBPOS_HARDWAREMNG hwm ON hwm.OBPOS_HARDWAREMNG_ID = p.OBPOS_HARDWAREMNG_ID "//
        + "WHERE p.OBPOS_TERMINALTYPE_ID = :terminalTypeId " + "AND p.ISACTIVE = 'Y' "//
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

    String sql = "SELECT list.VALUE AS \"id\", " //
        + "COALESCE(trl.NAME, list.NAME) AS \"name\" " //
        + "FROM AD_REF_LIST list " //
        + "LEFT JOIN AD_REF_LIST_TRL trl ON trl.AD_REF_LIST_ID = list.AD_REF_LIST_ID " //
        + "  AND trl.AD_LANGUAGE = :language " //
        + "WHERE list.AD_REFERENCE_ID = '41D44C94D0AC41DEBA9A2BEB0EAF059C' " //
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
}
