/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.core.TriggerHandler;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.InvoiceLineTax;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Locator;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.invoice.InvoiceLine;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOutLine;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Native (OBPL) implementation of POS shipment + invoice for standard completed sales, inspired by
 * the behavioral sequence documented in orderloader-native-sequence-reference.md — without copying
 * org.openbravo.retail.posterminal.utility (OBCL).
 */
@ApplicationScoped
public class OcreNativeStandardDocumentsService {

  private static final BigDecimal NEG_ONE = BigDecimal.valueOf(-1);

  /**
   * Ensures JSON fields commonly read by legacy POS utilities exist when absent.
   */
  public void enrichOrderJsonDefaults(JSONObject orderJson) throws JSONException {
    if (!orderJson.has("timezoneOffset")) {
      int offsetMinutes = java.time.OffsetDateTime.now().getOffset().getTotalSeconds() / 60;
      orderJson.put("timezoneOffset", (long) -offsetMinutes);
    }
    if (!orderJson.has("priceIncludesTax")) {
      orderJson.put("priceIncludesTax", true);
    }
  }

  /**
   * Marks order lines with POS delivery/payment flags from the payload (obpos*), required before
   * shipment creation (shipment logic skips unpaid lines in retail).
   */
  public void applyPosLineFlagsFromPayload(Order order, JSONObject orderJson) throws JSONException {
    JSONArray lines = orderJson.optJSONArray("lines");
    if (lines == null) {
      return;
    }
    OBDal.getInstance().refresh(order);
    List<OrderLine> persisted = order.getOrderLineList();
    if (persisted == null || persisted.isEmpty()) {
      return;
    }
    List<OrderLine> sorted = new ArrayList<>(persisted);
    sorted.sort((a, b) -> Long.compare(a.getLineNo(), b.getLineNo()));

    int n = Math.min(lines.length(), sorted.size());
    for (int i = 0; i < n; i++) {
      JSONObject lj = lines.getJSONObject(i);
      OrderLine ol = sorted.get(i);
      if (lj.optBoolean("obposCanbedelivered", false)) {
        ol.setObposCanbedelivered(true);
      }
      if (lj.optBoolean("obposIspaid", false)) {
        ol.setObposIspaid(true);
      }
      BigDecimal toDel = null;
      if (lj.has("obposQtytodeliver") && !lj.isNull("obposQtytodeliver")) {
        toDel = BigDecimal.valueOf(lj.getDouble("obposQtytodeliver")).stripTrailingZeros();
      }
      if (toDel == null) {
        toDel = ol.getOrderedQuantity();
      }
      ol.setDeliveredQuantity(toDel);
      OBDal.getInstance().save(ol);
    }
    OBDal.getInstance().flush();
  }

