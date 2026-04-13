/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.orderload.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.advpaymentmngt.dao.AdvPaymentMngtDao;
import org.openbravo.advpaymentmngt.utility.FIN_Utility;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.businesspartner.BusinessPartner;
import org.openbravo.model.common.businesspartner.Location;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.common.uom.UOM;
import org.openbravo.model.financialmgmt.payment.FIN_FinancialAccount;
import org.openbravo.model.financialmgmt.payment.FIN_Payment;
import org.openbravo.model.financialmgmt.payment.FIN_PaymentMethod;
import org.openbravo.model.financialmgmt.payment.PaymentTerm;
import org.openbravo.model.financialmgmt.tax.TaxRate;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.service.json.JsonConstants;

import com.doceleguas.pos.webservices.orderload.spi.OrderPersistencePort;

/**
 * First functional Core/DAL persistence adapter for OCOrder.
 *
 * Scope intentionally limited to standard sales:
 * - order header
 * - order lines
 * - basic FIN_Payment rows (non change/refund lines)
 *
 * Unsupported flows intentionally fail so orchestrator can fallback to retail pipeline.
 */
@ApplicationScoped
public class CoreOrderPersistenceAdapter implements OrderPersistencePort {

  private static final Logger log = LogManager.getLogger();

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
      ensureSupportedStandardSale(orderJson);

      OrderCreationResult orderResult = createOrReuseOrder(orderJson, terminalContext);
      Order order = orderResult.order;
      if (orderResult.duplicate) {
        // Existing order found by documentNo+org. Keep idempotency.
        processedOrders.put(successOrderJson(order, true));
        continue;
      }

      createOrderLines(orderJson, order, terminalContext);
      createBasicPayments(orderJson, order, terminalContext);
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

  private JSONObject successOrderJson(Order order, boolean duplicate) throws Exception {
    JSONObject json = new JSONObject();
    json.put("id", order.getId());
    json.put("documentNo", order.getDocumentNo());
    json.put("duplicate", duplicate);
    json.put("native", true);
    return json;
  }

  private void ensureSupportedStandardSale(JSONObject orderJson) throws Exception {
    if (orderJson.optBoolean("isReturn", false) || orderJson.optBoolean("isQuotation", false)
        || orderJson.optBoolean("isLayaway", false) || orderJson.optBoolean("payOnCredit", false)
        || orderJson.optBoolean("isNewLayaway", false)) {
      throw new OBException("Core adapter supports only standard sale flow for now");
    }

    String step = orderJson.optString("step", "create");
    if (!StringUtils.isEmpty(step) && !"create".equalsIgnoreCase(step)) {
      throw new OBException("Unsupported step for Core adapter: " + step);
    }
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

    DocumentType documentType = FIN_Utility.getDocumentType(ctx.organization, "SOO");
    if (documentType == null) {
      throw new OBException("No SOO document type found for org " + ctx.organization.getIdentifier());
    }

    Order order = OBProvider.getInstance().get(Order.class);
    order.setOrganization(ctx.organization);
    order.setClient(ctx.client);
    order.setDocumentType(documentType);
    order.setTransactionDocument(documentType);
    order.setDocumentNo(StringUtils.isNotBlank(documentNo)
        ? documentNo
        : FIN_Utility.getDocumentNo(ctx.organization, "SOO", "C_Order"));
    order.setAccountingDate(orderDate);
    order.setOrderDate(orderDate);
    order.setWarehouse(warehouse);
    order.setBusinessPartner(bp);
    order.setPartnerAddress(bpLocation);
    order.setPriceList(priceList);
    order.setCurrency(currency);
    order.setSalesTransaction(true);
    order.setSummedLineAmount(asBigDecimal(orderJson, "netAmount", BigDecimal.ZERO));
    order.setGrandTotalAmount(asBigDecimal(orderJson, "grossAmount", BigDecimal.ZERO));
    order.setPaymentTerms(paymentTerm);
    if (orderPaymentMethod != null) {
      order.setPaymentMethod(orderPaymentMethod);
    }

    OBDal.getInstance().save(order);
    OBDal.getInstance().flush();
    return new OrderCreationResult(order, false);
  }

