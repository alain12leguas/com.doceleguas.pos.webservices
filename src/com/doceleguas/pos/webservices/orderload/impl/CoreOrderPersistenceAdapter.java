/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.advpaymentmngt.process.FIN_AddPayment;
import org.openbravo.advpaymentmngt.process.FIN_PaymentProcess;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Tax;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.invoice.Invoice;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentDetail;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentSchedule;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentScheduleDetail;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.materialmgmt.transaction.ShipmentInOut;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.posterminal.OBPOSAppCashup;
import org.openbravo.retail.posterminal.OBPOSAppPayment;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.TerminalTypePaymentMethod;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.json.JsonConstants;

import com.doceleguas.pos.webservices.cashup.engine.UpdateCashup;
import com.doceleguas.pos.webservices.orderload.OrderFlowType;
import com.doceleguas.pos.webservices.orderload.OrderFlowUtils;
import com.doceleguas.pos.webservices.orderload.spi.OrderPersistencePort;

/**
 * First functional Core/DAL persistence adapter for OCOrder.
 *
 * Native Core/DAL persistence adapter for OCWS_Order.
 */
@ApplicationScoped
public class CoreOrderPersistenceAdapter implements OrderPersistencePort {

  private static final Logger log = LogManager.getLogger();
  private final Map<String, ProductServiceConfig> serviceConfigCache = new HashMap<>();

  @Inject
  private OcreNativeStandardDocumentsService nativeStandardDocumentsService;

  @Override
  public JSONObject persistTransformedEnvelope(JSONObject transformedEnvelope) throws Exception {
    JSONArray data = transformedEnvelope.optJSONArray("data");
    if (data == null) {
      throw new OBException("OCOrder payload does not contain data[]");
    }

    String terminalSearchKey = transformedEnvelope.optString("posTerminal", null);
    TerminalContext terminalContext = resolveTerminalContext(terminalSearchKey);

    JSONArray processedOrders = new JSONArray();
    for (int i = 0; i < data.length(); i++) {
      JSONObject orderJson = data.getJSONObject(i);
      updateCashUpReportIfPresent(orderJson);
      verifyCashupStatusIfApplicable(orderJson);
      ensureSupportedOrderFlow(orderJson);
      OrderFlowType flow = OrderFlowUtils.classify(orderJson);
      log.info("[OCOrder][core] processing documentNo={} flow={} step={}",
          orderJson.optString("documentNo", "<missing>"), flow, OrderFlowUtils.resolveStep(orderJson));

      OrderCreationResult orderResult = createOrReuseOrder(orderJson, terminalContext);
      Order order = orderResult.order;
      if (orderResult.duplicate) {
        // Existing order found by documentNo+org. Keep idempotency.
        processedOrders.put(successOrderJson(order, true));
        continue;
      }

      createOrderLines(orderJson, order, terminalContext);
      if (shouldCompleteOrder(orderJson)) {
        completeOrder(order, orderJson);
      }
      // Lines + DB triggers must persist before shipment/invoice read order totals; otherwise
      // grand total can still be 0 in memory and invoice + PSD→invoice links are skipped (no
      // fin_payment_scheduledetail.fin_payment_schedule_invoice → empty Payment Details on invoice).
      OBDal.getInstance().flush();
      OBDal.getInstance().refresh(order);
      if (shouldCreateStandardPosDocuments(orderJson)) {
        nativeStandardDocumentsService.enrichOrderJsonDefaults(orderJson);
        nativeStandardDocumentsService.applyPosLineFlagsFromPayload(order, orderJson);
        ShipmentInOut shipment = nativeStandardDocumentsService.createShipmentIfRequested(order,
            orderJson);
        nativeStandardDocumentsService.createInvoiceIfRequested(order, orderJson, shipment);
        OBDal.getInstance().refresh(order);
      }
      if (shouldCreatePayments(orderJson)) {
        createBasicPayments(orderJson, order, terminalContext);
      }
      OBDal.getInstance().flush();

      processedOrders.put(successOrderJson(order, false));
    }

    JSONObject response = new JSONObject();
    response.put(JsonConstants.RESPONSE_STATUS, JsonConstants.RPCREQUEST_STATUS_SUCCESS);
    response.put("result", "0");
    response.put("orders", processedOrders);
    response.put("pipeline", "core");
    return response;
  }

  /**
   * Parity with {@code OrderLoader.saveRecord}: apply cashup snapshot from the order payload before
   * order persistence.
   */
  private void updateCashUpReportIfPresent(JSONObject orderJson) throws Exception {
    if (!orderJson.has("cashUpReportInformation")) {
      return;
    }
    JSONObject jsonCashup = orderJson.getJSONObject("cashUpReportInformation");
    Date cashUpDate = new Date();
    UpdateCashup.getAndUpdateCashUp(jsonCashup.getString("id"), jsonCashup, cashUpDate);
  }

  /**
   * Parity with {@code OrderLoader.verifyCashupStatus}: reject if the cashup in the report is
   * already processed in BO (same exclusions as retail: quotation / layaway / cancel layaway).
   */
  private void verifyCashupStatusIfApplicable(JSONObject orderJson) throws Exception {
    boolean isQuotation = orderJson.optBoolean("isQuotation", false);
    boolean isLayaway = orderJson.optBoolean("isLayaway", false);
    boolean cancelLayaway = orderJson.optBoolean("cancelLayaway", false);
    if (isQuotation || isLayaway || cancelLayaway) {
      return;
    }
    verifyCashupStatus(orderJson);
  }