  /**
   * Creates customer shipment (C-) with stock update when {@code generateShipment} is true.
   */
  public ShipmentInOut createShipmentIfRequested(Order order, JSONObject orderJson)
      throws Exception {
    if (!orderJson.optBoolean("generateShipment", false)) {
      return null;
    }
    OBDal.getInstance().refresh(order);
    OBCriteria<ShipmentInOut> existing = OBDal.getInstance().createCriteria(ShipmentInOut.class);
    existing.add(Restrictions.eq(ShipmentInOut.PROPERTY_SALESORDER, order));
    existing.setFilterOnReadableOrganization(false);
    existing.setMaxResults(1);
    ShipmentInOut existingInOut = (ShipmentInOut) existing.uniqueResult();
    if (existingInOut != null) {
      List<ShipmentInOutLine> existingLines = existingInOut
          .getMaterialMgmtShipmentInOutLineList();
      if (existingLines != null && !existingLines.isEmpty()) {
        return existingInOut;
      }
      // Orphan header (e.g. first pass with no obposIspaid lines): idempotency must not
      // return this; drop it so this run can create lines + inventory.
      OBDal.getInstance().remove(existingInOut);
      OBDal.getInstance().flush();
    }
    enrichOrderJsonDefaults(orderJson);

    TriggerHandler.getInstance().disable();
    try {
      Locator bin = pickPrimaryLocator(order.getWarehouse());
      if (bin == null) {
        throw new OBException(
            "Native shipment: no storage bin found for warehouse " + order.getWarehouse().getId());
      }

      DocumentType shipDocType = getShipmentDocumentType(order.getDocumentType());
      ShipmentInOut shipment = OBProvider.getInstance().get(ShipmentInOut.class);
      shipment.setOrganization(order.getOrganization());
      shipment.setTrxOrganization(order.getTrxOrganization());
      shipment.setDocumentType(shipDocType);
      shipment.setDocumentNo(order.getDocumentNo());
      shipment.setMovementDate(order.getOrderDate());
      shipment.setAccountingDate(order.getOrderDate());
      shipment.setBusinessPartner(order.getBusinessPartner());
      shipment.setPartnerAddress(order.getPartnerAddress());
      shipment.setSalesTransaction(true);
      shipment.setDocumentStatus("CO");
      shipment.setDocumentAction("--");
      shipment.setMovementType("C-");
      shipment.setProcessNow(false);
      shipment.setProcessed(true);
      shipment.setSalesOrder(order);
      shipment.setWarehouse(order.getWarehouse());
      shipment.setProcessGoodsJava("--");
      OBDal.getInstance().save(shipment);

      JSONArray lines = orderJson.getJSONArray("lines");
      long lineNo = 0;
      for (int i = 0; i < lines.length(); i++) {
        JSONObject lj = lines.getJSONObject(i);
        OrderLine ol = findOrderLineByPayloadId(order, lj.optString("id", null), i);
        if (ol == null) {
          continue;
        }
        if (!ol.isObposIspaid()) {
          continue;
        }
        BigDecimal qty = ol.getDeliveredQuantity() != null ? ol.getDeliveredQuantity().abs()
            : ol.getOrderedQuantity().abs();
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
          continue;
        }
        lineNo += 10;
        ShipmentInOutLine sil = OBProvider.getInstance().get(ShipmentInOutLine.class);
        sil.setOrganization(ol.getOrganization());
        sil.setLineNo(lineNo);
        sil.setShipmentReceipt(shipment);
        sil.setSalesOrderLine(ol);
        sil.setProduct(ol.getProduct());
        sil.setUOM(ol.getUOM());
        sil.setMovementQuantity(qty);
        // M_INOUTLINE check: QUANTITYORDER and M_PRODUCT_UOM_ID are both null or both set (core
        // OB).
        if (ol.getOrderUOM() != null) {
          sil.setOrderUOM(ol.getOrderUOM());
          sil.setOrderQuantity(qty);
        }
        sil.setStorageBin(bin);
        sil.setAttributeSetValue(ol.getAttributeSetValue());
        shipment.getMaterialMgmtShipmentInOutLineList().add(sil);
        ol.getMaterialMgmtShipmentInOutLineList().add(sil);
        OBDal.getInstance().save(sil);
      }

      OBDal.getInstance().flush();
      updateInventoryForShipment(shipment);
      OBDal.getInstance().flush();

      if (orderJson.optBoolean("deliver", false)) {
        order.setDelivered(true);
        OBDal.getInstance().save(order);
      }
      return shipment;
    } finally {
      TriggerHandler.getInstance().enable();
    }
  }

  /**
   * Creates AR invoice linked to order (and shipment lines when provided) for completed standard
   * sales. Triggered when {@code completeTicket} is true and either {@code generateInvoice},
   * {@code generateExternalInvoice}, or {@code calculatedInvoice} is present — or when both
   * {@code completeTicket} and {@code generateShipment} are set (typical OCRE closed sale).
   */
  public Invoice createInvoiceIfRequested(Order order, JSONObject orderJson, ShipmentInOut shipment)
      throws Exception {
    if (!shouldCreateInvoice(orderJson)) {
      return null;
    }
    OBDal.getInstance().refresh(order);
    OBCriteria<Invoice> existingInv = OBDal.getInstance().createCriteria(Invoice.class);
    existingInv.add(Restrictions.eq(Invoice.PROPERTY_SALESORDER, order));
    existingInv.setFilterOnReadableOrganization(false);
    existingInv.setMaxResults(1);
    Invoice inv = (Invoice) existingInv.uniqueResult();
    if (inv != null) {
      relinkInvoiceLinesToGoodsShipmentIfMissing(inv, shipment);
      return inv;
    }
    enrichOrderJsonDefaults(orderJson);

    OBContext.setAdminMode(false);
    TriggerHandler.getInstance().disable();
    try {
      // Persist order lines and reload header totals before copying to C_Invoice (otherwise
      // grand total can still be 0 and downstream PSD→invoice linking is skipped).
      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(order);
      DocumentType invDocType = getInvoiceDocumentType(order.getDocumentType(), true);
      Invoice invoice = OBProvider.getInstance().get(Invoice.class);
      invoice.setOrganization(order.getOrganization());
      invoice.setTrxOrganization(order.getTrxOrganization());
      invoice.setDocumentType(invDocType);
      invoice.setTransactionDocument(invDocType);
      invoice.setDocumentNo(order.getDocumentNo());
      invoice.setAccountingDate(order.getOrderDate());
      invoice.setInvoiceDate(order.getOrderDate());
      invoice.setBusinessPartner(order.getBusinessPartner());
      Location billTo = order.getInvoiceAddress() != null ? order.getInvoiceAddress()
          : order.getPartnerAddress();
      invoice.setPartnerAddress(billTo);
      invoice.setSalesTransaction(true);
      invoice.setDocumentStatus("CO");
      invoice.setDocumentAction("RE");
      invoice.setAPRMProcessinvoice("RE");
      invoice.setProcessed(true);
      invoice.setSalesOrder(order);
      invoice.setCurrency(order.getCurrency());
      invoice.setPaymentMethod(order.getPaymentMethod());
      invoice.setPaymentTerms(order.getPaymentTerms());
      invoice.setGrandTotalAmount(order.getGrandTotalAmount());
      invoice.setSummedLineAmount(order.getSummedLineAmount());
      invoice.setPriceList(order.getPriceList());
      OBDal.getInstance().save(invoice);

      JSONArray lines = orderJson.getJSONArray("lines");
      int pricePrecision = order.getCurrency().getObposPosprecision() == null
          ? order.getCurrency().getPricePrecision().intValue()
          : order.getCurrency().getObposPosprecision().intValue();

      long lineNo = 0;
      for (int i = 0; i < lines.length(); i++) {
        JSONObject lj = lines.getJSONObject(i);
        OrderLine ol = findOrderLineByPayloadId(order, lj.optString("id", null), i);
        if (ol == null) {
          continue;
        }
        ShipmentInOutLine shipLine = findShipmentLineForOrderLine(shipment, ol);
        BigDecimal qtyToInvoice = ol.getOrderedQuantity().abs();
        lineNo += 10;
        InvoiceLine il = OBProvider.getInstance().get(InvoiceLine.class);
        il.setOrganization(invoice.getOrganization());
        il.setLineNo(lineNo);
        il.setInvoice(invoice);
        il.setProduct(ol.getProduct());
        il.setUOM(ol.getUOM());
        il.setSalesOrderLine(ol);
        il.setGoodsShipmentLine(shipLine);
        il.setInvoicedQuantity(qtyToInvoice);
        il.setUnitPrice(ol.getUnitPrice());
        il.setListPrice(ol.getListPrice());
        il.setStandardPrice(ol.getStandardPrice());
        il.setGrossUnitPrice(ol.getGrossUnitPrice());
        il.setGrossListPrice(ol.getGrossListPrice());
        il.setBaseGrossUnitPrice(ol.getBaseGrossUnitPrice());
        il.setLineNetAmount(ol.getLineNetAmount().setScale(pricePrecision, RoundingMode.HALF_UP));
        il.setGrossAmount(ol.getLineGrossAmount().setScale(pricePrecision, RoundingMode.HALF_UP));
        il.setTax(ol.getTax());
        invoice.getInvoiceLineList().add(il);
        ol.setInvoicedQuantity(
            (ol.getInvoicedQuantity() != null ? ol.getInvoicedQuantity() : BigDecimal.ZERO)
                .add(qtyToInvoice));
        OBDal.getInstance().save(il);
        addInvoiceLineTax(il, ol, pricePrecision);
      }
      OBDal.getInstance().flush();
      return invoice;
    } finally {
      try {
        try {
          // enable triggers contains a flush in getConnection method
          TriggerHandler.getInstance().enable();
        } catch (Throwable ignored) {
        }
        OBContext.restorePreviousMode();
      } catch (Throwable ignored) {
      }
    }
  }

  private boolean shouldCreateInvoice(JSONObject orderJson) {
    if (!orderJson.optBoolean("completeTicket", false)) {
      return false;
    }
    if (orderJson.has("calculatedInvoice")) {
      return true;
    }
    if (orderJson.optBoolean("generateExternalInvoice", false)) {
      return true;
    }
    if (orderJson.optBoolean("generateInvoice", false)) {
      return true;
    }
    // Closed sale with physical shipment: mirror ExternalOrderLoader "ALL" / retail expectation.
    return orderJson.optBoolean("generateShipment", false);
  }

  private OrderLine findOrderLineByPayloadId(Order order, String payloadLineId, int indexFallback) {
    List<OrderLine> list = order.getOrderLineList();
    if (list == null) {
      return null;
    }
    if (StringUtils.isNotBlank(payloadLineId) && payloadLineId.length() == 32) {
      for (OrderLine ol : list) {
        if (payloadLineId.equals(ol.getId())) {
          return ol;
        }
      }
    }
    List<OrderLine> sorted = new ArrayList<>(list);
    sorted.sort((a, b) -> Long.compare(a.getLineNo(), b.getLineNo()));
    if (indexFallback >= 0 && indexFallback < sorted.size()) {
      return sorted.get(indexFallback);
    }
    return null;
  }

  private ShipmentInOutLine findShipmentLineForOrderLine(ShipmentInOut shipment, OrderLine ol) {
    if (shipment == null || ol == null) {
      return null;
    }
    for (ShipmentInOutLine sil : shipment.getMaterialMgmtShipmentInOutLineList()) {
      if (sil.getSalesOrderLine() != null && sil.getSalesOrderLine().getId().equals(ol.getId())) {
        return sil;
      }
    }
    return null;
  }

  /**
   * If the invoice was created while the goods shipment had no lines (e.g. empty header recycled on
   * a later sync), attach the correct {@link ShipmentInOutLine} to each sales invoice line.
   */
  private void relinkInvoiceLinesToGoodsShipmentIfMissing(Invoice inv, ShipmentInOut shipment) {
    if (inv == null || shipment == null) {
      return;
    }
    OBDal.getInstance().refresh(inv);
    OBDal.getInstance().refresh(shipment);
    List<InvoiceLine> invLines = inv.getInvoiceLineList();
    if (invLines == null) {
      return;
    }
    for (InvoiceLine il : invLines) {
      if (il == null) {
        continue;
      }
      if (il.getGoodsShipmentLine() != null) {
        continue;
      }
      if (il.getSalesOrderLine() == null) {
        continue;
      }
      ShipmentInOutLine sil = findShipmentLineForOrderLine(shipment, il.getSalesOrderLine());
      if (sil == null) {
        continue;
      }
      il.setGoodsShipmentLine(sil);
      OBDal.getInstance().save(il);
    }
  }

  private void addInvoiceLineTax(InvoiceLine il, OrderLine ol, int pricePrecision) {
    TaxRate tax = ol.getTax();
    if (tax == null) {
      return;
    }
    InvoiceLineTax ilt = OBProvider.getInstance().get(InvoiceLineTax.class);
    ilt.setOrganization(il.getOrganization());
    ilt.setInvoice(il.getInvoice());
    ilt.setInvoiceLine(il);
    ilt.setTax(tax);
    BigDecimal net = il.getLineNetAmount();
    BigDecimal gross = il.getGrossAmount();
    if (gross == null || net == null) {
      return;
    }
    ilt.setTaxableAmount(net);
    ilt.setTaxAmount(gross.subtract(net).setScale(pricePrecision, RoundingMode.HALF_UP));
    il.getInvoiceLineTaxList().add(ilt);
    OBDal.getInstance().save(ilt);
  }

  private Locator pickPrimaryLocator(org.openbravo.model.common.enterprise.Warehouse warehouse) {
    if (warehouse == null) {
      return null;
    }
    OBCriteria<Locator> loc = OBDal.getInstance().createCriteria(Locator.class);
    loc.add(Restrictions.eq(Locator.PROPERTY_WAREHOUSE, warehouse));
    loc.add(Restrictions.eqOrIsNull(Locator.PROPERTY_ISVIRTUAL, false));
    loc.addOrderBy(Locator.PROPERTY_RELATIVEPRIORITY, true);
    loc.setFilterOnReadableOrganization(false);
    loc.setMaxResults(1);
    return (Locator) loc.uniqueResult();
  }

  private DocumentType getShipmentDocumentType(DocumentType orderDocType) {
    if (orderDocType == null) {
      throw new OBException("Native shipment: order document type is null");
    }
    DocumentType dt = orderDocType.getDocumentTypeForShipment();
    if (dt == null) {
      throw new OBException(
          "Native shipment: configure Document Type for Shipment on " + orderDocType.getName());
    }
    return dt;
  }

  private DocumentType getInvoiceDocumentType(DocumentType orderDocType, boolean fullInvoice) {
    if (orderDocType == null) {
      throw new OBException("Native invoice: order document type is null");
    }
    DocumentType dt = fullInvoice ? orderDocType.getDocumentTypeForInvoice()
        : orderDocType.getDoctypesimpinvoice();
    if (dt == null) {
      throw new OBException(
          "Native invoice: configure Document Type for Invoice on " + orderDocType.getName());
    }
    return dt;
  }

  private void updateInventoryForShipment(ShipmentInOut shipment) throws Exception {
    org.openbravo.database.ConnectionProvider cp = new DalConnectionProvider(false);
    CallableStatement st = cp.getConnection()
        .prepareCall("{call M_UPDATE_INVENTORY (?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    try {
      for (ShipmentInOutLine line : shipment.getMaterialMgmtShipmentInOutLineList()) {
        if (!"I".equals(line.getProduct().getProductType()) || !line.getProduct().isStocked()) {
          continue;
        }
        MaterialTransaction tr = OBProvider.getInstance().get(MaterialTransaction.class);
        tr.setOrganization(line.getOrganization());
        tr.setMovementType(shipment.getMovementType());
        tr.setProduct(line.getProduct());
        tr.setStorageBin(line.getStorageBin());
        tr.setOrderUOM(line.getOrderUOM());
        tr.setUOM(line.getUOM());
        tr.setOrderQuantity(line.getOrderQuantity());
        tr.setMovementQuantity(line.getMovementQuantity().multiply(NEG_ONE));
        tr.setMovementDate(shipment.getMovementDate());
        tr.setGoodsShipmentLine(line);
        tr.setAttributeSetValue(line.getAttributeSetValue());
        tr.setId(line.getId());
        tr.setNewOBObject(true);
        fillUpdateInventoryStatement(st, tr);
        st.execute();
        OBDal.getInstance().save(tr);
      }
    } finally {
      st.close();
    }
  }

  private void fillUpdateInventoryStatement(CallableStatement updateStockStatement,
      MaterialTransaction transaction) throws Exception {
    updateStockStatement.setString(1, OBContext.getOBContext().getCurrentClient().getId());
    updateStockStatement.setString(2, OBContext.getOBContext().getCurrentOrganization().getId());
    updateStockStatement.setString(3, OBContext.getOBContext().getUser().getId());
    updateStockStatement.setString(4, transaction.getProduct().getId());
    updateStockStatement.setString(5, transaction.getStorageBin().getId());
    updateStockStatement.setString(6,
        transaction.getAttributeSetValue() != null ? transaction.getAttributeSetValue().getId()
            : null);
    updateStockStatement.setString(7, transaction.getUOM().getId());
    updateStockStatement.setString(8, null);
    updateStockStatement.setBigDecimal(9, transaction.getMovementQuantity());
    updateStockStatement.setBigDecimal(10, transaction.getOrderQuantity());
    updateStockStatement.setDate(11, null);
    updateStockStatement.setBigDecimal(12, BigDecimal.ZERO);
    updateStockStatement.setBigDecimal(13,
        transaction.getOrderQuantity() != null ? transaction.getOrderQuantity().multiply(NEG_ONE)
            : null);
  }
}