  private void createOrderLines(JSONObject orderJson, Order order, TerminalContext ctx) throws Exception {
    JSONArray lines = orderJson.optJSONArray("lines");
    if (lines == null || lines.length() == 0) {
      throw new OBException("Order has no lines");
    }

    BigDecimal sumNet = BigDecimal.ZERO;
    for (int i = 0; i < lines.length(); i++) {
      JSONObject lineJson = lines.getJSONObject(i);
      Product product = resolveProduct(lineJson);
      UOM uom = product.getUOM();

      BigDecimal qty = asBigDecimal(lineJson, "qty", BigDecimal.ONE);
      if (qty.compareTo(BigDecimal.ZERO) == 0) {
        qty = BigDecimal.ONE;
      }
      BigDecimal unitPrice = resolveLineUnitNetPrice(lineJson, qty);
      BigDecimal listPrice = asBigDecimal(lineJson, "baseNetUnitPrice", unitPrice);
      BigDecimal lineNet = resolveLineNetAmount(lineJson, qty, unitPrice);
      TaxRate tax = resolveLineTax(lineJson, order, ctx);

      OrderLine line = OBProvider.getInstance().get(OrderLine.class);
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
      if (tax != null) {
        line.setTax(tax);
      }
      line.setLineNetAmount(lineNet);

      OBDal.getInstance().save(line);
      sumNet = sumNet.add(lineNet);
    }

    order.setSummedLineAmount(sumNet);
    BigDecimal gross = asBigDecimal(orderJson, "grossAmount", sumNet);
    order.setGrandTotalAmount(gross);
    OBDal.getInstance().save(order);
  }

  private void createBasicPayments(JSONObject orderJson, Order order, TerminalContext ctx) throws Exception {
    JSONArray payments = orderJson.optJSONArray("payments");
    if (payments == null || payments.length() == 0) {
      return;
    }

    AdvPaymentMngtDao paymentDao = new AdvPaymentMngtDao();
    List<FIN_Payment> created = new ArrayList<>();
    for (int i = 0; i < payments.length(); i++) {
      JSONObject paymentJson = payments.getJSONObject(i);
      if (paymentJson.optBoolean("changePayment", false)) {
        continue;
      }

      BigDecimal amount = resolvePaymentAmount(paymentJson);
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }

      PaymentResolution paymentResolution = resolvePaymentMethodAndAccount(paymentJson, ctx,
          order.getCurrency().getId());
      if (paymentResolution == null) {
        log.warn("[OCOrder][core] skipping payment without resolvable method/account. order={}, kind={}",
            order.getDocumentNo(), paymentJson.optString("kind", "<missing>"));
        continue;
      }

      DocumentType paymentDocType = FIN_Utility.getDocumentType(order.getOrganization(), "ARR");
      String paymentDocNo = FIN_Utility.getDocumentNo(order.getOrganization(), "APP", "FIN_Payment");
      Date paymentDate = parseDate(paymentJson.optString("date", null));
      String reference = order.getDocumentNo() + ":" + paymentJson.optString("id", String.valueOf(i));

      FIN_Payment payment = paymentDao.getNewPayment(true, order.getOrganization(), paymentDocType,
          paymentDocNo, order.getBusinessPartner(), paymentResolution.paymentMethod,
          paymentResolution.financialAccount, amount.toPlainString(), paymentDate, reference,
          order.getCurrency(), BigDecimal.ONE, amount);
      created.add(payment);
    }

