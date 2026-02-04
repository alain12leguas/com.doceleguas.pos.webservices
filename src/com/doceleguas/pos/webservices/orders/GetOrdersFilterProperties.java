/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orders;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.mobile.core.model.HQLProperty;
import org.openbravo.mobile.core.model.ModelExtension;

/**
 * Defines the HQL properties for GetOrdersFilter query.
 * 
 * Properties are compatible with PaidReceiptsFilter response format.
 * Extensible via CDI with {@code @Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)}.
 * 
 * @see GetOrdersFilter
 */
@ApplicationScoped
@Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)
public class GetOrdersFilterProperties extends ModelExtension {

  @Override
  public List<HQLProperty> getHQLProperties(Object params) {
    List<HQLProperty> props = new ArrayList<>();

    // Core (aligned with PaidReceiptsFilterProperties)
    props.add(new HQLProperty("ord.id", "id"));
    props.add(new HQLProperty("docType.id", "documentTypeId"));
    props.add(new HQLProperty("ord.documentStatus", "documentStatus"));
    props.add(new HQLProperty("ord.documentNo", "documentNo"));
    props.add(new HQLProperty("ord.creationDate", "creationDate"));
    props.add(new HQLProperty("ord.orderDate", "orderDate"));
    props.add(new HQLProperty("ord.orderDate", "orderDateFrom"));
    props.add(new HQLProperty("ord.orderDate", "orderDateTo"));

    // Business Partner
    props.add(new HQLProperty("bp.id", "businessPartner"));
    props.add(new HQLProperty("bp.name", "businessPartnerName"));

    // Amounts
    props.add(new HQLProperty("ord.grandTotalAmount", "totalamount"));
    props.add(new HQLProperty("ord.grandTotalAmount", "totalamountFrom"));
    props.add(new HQLProperty("ord.grandTotalAmount", "totalamountTo"));

    // Status
    props.add(new HQLProperty("ord.iscancelled", "iscancelled"));

    // Organization (using org alias like PaidReceiptsFilter)
    props.add(new HQLProperty("org.id", "organization"));
    props.add(new HQLProperty("org.searchKey", "orgSearchKey"));
    props.add(new HQLProperty("org.name", "orgName"));

    // Transaction Organization (using trxOrg alias)
    props.add(new HQLProperty("trxOrg.id", "trxOrganization"));
    props.add(new HQLProperty("trxOrg.name", "trxOrganizationName"));

    // Delivery
    props.add(new HQLProperty("ord.delivered", "isdelivered"));
    props.add(new HQLProperty("ord.externalBusinessPartnerReference", "externalBusinessPartnerReference"));
    props.add(new HQLProperty(
        "(select coalesce(max(ol.obrdmDeliveryMode), 'PickAndCarry') from OrderLine ol where ord.id = ol.salesOrder.id and ol.obposIsDeleted = 'N')",
        "deliveryMode"));
    props.add(new HQLProperty(
        "(select min(case when ol.obrdmDeliveryDate is null or ol.obrdmDeliveryTime is null then null else to_timestamp(to_char(ol.obrdmDeliveryDate, 'YYYY') || '-' || to_char(ol.obrdmDeliveryDate, 'MM') || '-' || to_char(ol.obrdmDeliveryDate, 'DD') || ' ' || to_char(ol.obrdmDeliveryTime, 'HH24') || ':' || to_char(ol.obrdmDeliveryTime, 'MI'), 'YYYY-MM-DD HH24:MI') end) from OrderLine ol where ord.id = ol.salesOrder.id)",
        "deliveryDate"));

    // OrderType (dynamic calculation like PaidReceiptsFilter)
    props.add(new HQLProperty(getOrderTypeHql(params), "orderType"));

    // Invoice
    props.add(new HQLProperty("ord.invoiceTerms", "invoiceTerms"));

    // Anonymous customer detection (same logic as PaidReceiptsFilter)
    props.add(new HQLProperty(
        "(case when ord.externalBusinessPartnerReference is not null then false "
            + "when (obpos.defaultCustomer is not null and obpos.defaultCustomer.id = bp.id) then true "
            + "when (org.obretcoCBpartner is not null and org.obretcoCBpartner.id = bp.id) then true "
            + "else false end)",
        "isAnonymousCustomerSale"));

    return props;
  }

  /**
   * Generates orderType HQL expression with dynamic CASE WHEN logic.
   * Compatible with PaidReceiptsFilter behavior.
   */
  protected String getOrderTypeHql(Object params) {
    String orderType = "";
    if (params instanceof JSONObject) {
      orderType = GetOrdersFilter.getFilterValue((JSONObject) params, "orderType");
    }
    switch (orderType) {
      case "ORD":
        return "to_char('ORD')";
      case "LAY":
        return "to_char('LAY')";
      case "QT":
        return "to_char('QT')";
      case "RET":
        return "to_char('RET')";
      default:
        return "(case when ord.documentType.return = true then 'RET'"
            + " when ord.documentType.sOSubType = 'OB' then 'QT'"
            + " when ord.obposIslayaway = true then 'LAY' else 'ORD' end)";
    }
  }
}