  private void verifyCashupStatus(JSONObject orderJson) throws JSONException, OBException {
    OBContext.setAdminMode(false);
    try {
      if (!orderJson.has("cashUpReportInformation")) {
        return;
      }
      JSONObject cashUpInfo = orderJson.getJSONObject("cashUpReportInformation");
      if (cashUpInfo == null || !cashUpInfo.has("id")
          || cashUpInfo.getString("id").equals("")) {
        return;
      }
      OBPOSAppCashup cashUp = OBDal.getInstance()
          .get(OBPOSAppCashup.class, cashUpInfo.getString("id"));
      if (cashUp != null && cashUp.isProcessedbo()) {
        throw new OBException("The cashup related to this order has been processed");
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private JSONObject successOrderJson(Order order, boolean duplicate) throws Exception {
    JSONObject json = new JSONObject();
    json.put("id", order.getId());
    json.put("documentNo", order.getDocumentNo());
    json.put("duplicate", duplicate);
    json.put("native", true);
    return json;
  }

  private void ensureSupportedOrderFlow(JSONObject orderJson) throws Exception {
    String step = OrderFlowUtils.resolveStep(orderJson);
    if (!"create".equalsIgnoreCase(step) && !"all".equalsIgnoreCase(step)
        && !"pay".equalsIgnoreCase(step) && !"ship".equalsIgnoreCase(step)
        && !"cancel".equalsIgnoreCase(step) && !"cancel_replace".equalsIgnoreCase(step)) {
      log.warn("[OCOrder][core] unsupported step '{}' for document {}. Using best-effort create.",
          step, orderJson.optString("documentNo", "<missing>"));
    }

    validateRequiredOrderFields(orderJson);
    validateRequiredLineFields(orderJson);
  }

  private OrderCreationResult createOrReuseOrder(JSONObject orderJson, TerminalContext ctx)
      throws Exception {
    String documentNo = orderJson.optString("documentNo", null);
    Order existing = findExistingOrder(documentNo, ctx.organization.getId());
    if (existing != null) {
      return new OrderCreationResult(existing, true);
    }

    Date orderDate = parseDate(orderJson.optString("orderDate", null));
    BusinessPartner bp = resolveBusinessPartner(orderJson, ctx);
    Location bpLocation = resolveBusinessPartnerLocation(orderJson, bp, ctx);
    PriceList priceList = resolvePriceList(orderJson, ctx);
    Currency currency = resolveCurrency(orderJson, priceList);
    Warehouse warehouse = resolveWarehouse(ctx);
    PaymentTerm paymentTerm = resolvePaymentTerm(bp);
    FIN_PaymentMethod orderPaymentMethod = resolveOrderPaymentMethod(orderJson, ctx, currency);

    DocumentType documentType = resolveOrderDocumentType(orderJson, ctx.organization);
    if (documentType == null) {
      throw new OBException(
          "No order document type found for org " + ctx.organization.getIdentifier());
    }

    Order order = OBProvider.getInstance().get(Order.class);
    order.setOrganization(ctx.organization);
    order.setClient(ctx.client);
    order.setDocumentType(documentType);
    order.setTransactionDocument(documentType);
    order.setDocumentNo(StringUtils.isNotBlank(documentNo) ? documentNo
        : FIN_Utility.getDocumentNo(ctx.organization, "SOO", "C_Order"));
    order.setAccountingDate(orderDate);
    order.setOrderDate(orderDate);
    order.setWarehouse(warehouse);
    order.setObposApplications(ctx.terminal);
    order.setBusinessPartner(bp);
    order.setPartnerAddress(bpLocation);
    order.setPriceList(priceList);
    order.setCurrency(currency);
    order.setSalesTransaction(true);
    // Zero until lines persist: DB trigger adds line gross to GrandTotal (no pre-fill from JSON).
    order.setSummedLineAmount(BigDecimal.ZERO);
    order.setGrandTotalAmount(BigDecimal.ZERO);
    order.setPaymentTerms(paymentTerm);
    if (orderPaymentMethod != null) {
      order.setPaymentMethod(orderPaymentMethod);
    }

    OBDal.getInstance().save(order);
    OBDal.getInstance().flush();
    return new OrderCreationResult(order, false);
  }

  private DocumentType resolveOrderDocumentType(JSONObject orderJson, Organization organization) {
    if (orderJson.optBoolean("isQuotation", false) && organization.getObposCDoctypequot() != null) {
      return organization.getObposCDoctypequot();
    }
    if (OrderFlowUtils.isReturnFlow(orderJson) && organization.getObposCDoctyperet() != null) {
      return organization.getObposCDoctyperet();
    }
    if (organization.getObposCDoctype() != null) {
      return organization.getObposCDoctype();
    }
    return FIN_Utility.getDocumentType(organization, "SOO");
  }

  private void createOrderLines(JSONObject orderJson, Order order, TerminalContext ctx)
      throws Exception {
    JSONArray lines = orderJson.optJSONArray("lines");
    if (lines == null || lines.length() == 0) {
      throw new OBException("Order has no lines");
    }

    BigDecimal sumNet = BigDecimal.ZERO;
    BigDecimal sumGross = BigDecimal.ZERO;
    List<PersistedOrderLine> persistedLines = new ArrayList<>();
    for (int i = 0; i < lines.length(); i++) {
      JSONObject lineJson = lines.getJSONObject(i);
      Product product = resolveProduct(lineJson);
      UOM uom = product.getUOM();

      BigDecimal qty = asBigDecimal(lineJson, "qty", BigDecimal.ONE);
      if (qty.compareTo(BigDecimal.ZERO) == 0) {
        throw new OBException("Order line quantity cannot be zero");
      }
      BigDecimal unitPrice = resolveLineUnitNetPrice(lineJson, qty);
      BigDecimal listPrice = asBigDecimal(lineJson, "baseNetUnitPrice", unitPrice);
      BigDecimal lineNet = resolveLineNetAmount(lineJson, qty, unitPrice);
      BigDecimal grossUnitPrice = resolveLineGrossUnitPrice(lineJson, qty, unitPrice, lineNet);
      BigDecimal grossListPrice = resolveLineGrossListPrice(lineJson, grossUnitPrice);
      BigDecimal baseGrossUnitPrice = resolveBaseGrossUnitPrice(lineJson, grossUnitPrice);
      BigDecimal lineGross = resolveLineGrossAmount(lineJson, qty, grossUnitPrice, lineNet);
      TaxRate tax = resolveLineTax(lineJson, order, ctx, product);

      OrderLine line = OBProvider.getInstance().get(OrderLine.class);
      String payloadLineId = lineJson.optString("id", null);
      if (isLikelyId(payloadLineId)) {
        line.setId(payloadLineId);
        line.setNewOBObject(true);
      }
      line.setOrganization(order.getOrganization());
      line.setClient(order.getClient());
      line.setSalesOrder(order);
      line.setOrderDate(order.getOrderDate());
      line.setWarehouse(order.getWarehouse());
      line.setCurrency(order.getCurrency());
      line.setLineNo((long) (10 * (i + 1)));
      line.setProduct(product);
      line.setUOM(uom);
      line.setInvoicedQuantity(BigDecimal.ZERO);
      line.setOrderedQuantity(qty);
      line.setUnitPrice(unitPrice);
      line.setListPrice(listPrice);
      line.setPriceLimit(unitPrice);
      line.setStandardPrice(listPrice);
      line.setGrossUnitPrice(grossUnitPrice);
      line.setGrossListPrice(grossListPrice);
      line.setBaseGrossUnitPrice(baseGrossUnitPrice);
      line.setTax(tax);
      line.setLineNetAmount(lineNet);
      line.setLineGrossAmount(lineGross);
      line.setTaxableAmount(lineNet);

      OBDal.getInstance().save(line);
      persistedLines.add(new PersistedOrderLine(line, product, lineJson, payloadLineId));
      sumNet = sumNet.add(lineNet);
      sumGross = sumGross.add(lineGross);
    }

    createServiceRelationsForLinkedProducts(order, persistedLines);

    BigDecimal payloadGross = asBigDecimal(orderJson, "grossAmount", null);
    BigDecimal payloadNet = asBigDecimal(orderJson, "netAmount", null);
    if (payloadNet != null && payloadNet.compareTo(sumNet) != 0) {
      log.warn("[OCOrder][core] net mismatch for order {}. payloadNet={}, linesNet={}.",
          order.getDocumentNo(), payloadNet, sumNet);
    }
    if (payloadGross != null && payloadGross.compareTo(sumGross) != 0) {
      log.warn(
          "[OCOrder][core] gross mismatch for order {}. payloadGross={}, linesGross={}.",
          order.getDocumentNo(), payloadGross, sumGross);
    }
    // Do not force header totals here; native order processing will derive them from persisted lines.
  }

  private void createServiceRelationsForLinkedProducts(Order order,
      List<PersistedOrderLine> persistedLines) throws Exception {
    if (persistedLines.isEmpty()) {
      return;
    }

    Map<String, PersistedOrderLine> linesByPayloadId = new HashMap<>();
    for (PersistedOrderLine line : persistedLines) {
      if (StringUtils.isNotBlank(line.payloadLineId)) {
        linesByPayloadId.put(line.payloadLineId, line);
      }
    }

    for (PersistedOrderLine serviceLine : persistedLines) {
      ProductServiceConfig config = getProductServiceConfig(serviceLine.product.getId(),
          order.getClient().getId());
      if (!config.linkedToProduct) {
        continue;
      }

      List<OrderLine> relatedLines = resolveRelatedLinesForService(serviceLine, persistedLines,
          linesByPayloadId, order);
      if (relatedLines.isEmpty()) {
        throw new OBException(
            "Missing service relation for linked service line " + serviceLine.orderLine.getLineNo()
                + " (" + serviceLine.product.getSearchKey() + "). Add relatedLines[] in payload.");
      }

      for (OrderLine relatedLine : relatedLines) {
        BigDecimal quantity = resolveServiceRelationQuantity(config.quantityRule,
            serviceLine.orderLine.getOrderedQuantity(), relatedLine.getOrderedQuantity());
        BigDecimal baseGross = relatedLine.getBaseGrossUnitPrice();
        if (baseGross == null) {
          baseGross = relatedLine.getGrossUnitPrice() != null ? relatedLine.getGrossUnitPrice()
              : relatedLine.getUnitPrice();
        }
        BigDecimal amount = baseGross.multiply(quantity);
        insertOrderLineServiceRelation(serviceLine.orderLine, relatedLine, quantity, amount);
      }
    }
  }

  private List<OrderLine> resolveRelatedLinesForService(PersistedOrderLine serviceLine,
      List<PersistedOrderLine> persistedLines, Map<String, PersistedOrderLine> linesByPayloadId,
      Order order) throws Exception {
    JSONArray relatedJsonLines = serviceLine.lineJson.optJSONArray("relatedLines");
    if (relatedJsonLines != null && relatedJsonLines.length() > 0) {
      Map<String, OrderLine> relatedById = new LinkedHashMap<>();
      for (int i = 0; i < relatedJsonLines.length(); i++) {
        Object relatedRef = relatedJsonLines.get(i);
        String relatedId = null;
        if (relatedRef instanceof JSONObject) {
          JSONObject relatedObj = (JSONObject) relatedRef;
          relatedId = relatedObj.optString("orderlineId", null);
        } else if (relatedRef != null) {
          relatedId = String.valueOf(relatedRef);
        }
        if (!isLikelyId(relatedId)) {
          continue;
        }
        PersistedOrderLine persisted = linesByPayloadId.get(relatedId);
        if (persisted != null) {
          relatedById.put(persisted.orderLine.getId(), persisted.orderLine);
          continue;
        }
        OrderLine existing = OBDal.getInstance().get(OrderLine.class, relatedId);
        if (existing != null && existing.getSalesOrder() != null
            && order.getId().equals(existing.getSalesOrder().getId())) {
          relatedById.put(existing.getId(), existing);
        }
      }
      return new ArrayList<>(relatedById.values());
    }

    List<PersistedOrderLine> precedingNonLinked = new ArrayList<>();
    List<PersistedOrderLine> allNonLinked = new ArrayList<>();
    for (PersistedOrderLine candidate : persistedLines) {
      if (candidate.orderLine.getId().equals(serviceLine.orderLine.getId())) {
        continue;
      }
      ProductServiceConfig candidateCfg = getProductServiceConfig(candidate.product.getId(),
          order.getClient().getId());
      if (candidateCfg.linkedToProduct) {
        continue;
      }
      allNonLinked.add(candidate);
      if (candidate.orderLine.getLineNo() < serviceLine.orderLine.getLineNo()) {
        precedingNonLinked.add(candidate);
      }
    }

    if (precedingNonLinked.size() == 1) {
      return java.util.Collections.singletonList(precedingNonLinked.get(0).orderLine);
    }
    if (precedingNonLinked.isEmpty() && allNonLinked.size() == 1) {
      return java.util.Collections.singletonList(allNonLinked.get(0).orderLine);
    }

    throw new OBException("Missing service relation for linked service line "
        + serviceLine.orderLine.getLineNo() + " (" + serviceLine.product.getSearchKey()
        + "). Payload must include relatedLines[] for this order.");
  }

  private BigDecimal resolveServiceRelationQuantity(String quantityRule, BigDecimal serviceQty,
      BigDecimal relatedQty) {
    if ("UQ".equals(quantityRule)) {
      return serviceQty.signum() >= 0 ? BigDecimal.ONE : BigDecimal.ONE.negate();
    }
    if (relatedQty.signum() != serviceQty.signum()) {
      return relatedQty.negate();
    }
    return relatedQty;
  }

  private void insertOrderLineServiceRelation(OrderLine serviceLine, OrderLine relatedLine,
      BigDecimal quantity, BigDecimal amount) throws Exception {
    String sql = "insert into c_orderline_servicerelation "
        + "(c_orderline_servicerelation_id, ad_client_id, ad_org_id, isactive, created, createdby, "
        + "updated, updatedby, c_orderline_id, c_orderline_related_id, amount, quantity) "
        + "values (?, ?, ?, 'Y', ?, ?, ?, ?, ?, ?, ?, ?)";
    Date now = new Date();
    String userId = OBContext.getOBContext().getUser().getId();
    try (PreparedStatement ps = OBDal.getInstance().getConnection().prepareStatement(sql)) {
      ps.setString(1, generateUuid32());
      ps.setString(2, serviceLine.getClient().getId());
      ps.setString(3, serviceLine.getOrganization().getId());
      ps.setTimestamp(4, new Timestamp(now.getTime()));
      ps.setString(5, userId);
      ps.setTimestamp(6, new Timestamp(now.getTime()));
      ps.setString(7, userId);
      ps.setString(8, serviceLine.getId());
      ps.setString(9, relatedLine.getId());
      ps.setBigDecimal(10, amount);
      ps.setBigDecimal(11, quantity);
      ps.executeUpdate();
    }
  }

  private ProductServiceConfig getProductServiceConfig(String productId, String clientId)
      throws Exception {
    ProductServiceConfig cached = serviceConfigCache.get(productId);
    if (cached != null) {
      return cached;
    }

    String sql = "select islinkedtoproduct, quantity_rule from m_product where m_product_id = ? "
        + "and ad_client_id in ('0', ?)";
    try (PreparedStatement ps = OBDal.getInstance().getConnection().prepareStatement(sql)) {
      ps.setString(1, productId);
      ps.setString(2, clientId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          ProductServiceConfig cfg = new ProductServiceConfig("Y".equals(rs.getString(1)),
              rs.getString(2));
          serviceConfigCache.put(productId, cfg);
          return cfg;
        }
      }
    }

    ProductServiceConfig cfg = new ProductServiceConfig(false, null);
    serviceConfigCache.put(productId, cfg);
    return cfg;
  }

  private void createBasicPayments(JSONObject orderJson, Order order, TerminalContext ctx)
      throws Exception {
    JSONArray payments = orderJson.optJSONArray("payments");
    if (payments == null || payments.length() == 0) {
      return;
    }

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(order);

    List<FIN_Payment> created = new ArrayList<>();
    FIN_PaymentSchedule paymentSchedule = createOrReuseOrderPaymentSchedule(order);
    ensureInitialOutstandingPsdLikeOrderLoader(order, orderJson, paymentSchedule);
    FIN_PaymentSchedule invoiceScheduleForPsd = linkOrderPsdToInvoicePaymentScheduleIfInvoiceExists(
        order, paymentSchedule);
    OBDal.getInstance().flush();
    int paymentCount = 0;
    for (int i = 0; i < payments.length(); i++) {
      JSONObject paymentJson = payments.getJSONObject(i);
      if (paymentJson.optBoolean("changePayment", false)) {
        continue;
      }

      BigDecimal signedAmount = resolvePaymentAmount(paymentJson);
      if (signedAmount.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }
      BigDecimal amount = signedAmount.abs();

      PaymentResolution paymentResolution = resolvePaymentMethodAndAccount(paymentJson, ctx,
          order.getCurrency().getId());
      if (paymentResolution == null) {
        log.warn(
            "[OCOrder][core] skipping payment without resolvable method/account. order={}, kind={}",
            order.getDocumentNo(), paymentJson.optString("kind", "<missing>"));
        continue;
      }

      boolean isReceipt = signedAmount.signum() >= 0;
      DocumentType paymentDocType = FIN_Utility.getDocumentType(order.getOrganization(),
          isReceipt ? "ARR" : "APP");
      if (paymentDocType == null) {
        paymentDocType = FIN_Utility.getDocumentType(order.getOrganization(), "ARR");
      }
      String paymentDocNo = order.getDocumentNo();
      if (paymentCount > 0) {
        paymentDocNo = paymentDocNo + "-" + paymentCount;
      }
      if (paymentJson.has("reversedPaymentId") && !paymentJson.isNull("reversedPaymentId")) {
        paymentDocNo = "*R*" + paymentDocNo;
      }
      Date paymentDate = parseDate(paymentJson.optString("date", null));

      FIN_PaymentScheduleDetail psd = resolveOrCreateOutstandingOrderPSD(paymentSchedule, order,
          amount, invoiceScheduleForPsd);
      // FIN_AddPayment.savePayment calls refresh() on each PSD; the row must exist first.
      OBDal.getInstance().flush();
      List<FIN_PaymentScheduleDetail> selectedPSD = new ArrayList<>();
      selectedPSD.add(psd);
      HashMap<String, BigDecimal> selectedPSDAmounts = new HashMap<>();
      BigDecimal psdAllocation = amount;
      if (psd.getAmount() != null && psd.getAmount().signum() < 0) {
        psdAllocation = amount.negate();
      }
      selectedPSDAmounts.put(psd.getId(), psdAllocation);

      // Let the server assign FIN_Payment.id (OrderLoader does not set it from POS line UUIDs).
      FIN_Payment payment = FIN_AddPayment.savePayment(null, isReceipt, paymentDocType, paymentDocNo,
          order.getBusinessPartner(), paymentResolution.paymentMethod,
          paymentResolution.financialAccount, amount.toPlainString(), paymentDate,
          order.getOrganization(), null, selectedPSD, selectedPSDAmounts, false, false,
          order.getCurrency(), BigDecimal.ONE, amount, true, null);
      // Invoice Payment Details tab joins PSD rows by fin_payment_schedule_invoice; if only the
      // order FK was set, Sales Order shows lines but Invoice plan stays empty.
      ensureInvoicePaymentPlanSharesSavedPaymentDetails(payment, invoiceScheduleForPsd);
      FIN_PaymentProcess.doProcessPayment(payment, "P", null, null);
      ensureInvoicePaymentPlanSharesSavedPaymentDetails(payment, invoiceScheduleForPsd);
      created.add(payment);
      paymentCount++;
    }

    if (!created.isEmpty()) {
      syncInvoiceHeaderAndScheduleAfterPosPayments(order, created);
      OBDal.getInstance().flush();
    }
  }

  /**
   * FIN_PaymentProcess may skip {@link FIN_AddPayment#updatePaymentScheduleAmounts} for the invoice
   * schedule depending on payment-status sequencing (invoicePaidAmounts guard). Retail updates invoice
   * totals after payments via {@code InvoiceUtils.setPaidAmountAtInvoicing} / monitor. Align header
   * + invoice payment plan with actual FIN_Payment rows we just created.
   */
  private void syncInvoiceHeaderAndScheduleAfterPosPayments(Order order, List<FIN_Payment> payments) {
    if (payments == null || payments.isEmpty()) {
      return;
    }
    OBCriteria<Invoice> invCrit = OBDal.getInstance().createCriteria(Invoice.class);
    invCrit.add(Restrictions.eq(Invoice.PROPERTY_SALESORDER, order));
    invCrit.setMaxResults(1);
    Invoice invoice = (Invoice) invCrit.uniqueResult();
    if (invoice == null) {
      return;
    }
    BigDecimal sumPaid = BigDecimal.ZERO;
    for (FIN_Payment p : payments) {
      OBDal.getInstance().refresh(p);
      if (p.getAmount() != null) {
        sumPaid = sumPaid.add(p.getAmount());
      }
    }
    BigDecimal gross = invoice.getGrandTotalAmount();
    if (gross == null) {
      return;
    }
    List<FIN_PaymentSchedule> invSchedules = invoice.getFINPaymentScheduleList();
    if (invSchedules == null || invSchedules.isEmpty()) {
      return;
    }
    FIN_PaymentSchedule invSch = invSchedules.get(0);
    BigDecimal prepayment = invoice.getPrepaymentamt() != null ? invoice.getPrepaymentamt()
        : BigDecimal.ZERO;
    // When PSD rows already have fin_payment_detail_id, FIN_PaymentProcess updated (or will have
    // updated) schedule line amounts; avoid overwriting schedule totals only in that case. C_Invoice
    // monitor is still aligned from our POS payments below.
    boolean planHasPaymentDetails = invoicePaymentPlanHasLinkedPaymentDetails(invSch);
    if (!planHasPaymentDetails) {
      invSch.setPaidAmount(sumPaid);
      invSch.setOutstandingAmount(gross.subtract(sumPaid));
      OBDal.getInstance().save(invSch);
    }

    invoice.setTotalPaid(sumPaid);
    invoice.setOutstandingAmount(gross.subtract(sumPaid));
    invoice.setDueAmount(gross.subtract(sumPaid));
    invoice.setPaymentComplete(sumPaid.compareTo(gross) == 0);
    invoice.setLastCalculatedOnDate(new Date());
    invoice.setPaidAmountAtInvoicing(sumPaid.subtract(prepayment));
    OBDal.getInstance().save(invoice);
  }

  /**
   * True when at least one invoice PSD row has FIN_Payment_Detail set (what the UI uses for
   * "Number of Payments" and the Payment Details subtab).
   */
  private boolean invoicePaymentPlanHasLinkedPaymentDetails(FIN_PaymentSchedule invoiceSchedule) {
    if (invoiceSchedule == null) {
      return false;
    }
    List<FIN_PaymentScheduleDetail> list = invoiceSchedule
        .getFINPaymentScheduleDetailInvoicePaymentScheduleList();
    if (list == null) {
      return false;
    }
    for (FIN_PaymentScheduleDetail psd : list) {
      if (psd != null && psd.getPaymentDetails() != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * The invoice Payment Plan subtab lists {@link FIN_PaymentScheduleDetail} rows where
   * {@code fin_payment_schedule_invoice} is set and {@code fin_payment_detail_id} is not null.
   * The same PSD row must reference both order and invoice schedules (retail / OrderLoader model).
   */
  private void applyInvoiceScheduleToPsdIfNeeded(FIN_PaymentScheduleDetail psd,
      FIN_PaymentSchedule invoiceSchedule) {
    if (psd == null || invoiceSchedule == null) {
      return;
    }
    if (psd.getInvoicePaymentSchedule() == null) {
      psd.setInvoicePaymentSchedule(invoiceSchedule);
      OBDal.getInstance().save(psd);
    }
  }

  /**
   * After {@link FIN_AddPayment#savePayment}, ensure each paid PSD row is linked to the invoice
   * payment schedule so the invoice document shows Payment Details (not only the sales order).
   */
  private void ensureInvoicePaymentPlanSharesSavedPaymentDetails(FIN_Payment payment,
      FIN_PaymentSchedule invoiceSchedule) {
    if (payment == null || invoiceSchedule == null) {
      return;
    }
    OBDal.getInstance().refresh(payment);
    boolean changed = false;
    for (FIN_PaymentDetail pd : payment.getFINPaymentDetailList()) {
      if (pd == null) {
        continue;
      }
      for (FIN_PaymentScheduleDetail psd : pd.getFINPaymentScheduleDetailList()) {
        if (psd != null && psd.getOrderPaymentSchedule() != null
            && psd.getInvoicePaymentSchedule() == null) {
          psd.setInvoicePaymentSchedule(invoiceSchedule);
          OBDal.getInstance().save(psd);
          changed = true;
        }
      }
    }
    if (changed) {
      OBDal.getInstance().flush();
    }
  }

  private FIN_PaymentScheduleDetail resolveOrCreateOutstandingOrderPSD(FIN_PaymentSchedule paymentSchedule,
      Order order, BigDecimal paymentAmount, FIN_PaymentSchedule invoiceSchedule) {
    OBDal.getInstance().refresh(paymentSchedule);
    List<FIN_PaymentScheduleDetail> psdList = paymentSchedule
        .getFINPaymentScheduleDetailOrderPaymentScheduleList();
    FIN_PaymentScheduleDetail firstOutstanding = null;
    if (psdList != null) {
      for (FIN_PaymentScheduleDetail psd : psdList) {
        if (psd == null || psd.getPaymentDetails() != null || psd.getAmount() == null
            || psd.getAmount().abs().compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }
        if (psd.getAmount().abs().compareTo(paymentAmount.abs()) == 0) {
          applyInvoiceScheduleToPsdIfNeeded(psd, invoiceSchedule);
          return psd;
        }
        if (firstOutstanding == null) {
          firstOutstanding = psd;
        }
      }
    }
    if (firstOutstanding != null) {
      applyInvoiceScheduleToPsdIfNeeded(firstOutstanding, invoiceSchedule);
      return firstOutstanding;
    }
    BigDecimal psdAmount = paymentAmount;
    if (paymentSchedule.getAmount() != null && paymentSchedule.getAmount().signum() < 0) {
      psdAmount = paymentAmount.negate();
    }
    // Same PSD row must reference the invoice plan when an invoice exists, or the invoice Payment
    // Plan shows 0 payments / empty details (computed column joins via fin_payment_schedule_invoice).
    return FIN_AddPayment.createPSD(psdAmount, paymentSchedule, invoiceSchedule,
        order.getOrganization(), order.getBusinessPartner());
  }

  private FIN_PaymentSchedule createOrReuseOrderPaymentSchedule(Order order) {
    OBDal.getInstance().refresh(order);
    List<FIN_PaymentSchedule> schedules = findOrderPaymentSchedules(order.getId());
    if (!schedules.isEmpty()) {
      FIN_PaymentSchedule primary = schedules.get(0);
      normalizePosPaymentPlan(order, primary, schedules);
      return primary;
    }

    FIN_PaymentSchedule paymentSchedule = OBProvider.getInstance().get(FIN_PaymentSchedule.class);
    paymentSchedule.setOrganization(order.getOrganization());
    paymentSchedule.setCurrency(order.getCurrency());
    paymentSchedule.setOrder(order);
    paymentSchedule.setFinPaymentmethod(order.getPaymentMethod());
    paymentSchedule.setAmount(order.getGrandTotalAmount());
    paymentSchedule.setOutstandingAmount(order.getGrandTotalAmount());
    paymentSchedule.setDueDate(order.getOrderDate());
    paymentSchedule.setExpectedDate(order.getOrderDate());
    if (ModelProvider.getInstance()
        .getEntity(FIN_PaymentSchedule.class)
        .hasProperty("origDueDate")) {
      paymentSchedule.set("origDueDate", paymentSchedule.getDueDate());
    }

    order.getFINPaymentScheduleList().add(paymentSchedule);
    OBDal.getInstance().save(paymentSchedule);
    OBDal.getInstance().save(order);
    return paymentSchedule;
  }

  private List<FIN_PaymentSchedule> findOrderPaymentSchedules(String orderId) {
    List<FIN_PaymentSchedule> schedules = new ArrayList<>();
    String sql = "select fin_payment_schedule_id from fin_payment_schedule where c_order_id = ? "
        + "order by created";
    try (PreparedStatement ps = OBDal.getInstance().getConnection().prepareStatement(sql)) {
      ps.setString(1, orderId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          FIN_PaymentSchedule schedule = OBDal.getInstance().get(FIN_PaymentSchedule.class,
              rs.getString(1));
          if (schedule != null) {
            schedules.add(schedule);
          }
        }
      }
    } catch (Exception e) {
      throw new OBException("Error finding payment schedules for order " + orderId, e);
    }
    return schedules;
  }

  private void normalizePosPaymentPlan(Order order, FIN_PaymentSchedule primary,
      List<FIN_PaymentSchedule> schedules) {
    boolean changed = false;
    if (primary.getAmount() == null
        || primary.getAmount().compareTo(order.getGrandTotalAmount()) != 0) {
      primary.setAmount(order.getGrandTotalAmount());
      primary.setOutstandingAmount(order.getGrandTotalAmount());
      changed = true;
    }

    for (int i = 1; i < schedules.size(); i++) {
      FIN_PaymentSchedule duplicate = schedules.get(i);
      if (duplicate == null) {
        continue;
      }
      if (duplicate.getFINPaymentScheduleDetailOrderPaymentScheduleList() != null) {
        for (FIN_PaymentScheduleDetail psd : new ArrayList<>(
            duplicate.getFINPaymentScheduleDetailOrderPaymentScheduleList())) {
          if (psd != null && psd.getPaymentDetails() != null) {
            throw new OBException("Unexpected paid duplicate payment schedule for order "
                + order.getDocumentNo() + ": " + duplicate.getId());
          }
          OBDal.getInstance().remove(psd);
        }
      }
      OBDal.getInstance().remove(duplicate);
      changed = true;
    }

    if (changed) {
      OBDal.getInstance().save(primary);
      OBDal.getInstance().flush();
    }
  }

  private boolean shouldCompleteOrder(JSONObject orderJson) {
    if (orderJson.has("completeTicket")) {
      return orderJson.optBoolean("completeTicket", true);
    }
    return true;
  }

  private boolean shouldCreatePayments(JSONObject orderJson) {
    return OrderFlowUtils.classify(orderJson) != OrderFlowType.QUOTATION;
  }

  /**
   * When true, create native shipment/invoice (Doceleguas) for closed standard sales — equivalent
   * retail behavior without depending on OrderLoader utility classes.
   */
  private boolean shouldCreateStandardPosDocuments(JSONObject orderJson) {
    if (OrderFlowUtils.classify(orderJson) != OrderFlowType.STANDARD_SALE) {
      return false;
    }
    if (!orderJson.optBoolean("completeTicket", false)) {
      return false;
    }
    return orderJson.optBoolean("generateShipment", false)
        || orderJson.optBoolean("generateInvoice", false)
        || orderJson.optBoolean("generateExternalInvoice", false)
        || orderJson.has("calculatedInvoice");
  }

  private void completeOrder(Order order, JSONObject orderJson) {
    OrderFlowType flow = OrderFlowUtils.classify(orderJson);
    if (flow == OrderFlowType.QUOTATION) {
      order.setDocumentStatus("UE");
    } else {
      order.setDocumentStatus("CO");
    }
    order.setDocumentAction("--");
    order.setProcessed(true);
    order.setProcessNow(false);
    order.setDelivered(false);
    OBDal.getInstance().save(order);
    OBDal.getInstance().flush();
  }

  private PaymentResolution resolvePaymentMethodAndAccount(JSONObject paymentJson,
      TerminalContext ctx, String currencyId) throws Exception {
    String kind = paymentJson.optString("kind", null);
    if (StringUtils.isBlank(kind)) {
      return null;
    }

    PaymentResolution terminalMapping = resolvePaymentFromTerminal(kind, ctx.terminal);
    if (terminalMapping != null) {
      return terminalMapping;
    }

    FIN_PaymentMethod method = OBDal.getInstance().get(FIN_PaymentMethod.class, kind);
    if (method == null) {
      method = findPaymentMethodByName(kind);
    }
    if (method == null) {
      return null;
    }

    org.openbravo.model.financialmgmt.payment.FinAccPaymentMethod fapm = FIN_Utility
        .getFinancialAccountPaymentMethod(method.getId(), null, true, currencyId,
            ctx.organization.getId());
    if (fapm == null) {
      return null;
    }
    return new PaymentResolution(method, fapm.getAccount());
  }

  /**
   * OrderLoader creates one outstanding PSD for the gross total before the payment loop (unless
   * layaway / isPaid). Without this, the first PSD is only created inside
   * {@link #resolveOrCreateOutstandingOrderPSD} and was not flushed before
   * {@link FIN_AddPayment#savePayment}, which calls {@code refresh} on the PSD.
   */
  private void ensureInitialOutstandingPsdLikeOrderLoader(Order order, JSONObject orderJson,
      FIN_PaymentSchedule orderSchedule) {
    if (orderJson.optBoolean("isLayaway", false) || orderJson.optBoolean("isPaid", false)) {
      return;
    }
    OBCriteria<FIN_PaymentScheduleDetail> c = OBDal.getInstance()
        .createCriteria(FIN_PaymentScheduleDetail.class);
    c.add(Restrictions.eq(FIN_PaymentScheduleDetail.PROPERTY_ORDERPAYMENTSCHEDULE, orderSchedule));
    c.add(Restrictions.isNull(FIN_PaymentScheduleDetail.PROPERTY_PAYMENTDETAILS));
    c.setFilterOnReadableOrganization(false);
    if (!c.list().isEmpty()) {
      return;
    }
    BigDecimal gross = order.getGrandTotalAmount();
    if (gross == null || gross.compareTo(BigDecimal.ZERO) == 0) {
      return;
    }
    FIN_AddPayment.createPSD(gross, orderSchedule, null, order.getOrganization(),
        order.getBusinessPartner());
  }

  /**
   * Same resolution as OrderLoader.handlePayments: {@code kind} is the OBPOS_App_Payment
   * {@code Value} (search key), e.g. {@code OBPOS_payment.cash}, not a UUID. The previous SQL
   * compared {@code obpos_app_payment_id} to {@code kind}, which is invalid and prevented payments.
   */
  private PaymentResolution resolvePaymentFromTerminal(String kind, OBPOSApplications terminal) {
    if (terminal == null || StringUtils.isBlank(kind)) {
      return null;
    }
    OBCriteria<OBPOSAppPayment> obc = OBDal.getInstance().createCriteria(OBPOSAppPayment.class);
    obc.add(Restrictions.eq(OBPOSAppPayment.PROPERTY_SEARCHKEY, kind));
    obc.add(Restrictions.eq(OBPOSAppPayment.PROPERTY_OBPOSAPPLICATIONS, terminal));
    obc.setFilterOnActive(false);
    obc.setMaxResults(1);
    OBPOSAppPayment appPayment = (OBPOSAppPayment) obc.uniqueResult();
    if (appPayment == null || appPayment.getFinancialAccount() == null) {
      return null;
    }
    TerminalTypePaymentMethod terminalType = appPayment.getPaymentMethod();
    if (terminalType == null || terminalType.getPaymentMethod() == null) {
      return null;
    }
    return new PaymentResolution(terminalType.getPaymentMethod(), appPayment.getFinancialAccount());
  }

  /**
   * Links order PSD rows to the invoice payment schedule (same row as retail / OrderLoader). Removes
   * invoice-only placeholder PSD rows (no order link, no payment detail) so the invoice plan does not
   * show duplicate lines with empty Payment Details.
   *
   * @return the invoice {@link FIN_PaymentSchedule} used for PSD linking, or null if no invoice
   */
  private FIN_PaymentSchedule linkOrderPsdToInvoicePaymentScheduleIfInvoiceExists(Order order,
      FIN_PaymentSchedule orderSchedule) {
    if (orderSchedule == null) {
      return null;
    }
    OBCriteria<Invoice> invCrit = OBDal.getInstance().createCriteria(Invoice.class);
    invCrit.add(Restrictions.eq(Invoice.PROPERTY_SALESORDER, order));
    invCrit.setMaxResults(1);
    Invoice invoice = (Invoice) invCrit.uniqueResult();
    if (invoice == null) {
      return null;
    }
    OBDal.getInstance().refresh(invoice);
    OBDal.getInstance().refresh(order);
    BigDecimal gross = invoice.getGrandTotalAmount();
    if (gross == null || gross.compareTo(BigDecimal.ZERO) == 0) {
      gross = order.getGrandTotalAmount();
    }
    if (gross == null || gross.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    if (invoice.getGrandTotalAmount() == null
        || invoice.getGrandTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
      invoice.setGrandTotalAmount(gross);
      if (order.getSummedLineAmount() != null) {
        invoice.setSummedLineAmount(order.getSummedLineAmount());
      }
      OBDal.getInstance().save(invoice);
    }
    FIN_PaymentSchedule invoiceSchedule;
    List<FIN_PaymentSchedule> invSchedules = invoice.getFINPaymentScheduleList();
    if (invSchedules != null && !invSchedules.isEmpty()) {
      invoiceSchedule = invSchedules.get(0);
    } else {
      invoiceSchedule = OBProvider.getInstance().get(FIN_PaymentSchedule.class);
      invoiceSchedule.setOrganization(invoice.getOrganization());
      invoiceSchedule.setCurrency(order.getCurrency());
      invoiceSchedule.setInvoice(invoice);
      invoiceSchedule.setFinPaymentmethod(order.getPaymentMethod());
      invoiceSchedule.setAmount(gross);
      invoiceSchedule.setOutstandingAmount(gross);
      invoiceSchedule.setDueDate(order.getOrderDate());
      invoiceSchedule.setExpectedDate(order.getOrderDate());
      if (order.getFINPaymentPriority() != null) {
        invoiceSchedule.setFINPaymentPriority(order.getFINPaymentPriority());
      }
      if (ModelProvider.getInstance().getEntity(FIN_PaymentSchedule.class).hasProperty("origDueDate")) {
        invoiceSchedule.set("origDueDate", invoiceSchedule.getDueDate());
      }
      invoice.getFINPaymentScheduleList().add(invoiceSchedule);
      OBDal.getInstance().save(invoiceSchedule);
    }

    removeOrphanInvoiceOnlyPsdPlaceholders(invoiceSchedule);

    OBCriteria<FIN_PaymentScheduleDetail> psdCrit = OBDal.getInstance()
        .createCriteria(FIN_PaymentScheduleDetail.class);
    psdCrit.add(
        Restrictions.eq(FIN_PaymentScheduleDetail.PROPERTY_ORDERPAYMENTSCHEDULE, orderSchedule));
    psdCrit.add(Restrictions.isNull(FIN_PaymentScheduleDetail.PROPERTY_PAYMENTDETAILS));
    psdCrit.add(Restrictions.isNull(FIN_PaymentScheduleDetail.PROPERTY_INVOICEPAYMENTSCHEDULE));
    psdCrit.setFilterOnReadableOrganization(false);
    for (FIN_PaymentScheduleDetail psd : psdCrit.list()) {
      psd.setInvoicePaymentSchedule(invoiceSchedule);
      OBDal.getInstance().save(psd);
    }
    OBDal.getInstance().save(invoiceSchedule);
    OBDal.getInstance().save(invoice);
    return invoiceSchedule;
  }

  /**
   * Triggers / APR can create invoice PSD rows with only {@code fin_payment_schedule_invoice} set.
   * Those compete with the shared order+invoice PSD and never receive {@code fin_payment_detail_id},
   * which yields "Number of Payments: 0" and an empty Payment Details tab.
   */
  private void removeOrphanInvoiceOnlyPsdPlaceholders(FIN_PaymentSchedule invoiceSchedule) {
    if (invoiceSchedule == null) {
      return;
    }
    List<FIN_PaymentScheduleDetail> invoicePsdList = invoiceSchedule
        .getFINPaymentScheduleDetailInvoicePaymentScheduleList();
    if (invoicePsdList == null || invoicePsdList.isEmpty()) {
      return;
    }
    for (FIN_PaymentScheduleDetail psd : new ArrayList<>(invoicePsdList)) {
      if (psd == null) {
        continue;
      }
      if (psd.getOrderPaymentSchedule() != null) {
        continue;
      }
      if (psd.getPaymentDetails() != null) {
        continue;
      }
      OBDal.getInstance().remove(psd);
    }
  }

  private FIN_PaymentMethod findPaymentMethodByName(String name) {
    OBCriteria<FIN_PaymentMethod> criteria = OBDal.getInstance()
        .createCriteria(FIN_PaymentMethod.class);
    criteria.add(Restrictions.eq(FIN_PaymentMethod.PROPERTY_NAME, name));
    criteria.setMaxResults(1);
    return (FIN_PaymentMethod) criteria.uniqueResult();
  }

  private FIN_PaymentMethod resolveOrderPaymentMethod(JSONObject orderJson, TerminalContext ctx,
      Currency currency) throws Exception {
    JSONArray payments = orderJson.optJSONArray("payments");
    if (payments == null || payments.length() == 0) {
      return null;
    }
    for (int i = 0; i < payments.length(); i++) {
      JSONObject paymentJson = payments.getJSONObject(i);
      if (paymentJson.optBoolean("changePayment", false)) {
        continue;
      }
      PaymentResolution resolution = resolvePaymentMethodAndAccount(paymentJson, ctx,
          currency.getId());
      if (resolution != null) {
        return resolution.paymentMethod;
      }
    }
    return null;
  }

  private TaxRate resolveLineTax(JSONObject lineJson, Order order, TerminalContext ctx,
      Product product) throws Exception {
    JSONObject taxLines = lineJson.optJSONObject("taxLines");
    if (taxLines != null && taxLines.length() > 0) {
      @SuppressWarnings("unchecked")
      java.util.Iterator<String> keys = taxLines.keys();
      if (keys.hasNext()) {
        JSONObject firstTax = taxLines.optJSONObject(keys.next());
        if (firstTax != null) {
          BigDecimal rate = asBigDecimal(firstTax, "rate", null);
          if (rate != null) {
            TaxRate taxByRate = findTaxByRate(rate, order, ctx);
            if (taxByRate != null) {
              return taxByRate;
            }
          }
        }
      }
    }

    TaxRate taxByEngine = findTaxByEngine(order, product);
    if (taxByEngine != null) {
      return taxByEngine;
    }

    TaxRate taxByCategory = findTaxByProductTaxCategory(order, ctx, product);
    if (taxByCategory != null) {
      return taxByCategory;
    }

    String productKey = product != null ? product.getSearchKey() : "<unknown>";
    throw new OBException("Cannot resolve tax for order line. product=" + productKey + ", order="
        + order.getDocumentNo() + ", org=" + order.getOrganization().getId());
  }

  private TaxRate findTaxByRate(BigDecimal rate, Order order, TerminalContext ctx)
      throws Exception {
    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select c_tax_id from c_tax where isactive='Y' and rate = ? "
        + "and ad_client_id = ? and ad_org_id in ('0', ?) "
        + "order by case when ad_org_id = ? then 0 else 1 end";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setBigDecimal(1, rate);
      ps.setString(2, ctx.client.getId());
      ps.setString(3, order.getOrganization().getId());
      ps.setString(4, order.getOrganization().getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return OBDal.getInstance().get(TaxRate.class, rs.getString(1));
        }
      }
    }
    return null;
  }

  private TaxRate findTaxByEngine(Order order, Product product)
      throws IOException, ServletException {
    if (product == null) {
      return null;
    }
    String bpBillToLocationId = order.getInvoiceAddress() != null
        ? order.getInvoiceAddress().getId()
        : order.getPartnerAddress() != null ? order.getPartnerAddress().getId() : "";
    String bpLocationId = order.getPartnerAddress() != null ? order.getPartnerAddress().getId()
        : "";
    String warehouseId = order.getWarehouse() != null ? order.getWarehouse().getId() : "";
    String projectId = order.getProject() != null ? order.getProject().getId() : "";
    String promisedDate = DateFormatUtils.format(order.getOrderDate(),
        OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty("dateFormat.java"));

    String taxId = Tax.get(new DalConnectionProvider(), product.getId(), promisedDate,
        order.getOrganization().getId(), warehouseId, bpBillToLocationId, bpLocationId, projectId,
        order.isSalesTransaction());
    if (StringUtils.isBlank(taxId)) {
      return null;
    }
    return OBDal.getInstance().get(TaxRate.class, taxId);
  }

  private TaxRate findTaxByProductTaxCategory(Order order, TerminalContext ctx, Product product)
      throws Exception {
    if (product == null || product.getTaxCategory() == null) {
      return null;
    }
    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select c_tax_id from c_tax where isactive='Y' and c_taxcategory_id = ? "
        + "and ad_client_id = ? and ad_org_id in ('0', ?) "
        + "order by case when ad_org_id = ? then 0 else 1 end, validfrom desc nulls last";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, product.getTaxCategory().getId());
      ps.setString(2, ctx.client.getId());
      ps.setString(3, order.getOrganization().getId());
      ps.setString(4, order.getOrganization().getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return OBDal.getInstance().get(TaxRate.class, rs.getString(1));
        }
      }
    }
    return null;
  }