    if (!created.isEmpty()) {
      OBDal.getInstance().flush();
    }
  }

  private PaymentResolution resolvePaymentMethodAndAccount(JSONObject paymentJson, TerminalContext ctx,
      String currencyId) throws Exception {
    String kind = paymentJson.optString("kind", null);
    if (StringUtils.isBlank(kind)) {
      return null;
    }

    PaymentResolution terminalMapping = resolvePaymentFromTerminal(kind, ctx.terminalSearchKey);
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

  private PaymentResolution resolvePaymentFromTerminal(String kind, String terminalSearchKey)
      throws Exception {
    if (StringUtils.isBlank(terminalSearchKey)) {
      return null;
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select t.fin_paymentmethod_id, p.fin_financial_account_id "
        + "from obpos_app_payment p "
        + "join obpos_app_payment_type t on t.obpos_app_payment_type_id = p.obpos_app_payment_type_id "
        + "join obpos_applications a on a.obpos_applications_id = p.obpos_applications_id "
        + "where p.isactive = 'Y' and a.value = ? and (p.obpos_app_payment_id = ? or p.value = ?) "
        + "order by p.line nulls last";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, terminalSearchKey);
      ps.setString(2, kind);
      ps.setString(3, kind);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        FIN_PaymentMethod method = OBDal.getInstance().get(FIN_PaymentMethod.class, rs.getString(1));
        FIN_FinancialAccount account = OBDal.getInstance().get(FIN_FinancialAccount.class,
            rs.getString(2));
        if (method == null || account == null) {
          return null;
        }
        return new PaymentResolution(method, account);
      }
    }
  }

  private FIN_PaymentMethod findPaymentMethodByName(String name) {
    OBCriteria<FIN_PaymentMethod> criteria = OBDal.getInstance().createCriteria(FIN_PaymentMethod.class);
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
      PaymentResolution resolution = resolvePaymentMethodAndAccount(paymentJson, ctx, currency.getId());
      if (resolution != null) {
        return resolution.paymentMethod;
      }
    }
    return null;
  }

  private TaxRate resolveLineTax(JSONObject lineJson, Order order, TerminalContext ctx) throws Exception {
    JSONObject taxLines = lineJson.optJSONObject("taxLines");
    if (taxLines == null || taxLines.length() == 0) {
      return null;
    }

    @SuppressWarnings("unchecked")
    java.util.Iterator<String> keys = taxLines.keys();
    if (!keys.hasNext()) {
      return null;
    }
    JSONObject firstTax = taxLines.optJSONObject(keys.next());
    if (firstTax == null) {
      return null;
    }

    BigDecimal rate = asBigDecimal(firstTax, "rate", null);
    if (rate == null) {
      return null;
    }

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
      OBCriteria<BusinessPartner> criteria = OBDal.getInstance().createCriteria(BusinessPartner.class);
      criteria.add(Restrictions.eq(BusinessPartner.PROPERTY_SEARCHKEY, bpSearchKey));
      criteria.setMaxResults(1);
      BusinessPartner bp = (BusinessPartner) criteria.uniqueResult();
      if (bp != null) {
        return bp;
      }
    }

    if (isLikelyId(ctx.defaultBusinessPartnerId)) {
      BusinessPartner terminalBp = OBDal.getInstance().get(BusinessPartner.class,
          ctx.defaultBusinessPartnerId);
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

    if (bp.getBusinessPartnerLocationList() != null && !bp.getBusinessPartnerLocationList().isEmpty()) {
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
    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
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
    criteria.add(Restrictions.eq(Order.PROPERTY_ORGANIZATION, OBDal.getInstance().get(
        Organization.class, orgId)));
    criteria.add(Restrictions.eq(Order.PROPERTY_SALESTRANSACTION, true));
    criteria.setMaxResults(1);
    return (Order) criteria.uniqueResult();
  }

  private TerminalContext resolveTerminalContext(String terminalSearchKey) throws Exception {
    if (StringUtils.isBlank(terminalSearchKey)) {
      throw new OBException("Missing posTerminal in OCOrder envelope");
    }

    Connection conn = OBDal.getInstance().getConnection();
    String sql = "select a.ad_client_id, a.ad_org_id, a.c_bpartner_id, a.obpos_c_bpartner_loc_id, "
        + "ap.fin_financial_account_id "
        + "from obpos_applications a "
        + "left join obpos_app_payment ap on ap.obpos_applications_id = a.obpos_applications_id "
        + "and ap.isactive='Y' "
        + "where a.value = ? "
        + "order by ap.line nulls last";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, terminalSearchKey);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new OBException("Terminal not found by search key: " + terminalSearchKey);
        }

        String clientId = rs.getString(1);
        String orgId = rs.getString(2);
        String bpId = rs.getString(3);
        String bpLocId = rs.getString(4);
        String finAccountId = rs.getString(5);

        Organization org = OBDal.getInstance().get(Organization.class, orgId);
        if (org == null) {
          throw new OBException("Organization not found for terminal " + terminalSearchKey);
        }
        org.openbravo.model.ad.system.Client client = OBDal.getInstance()
            .get(org.openbravo.model.ad.system.Client.class, clientId);
        if (client == null) {
          throw new OBException("Client not found for terminal " + terminalSearchKey);
        }
        return new TerminalContext(terminalSearchKey, client, org, bpId, bpLocId, null, null,
            finAccountId);
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
      unit = qty.compareTo(BigDecimal.ZERO) == 0 ? lineNet : lineNet.divide(qty, 6,
          java.math.RoundingMode.HALF_UP);
    }
    return unit;
  }

  private BigDecimal resolveLineNetAmount(JSONObject lineJson, BigDecimal qty, BigDecimal unitPrice) {
    BigDecimal lineNet = asBigDecimal(lineJson, "lineNetAmount", null);
    if (lineNet == null) {
      lineNet = asBigDecimal(lineJson, "netAmount", null);
    }
    if (lineNet == null) {
      lineNet = unitPrice.multiply(qty);
    }
    return lineNet;
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

  private String firstNonBlank(String... values) {
    for (String v : values) {
      if (StringUtils.isNotBlank(v)) {
        return v;
      }
    }
    return null;
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
    final org.openbravo.model.ad.system.Client client;
    final Organization organization;
    final String defaultBusinessPartnerId;
    final String defaultBusinessPartnerLocationId;
    final String warehouseId;
    final String priceListId;
    final String defaultFinancialAccountId;

    TerminalContext(String terminalSearchKey, org.openbravo.model.ad.system.Client client,
        Organization organization, String defaultBusinessPartnerId,
        String defaultBusinessPartnerLocationId, String warehouseId, String priceListId,
        String defaultFinancialAccountId) {
      this.terminalSearchKey = terminalSearchKey;
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
