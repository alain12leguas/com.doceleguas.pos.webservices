/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;

/**
 * Minimal parity with {@code org.openbravo.retail.posterminal.OrderLoader} quotation revision and
 * order-from-quotation association ({@code deleteOldDocument}, {@code associateOrderToQuotation}).
 */
@ApplicationScoped
public class OcreQuotationLinkageHelper {

  /**
   * Before creating a new quotation that supersedes another, mark the previous document as rejected
   * ({@code CJ}), matching {@code OrderLoader.deleteOldDocument}.
   */
  public void markPreviousQuotationRejectedIfNeeded(JSONObject orderJson) throws JSONException {
    if (!orderJson.optBoolean("isQuotation", false)) {
      return;
    }
    String oldId = getValidOldId(orderJson);
    if (oldId == null) {
      return;
    }
    Order old = OBDal.getInstance().get(Order.class, oldId);
    if (old != null) {
      old.setDocumentStatus("CJ");
      OBDal.getInstance().save(old);
      OBDal.getInstance().flush();
    }
  }

  /**
   * On a new quotation header, link the rejected predecessor reference (EM_Obpos_Rejected_Quotat).
   */
  public void applyRejectedQuotationReference(Order order, JSONObject orderJson) throws JSONException {
    if (!orderJson.optBoolean("isQuotation", false)) {
      return;
    }
    String oldId = getValidOldId(orderJson);
    if (oldId == null) {
      return;
    }
    Order old = OBDal.getInstance().get(Order.class, oldId);
    if (old != null) {
      order.setObposRejectedQuotation(old);
      OBDal.getInstance().save(order);
    }
  }

  /**
   * When a sales order is created from a quotation ({@code !isQuotation} + {@code oldId}), link
   * header and lines to the source quotation ({@code OrderLoader.associateOrderToQuotation}).
   */
  public void associateOrderToQuotationIfNeeded(Order order, JSONObject orderJson)
      throws JSONException {
    if (orderJson.optBoolean("isQuotation", false)) {
      return;
    }
    String quotationId = getValidOldId(orderJson);
    if (quotationId == null) {
      return;
    }
    Order quotation = OBDal.getInstance().get(Order.class, quotationId);
    if (quotation == null || "CJ".equals(quotation.getDocumentStatus())) {
      return;
    }
    order.setQuotation(quotation);
    List<OrderLine> orderLines = sortedByLineNo(order.getOrderLineList());
    List<OrderLine> quotationLines = sortedByLineNo(quotation.getOrderLineList());
    for (int i = 0; i < orderLines.size() && i < quotationLines.size(); i++) {
      orderLines.get(i).setQuotationLine(quotationLines.get(i));
      OBDal.getInstance().save(orderLines.get(i));
    }
    quotation.setDocumentStatus("CA");
    OBDal.getInstance().save(quotation);
    OBDal.getInstance().save(order);
  }

  private List<OrderLine> sortedByLineNo(List<OrderLine> lines) {
    if (lines == null) {
      return java.util.Collections.emptyList();
    }
    return lines.stream().sorted(Comparator.comparing(OrderLine::getLineNo))
        .collect(Collectors.toList());
  }

  private String getValidOldId(JSONObject orderJson) throws JSONException {
    if (!orderJson.has("oldId") || orderJson.isNull("oldId")) {
      return null;
    }
    String oldId = orderJson.getString("oldId");
    if (StringUtils.isBlank(oldId) || "null".equalsIgnoreCase(oldId)) {
      return null;
    }
    return oldId;
  }
}