  private Product resolveProduct(JSONObject lineJson) {
    String productKey = lineJson.optString("product", null);
    if (StringUtils.isBlank(productKey)) {
      throw new OBException("Order line without product search key");
    }

    OBCriteria<Product> criteria = OBDal.getInstance().createCriteria(Product.class);
    criteria.add(Restrictions.eq(Product.PROPERTY_SEARCHKEY, productKey));
    criteria.setMaxResults(1);
    Product product = (Product) criteria.uniqueResult();
    if (product == null) {
      throw new OBException("Product not found by search key: " + productKey);
    }
    return product;
  }

  private BusinessPartner resolveBusinessPartner(JSONObject orderJson, TerminalContext ctx) {
    String bpId = orderJson.optString("businessPartnerId", null);
    if (isLikelyId(bpId)) {
      BusinessPartner bp = OBDal.getInstance().get(BusinessPartner.class, bpId);
      if (bp != null) {
        return bp;
      }
    }

    String bpSearchKey = orderJson.optString("businessPartner", null);
    if (StringUtils.isNotBlank(bpSearchKey)) {
      OBCriteria<BusinessPartner> criteria = OBDal.getInstance()
          .createCriteria(BusinessPartner.class);
      criteria.add(Restrictions.eq(BusinessPartner.PROPERTY_SEARCHKEY, bpSearchKey));
      criteria.setMaxResults(1);
      BusinessPartner bp = (BusinessPartner) criteria.uniqueResult();
      if (bp != null) {
        return bp;
      }
    }

    if (isLikelyId(ctx.defaultBusinessPartnerId)) {
      BusinessPartner terminalBp = OBDal.getInstance()
          .get(BusinessPartner.class, ctx.defaultBusinessPartnerId);
      if (terminalBp != null) {
        return terminalBp;
      }
    }

    throw new OBException("Cannot resolve Business Partner for order");
  }

