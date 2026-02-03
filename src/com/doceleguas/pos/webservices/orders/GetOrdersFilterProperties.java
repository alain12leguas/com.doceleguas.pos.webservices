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

import org.openbravo.client.kernel.ComponentProvider.Qualifier;
import org.openbravo.mobile.core.model.HQLProperty;
import org.openbravo.mobile.core.model.ModelExtension;

/**
 * Defines the HQL properties for the order filter query.
 * 
 * This class provides the base set of properties returned by GetOrdersFilter.
 * Other modules can extend this by creating additional classes annotated with
 * {@code @Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)}.
 * 
 * Properties are mapped from HQL expressions to JSON field names using
 * the HQLProperty class.
 * 
 * Example extension in another module:
 * <pre>
 * {@code
 * @ApplicationScoped
 * @Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)
 * public class CustomOrderProperties extends ModelExtension {
 *   @Override
 *   public List<HQLProperty> getHQLProperties(Object params) {
 *     List<HQLProperty> props = new ArrayList<>();
 *     props.add(new HQLProperty("ord.myCustomField", "customField"));
 *     return props;
 *   }
 *   
 *   @Override
 *   public int getPriority() {
 *     return 100; // Execute after default properties
 *   }
 * }
 * }
 * </pre>
 * 
 * @see GetOrdersFilter
 */
@ApplicationScoped
@Qualifier(GetOrdersFilter.EXTENSION_QUALIFIER)
public class GetOrdersFilterProperties extends ModelExtension {

  @Override
  public List<HQLProperty> getHQLProperties(Object params) {
    List<HQLProperty> properties = new ArrayList<>();

    // ============================================
    // Core Order Properties
    // ============================================
    properties.add(new HQLProperty("ord.id", "id"));
    properties.add(new HQLProperty("ord.documentNo", "documentNo"));
    properties.add(new HQLProperty("ord.orderDate", "orderDate"));
    properties.add(new HQLProperty("ord.creationDate", "creationDate"));
    properties.add(new HQLProperty("ord.updated", "updated"));

    // ============================================
    // Amount Properties
    // ============================================
    properties.add(new HQLProperty("ord.grandTotalAmount", "grossAmount"));
    properties.add(new HQLProperty("ord.summedLineAmount", "netAmount"));

    // ============================================
    // Status Properties
    // ============================================
    properties.add(new HQLProperty("ord.documentStatus", "documentStatus"));
    properties.add(new HQLProperty("ord.iscancelled", "isCancelled"));
    properties.add(new HQLProperty("ord.obposIslayaway", "isLayaway"));
    properties.add(new HQLProperty("ord.salesTransaction", "isSalesTransaction"));

    // ============================================
    // Organization Information
    // ============================================
    properties.add(new HQLProperty("ord.organization.id", "organizationId"));
    properties.add(new HQLProperty("ord.organization.name", "organization"));
    properties.add(new HQLProperty("ord.organization.searchKey", "organizationSearchKey"));

    // ============================================
    // Terminal Information
    // ============================================
    properties.add(new HQLProperty("pos.id", "terminalId"));
    properties.add(new HQLProperty("pos.searchKey", "terminal"));
    properties.add(new HQLProperty("pos.name", "terminalName"));

    // ============================================
    // Business Partner (Customer) Information
    // ============================================
    properties.add(new HQLProperty("bp.id", "businessPartnerId"));
    properties.add(new HQLProperty("bp.searchKey", "businessPartner"));
    properties.add(new HQLProperty("bp.name", "businessPartnerName"));

    // ============================================
    // Document Type Information
    // ============================================
    properties.add(new HQLProperty("docType.id", "documentTypeId"));
    properties.add(new HQLProperty("docType.name", "documentType"));
    properties.add(new HQLProperty("docType.return", "isReturn"));
    properties.add(new HQLProperty("docType.sOSubType", "documentSubType"));

    // ============================================
    // Currency and Price Information
    // ============================================
    properties.add(new HQLProperty("ord.currency.id", "currencyId"));
    properties.add(new HQLProperty("ord.currency.iSOCode", "currency"));
    properties.add(new HQLProperty("ord.priceIncludesTax", "priceIncludesTax"));
    properties.add(new HQLProperty("ord.priceList.id", "priceListId"));
    properties.add(new HQLProperty("ord.priceList.name", "priceList"));

    // ============================================
    // Warehouse Information
    // ============================================
    properties.add(new HQLProperty("ord.warehouse.id", "warehouseId"));
    properties.add(new HQLProperty("ord.warehouse.name", "warehouse"));

    // ============================================
    // Sales Representative
    // ============================================
    properties.add(new HQLProperty("salesRep.id", "salesRepresentativeId"));
    properties.add(new HQLProperty("salesRep.name", "salesRepresentative"));

    // ============================================
    // Additional Order Properties
    // ============================================
    properties.add(new HQLProperty("ord.description", "description"));
    properties.add(new HQLProperty("ord.orderReference", "orderReference"));
    properties.add(new HQLProperty("ord.externalBusinessPartnerReference", "externalReference"));

    return properties;
  }

  public int getPriority() {
    // Default priority, other extensions can use higher values to add after
    return 0;
  }
}