  private Location resolveBusinessPartnerLocation(JSONObject orderJson, BusinessPartner bp,
      TerminalContext ctx) {
    String locationId = firstNonBlank(orderJson.optString("locId", null),
        orderJson.optString("shipLocId", null), ctx.defaultBusinessPartnerLocationId);
    if (isLikelyId(locationId)) {
      Location loc = OBDal.getInstance().get(Location.class, locationId);
      if (loc != null) {
        return loc;
      }

      // If payload provides geography location id, resolve matching BP location.
      org.openbravo.model.common.geography.Location geoLoc = OBDal.getInstance()
          .get(org.openbravo.model.common.geography.Location.class, locationId);
      if (geoLoc != null) {
        for (Location bpLoc : bp.getBusinessPartnerLocationList()) {
          if (bpLoc != null && bpLoc.getLocationAddress() != null
              && locationId.equals(bpLoc.getLocationAddress().getId())) {
            return bpLoc;
          }
        }
      }
    }

    if (bp.getBusinessPartnerLocationList() != null
        && !bp.getBusinessPartnerLocationList().isEmpty()) {
      return bp.getBusinessPartnerLocationList().get(0);
    }

    throw new OBException("Cannot resolve Business Partner location");
  }

  private Warehouse resolveWarehouse(TerminalContext ctx) throws Exception {
    if (isLikelyId(ctx.warehouseId)) {
      Warehouse wh = OBDal.getInstance().get(Warehouse.class, ctx.warehouseId);
      if (wh != null) {
        return wh;
      }
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select m_warehouse_id from m_warehouse where isactive='Y' and ad_org_id = ? order by created";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, ctx.organization.getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Warehouse wh = OBDal.getInstance().get(Warehouse.class, rs.getString(1));
          if (wh != null) {
            return wh;
          }
        }
      }
    }
    throw new OBException("Cannot resolve warehouse for org " + ctx.organization.getIdentifier());
  }

  private PriceList resolvePriceList(JSONObject orderJson, TerminalContext ctx) throws Exception {
    String priceListId = orderJson.optString("priceList", null);
    if (isLikelyId(priceListId)) {
      PriceList pl = OBDal.getInstance().get(PriceList.class, priceListId);
      if (pl != null) {
        return pl;
      }
    }

    if (isLikelyId(ctx.priceListId)) {
      PriceList pl = OBDal.getInstance().get(PriceList.class, ctx.priceListId);
      if (pl != null) {
        return pl;
      }
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select m_pricelist_id from m_pricelist where isactive='Y' and issopricelist='Y' "
        + "and ad_client_id=? and ad_org_id in ('0', ?) order by case when ad_org_id=? then 0 else 1 end";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, ctx.client.getId());
      ps.setString(2, ctx.organization.getId());
      ps.setString(3, ctx.organization.getId());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          PriceList pl = OBDal.getInstance().get(PriceList.class, rs.getString(1));
          if (pl != null) {
            return pl;
          }
        }
      }
    }
    throw new OBException("Cannot resolve price list for order");
  }

  private Currency resolveCurrency(JSONObject orderJson, PriceList priceList) {
    String currencyId = orderJson.optString("currency", null);
    if (isLikelyId(currencyId)) {
      Currency currency = OBDal.getInstance().get(Currency.class, currencyId);
      if (currency != null) {
        return currency;
      }
    }
    return priceList.getCurrency();
  }

  private PaymentTerm resolvePaymentTerm(BusinessPartner bp) throws Exception {
    if (bp.getPaymentTerms() != null) {
      return bp.getPaymentTerms();
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select c_paymentterm_id from c_paymentterm where isactive='Y' order by created";
    try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
      if (rs.next()) {
        PaymentTerm pt = OBDal.getInstance().get(PaymentTerm.class, rs.getString(1));
        if (pt != null) {
          return pt;
        }
      }
    }
    throw new OBException("Cannot resolve payment term");
  }

  private Order findExistingOrder(String documentNo, String orgId) {
    if (StringUtils.isBlank(documentNo)) {
      return null;
    }
    OBCriteria<Order> criteria = OBDal.getInstance().createCriteria(Order.class);
    criteria.add(Restrictions.eq(Order.PROPERTY_DOCUMENTNO, documentNo));
    criteria.add(Restrictions.eq(Order.PROPERTY_ORGANIZATION,
        OBDal.getInstance().get(Organization.class, orgId)));
    criteria.add(Restrictions.eq(Order.PROPERTY_SALESTRANSACTION, true));
    criteria.setMaxResults(1);
    return (Order) criteria.uniqueResult();
  }

  private TerminalContext resolveTerminalContext(String terminalSearchKey) throws Exception {
    if (StringUtils.isBlank(terminalSearchKey)) {
      throw new OBException("Missing posTerminal in OCOrder envelope");
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select a.obpos_applications_id, a.ad_client_id, a.ad_org_id, a.c_bpartner_id, a.obpos_c_bpartner_loc_id, "
        + "ap.fin_financial_account_id " + "from obpos_applications a "
        + "left join obpos_app_payment ap on ap.obpos_applications_id = a.obpos_applications_id "
        + "and ap.isactive='Y' " + "where a.value = ? " + "order by ap.line nulls last";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, terminalSearchKey);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new OBException("Terminal not found by search key: " + terminalSearchKey);
        }

        String terminalId = rs.getString(1);
        String clientId = rs.getString(2);
        String orgId = rs.getString(3);
        String bpId = rs.getString(4);
        String bpLocId = rs.getString(5);
        String finAccountId = rs.getString(6);

        Organization org = OBDal.getInstance().get(Organization.class, orgId);
        if (org == null) {
          throw new OBException("Organization not found for terminal " + terminalSearchKey);
        }
        Client client = OBDal.getInstance().get(Client.class, clientId);
        if (client == null) {
          throw new OBException("Client not found for terminal " + terminalSearchKey);
        }
        OBPOSApplications terminal = OBDal.getInstance().get(OBPOSApplications.class, terminalId);
        if (terminal == null) {
          throw new OBException("OBPOSApplications not found for terminal " + terminalSearchKey);
        }
        return new TerminalContext(terminalSearchKey, terminal, client, org, bpId, bpLocId, null,
            null, finAccountId);
      }
    }
  }

  private BigDecimal resolveLineUnitNetPrice(JSONObject lineJson, BigDecimal qty) {
    BigDecimal unit = asBigDecimal(lineJson, "baseNetUnitPrice", null);
    if (unit == null) {
      unit = asBigDecimal(lineJson, "price", null);
    }
    if (unit == null) {
      BigDecimal lineNet = asBigDecimal(lineJson, "lineNetAmount", BigDecimal.ZERO);
      unit = qty.compareTo(BigDecimal.ZERO) == 0 ? lineNet
          : lineNet.divide(qty, 6, java.math.RoundingMode.HALF_UP);
    }
    return unit;
  }

  private BigDecimal resolveLineNetAmount(JSONObject lineJson, BigDecimal qty,
      BigDecimal unitPrice) {
    BigDecimal lineNet = asBigDecimal(lineJson, "lineNetAmount", null);
    if (lineNet == null) {
      lineNet = asBigDecimal(lineJson, "netAmount", null);
    }
    if (lineNet == null) {
      lineNet = unitPrice.multiply(qty);
    }
    return lineNet;
  }

  private BigDecimal resolveLineGrossUnitPrice(JSONObject lineJson, BigDecimal qty,
      BigDecimal unitPrice, BigDecimal lineNet) {
    BigDecimal grossUnit = asBigDecimal(lineJson, "grossUnitPrice", null);
    if (grossUnit == null) {
      grossUnit = asBigDecimal(lineJson, "baseGrossUnitPrice", null);
    }
    if (grossUnit == null) {
      BigDecimal grossLine = asBigDecimal(lineJson, "lineGrossAmount", null);
      if (grossLine == null) {
        grossLine = asBigDecimal(lineJson, "grossAmount", lineNet);
      }
      grossUnit = qty.compareTo(BigDecimal.ZERO) == 0 ? grossLine
          : grossLine.divide(qty, 6, java.math.RoundingMode.HALF_UP);
    }
    return grossUnit;
  }

  private BigDecimal resolveLineGrossListPrice(JSONObject lineJson, BigDecimal grossUnitPrice) {
    return asBigDecimal(lineJson, "grossListPrice",
        asBigDecimal(lineJson, "baseGrossUnitPrice", grossUnitPrice));
  }

  private BigDecimal resolveBaseGrossUnitPrice(JSONObject lineJson, BigDecimal grossUnitPrice) {
    return asBigDecimal(lineJson, "baseGrossUnitPrice",
        asBigDecimal(lineJson, "grossListPrice", grossUnitPrice));
  }

  private BigDecimal resolveLineGrossAmount(JSONObject lineJson, BigDecimal qty,
      BigDecimal grossUnitPrice, BigDecimal lineNet) {
    BigDecimal gross = asBigDecimal(lineJson, "lineGrossAmount", null);
    if (gross == null) {
      gross = asBigDecimal(lineJson, "grossAmount", null);
    }
    if (gross == null) {
      gross = grossUnitPrice.multiply(qty);
    }
    if (gross == null) {
      return lineNet;
    }
    return gross;
  }

  private BigDecimal resolvePaymentAmount(JSONObject paymentJson) {
    BigDecimal amount = asBigDecimal(paymentJson, "origAmount", null);
    if (amount == null) {
      amount = asBigDecimal(paymentJson, "paid", null);
    }
    if (amount == null) {
      amount = asBigDecimal(paymentJson, "amount", BigDecimal.ZERO);
    }
    return amount;
  }

  private BigDecimal asBigDecimal(JSONObject json, String key, BigDecimal defaultValue) {
    if (!json.has(key) || json.isNull(key)) {
      return defaultValue;
    }
    Object raw = json.opt(key);
    if (raw == null) {
      return defaultValue;
    }
    try {
      if (raw instanceof Number) {
        return new BigDecimal(raw.toString());
      }
      String txt = raw.toString();
      if (StringUtils.isBlank(txt)) {
        return defaultValue;
      }
      return new BigDecimal(txt);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private void validateRequiredOrderFields(JSONObject orderJson) {
    requireNumericField(orderJson, "grossAmount", "order");
    requireNumericField(orderJson, "netAmount", "order");
  }

  private void validateRequiredLineFields(JSONObject orderJson) throws Exception {
    JSONArray lines = orderJson.optJSONArray("lines");
    if (lines == null || lines.length() == 0) {
      throw new OBException("Order has no lines");
    }
    for (int i = 0; i < lines.length(); i++) {
      JSONObject line = lines.getJSONObject(i);
      String context = "order line " + i;
      requireNumericField(line, "qty", context);
      requireAnyNumericField(line, context, "baseNetUnitPrice", "price", "lineNetAmount");
      requireAnyNumericField(line, context, "lineGrossAmount", "grossAmount");
      requireAnyNumericField(line, context, "grossUnitPrice", "baseGrossUnitPrice",
          "grossListPrice");
      if (!line.has("product") || StringUtils.isBlank(line.optString("product", null))) {
        throw new OBException("Missing product in " + context);
      }
    }
  }

  private void requireNumericField(JSONObject json, String key, String context) {
    if (!json.has(key) || json.isNull(key) || asBigDecimal(json, key, null) == null) {
      throw new OBException("Missing required numeric field '" + key + "' in " + context);
    }
  }

  private void requireAnyNumericField(JSONObject json, String context, String... keys) {
    for (String key : keys) {
      if (json.has(key) && !json.isNull(key) && asBigDecimal(json, key, null) != null) {
        return;
      }
    }
    throw new OBException(
        "Missing required numeric fields " + java.util.Arrays.toString(keys) + " in " + context);
  }

  private Date parseDate(String raw) {
    if (StringUtils.isBlank(raw)) {
      return new Date();
    }
    try {
      return Date.from(OffsetDateTime.parse(raw).toInstant());
    } catch (Exception e) {
      try {
        return new Date(Timestamp.valueOf(raw).getTime());
      } catch (Exception ignored) {
        return new Date();
      }
    }
  }

  private boolean isLikelyId(String value) {
    return StringUtils.isNotBlank(value) && value.length() == 32;
  }

  private String generateUuid32() {
    return UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }

  private String firstNonBlank(String... values) {
    for (String v : values) {
      if (StringUtils.isNotBlank(v)) {
        return v;
      }
    }
    return null;
  }

  private static class PersistedOrderLine {
    final OrderLine orderLine;
    final Product product;
    final JSONObject lineJson;
    final String payloadLineId;

    PersistedOrderLine(OrderLine orderLine, Product product, JSONObject lineJson,
        String payloadLineId) {
      this.orderLine = orderLine;
      this.product = product;
      this.lineJson = lineJson;
      this.payloadLineId = payloadLineId;
    }
  }

  private static class ProductServiceConfig {
    final boolean linkedToProduct;
    final String quantityRule;

    ProductServiceConfig(boolean linkedToProduct, String quantityRule) {
      this.linkedToProduct = linkedToProduct;
      this.quantityRule = quantityRule;
    }
  }

  private static class PaymentResolution {
    final FIN_PaymentMethod paymentMethod;
    final FIN_FinancialAccount financialAccount;

    PaymentResolution(FIN_PaymentMethod paymentMethod, FIN_FinancialAccount financialAccount) {
      this.paymentMethod = paymentMethod;
      this.financialAccount = financialAccount;
    }
  }

  private static class TerminalContext {
    final String terminalSearchKey;
    final OBPOSApplications terminal;
    final Client client;
    final Organization organization;
    final String defaultBusinessPartnerId;
    final String defaultBusinessPartnerLocationId;
    final String warehouseId;
    final String priceListId;
    final String defaultFinancialAccountId;

    TerminalContext(String terminalSearchKey, OBPOSApplications terminal, Client client,
        Organization organization, String defaultBusinessPartnerId,
        String defaultBusinessPartnerLocationId, String warehouseId, String priceListId,
        String defaultFinancialAccountId) {
      this.terminalSearchKey = terminalSearchKey;
      this.terminal = terminal;
      this.client = client;
      this.organization = organization;
      this.defaultBusinessPartnerId = defaultBusinessPartnerId;
      this.defaultBusinessPartnerLocationId = defaultBusinessPartnerLocationId;
      this.warehouseId = warehouseId;
      this.priceListId = priceListId;
      this.defaultFinancialAccountId = defaultFinancialAccountId;
    }
  }

  private static class OrderCreationResult {
    final Order order;
    final boolean duplicate;

    OrderCreationResult(Order order, boolean duplicate) {
      this.order = order;
      this.duplicate = duplicate;
    }
  }
}
