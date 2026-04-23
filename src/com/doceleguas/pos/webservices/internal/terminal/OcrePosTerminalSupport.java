/*
 ************************************************************************************
 * Copyright (C) 2012-2022 Openbravo S.L.U. (original POSUtils logic)
 * Copyright (C) 2026 Doceleguas (this file)
 * Licensed under the Openbravo Public License version 1.0 where applicable.
 *
 * Static helpers extracted from {@code org.openbravo.retail.posterminal.POSUtils} so that
 * {@code com.doceleguas.pos.webservices} does not depend on that retail utility class.
 * Entity types remain in {@code org.openbravo.retail.posterminal} / src-gen.
 ************************************************************************************
 */
package com.doceleguas.pos.webservices.internal.terminal;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.model.pricing.pricelist.PriceListVersion;
import org.openbravo.retail.config.OBRETCOProductList;
import org.openbravo.retail.posterminal.OBPOSErrors;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.TerminalType;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.importprocess.ImportEntry;

/**
 * Terminal / pricing / sequence helpers (former {@code POSUtils} usage in this module).
 */
public final class OcrePosTerminalSupport {

  private static final Logger log = LogManager.getLogger();

  private OcrePosTerminalSupport() {
  }

  public static OBPOSApplications getTerminalById(String posTerminalId) {
    try {
      OBContext.setAdminMode(false);
      return OBDal.getInstance().get(OBPOSApplications.class, posTerminalId);
    } catch (Exception e) {
      log.error("Error getting terminal by id: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static List<String> getOrgList(String searchKey) {
    try {
      OBContext.setAdminMode(false);
      OBPOSApplications terminal = getTerminal(searchKey);
      if (terminal == null) {
        throw new OBException("No terminal with searchKey: " + searchKey);
      }
      return OBContext.getOBContext()
          .getOrganizationStructureProvider()
          .getParentList(terminal.getOrganization().getId(), true);
    } catch (Exception e) {
      log.error("Error getting store list: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static OBPOSApplications getTerminal(String searchKey) {
    try {
      OBContext.setAdminMode(false);
      OBQuery<OBPOSApplications> obq = OBDal.getInstance()
          .createQuery(OBPOSApplications.class, "searchKey = :value");
      obq.setNamedParameter("value", searchKey);
      List<OBPOSApplications> posApps = obq.list();
      if (posApps.isEmpty()) {
        return null;
      }
      return posApps.get(0);
    } catch (Exception e) {
      log.error("Error getting terminal: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static List<String> getOrgListByTerminalId(String terminalId) {
    try {
      OBContext.setAdminMode(false);
      OBPOSApplications terminal = getTerminalById(terminalId);
      if (terminal == null) {
        throw new OBException("No terminal with id: " + terminalId);
      }
      return OBContext.getOBContext()
          .getOrganizationStructureProvider()
          .getParentList(terminal.getOrganization().getId(), true);
    } catch (Exception e) {
      log.error("Error getting store list: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static List<String> getStoreList(String orgId) {
    return OBContext.getOBContext().getOrganizationStructureProvider().getParentList(orgId, true);
  }

  public static PriceList getPriceListByOrgId(String orgId) {
    try {
      OBContext.setAdminMode(false);
      final List<String> orgList = getStoreList(orgId);
      for (String currentOrgId : orgList) {
        final Organization org = OBDal.getInstance().get(Organization.class, currentOrgId);
        if (org.getObretcoPricelist() != null) {
          return org.getObretcoPricelist();
        }
      }
    } catch (Exception e) {
      log.error("Error getting PriceList by Org ID: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static PriceList getPriceListByTerminal(String searchKey) {
    try {
      OBContext.setAdminMode(false);
      final List<String> orgList = getOrgList(searchKey);
      for (String orgId : orgList) {
        final Organization org = OBDal.getInstance().get(Organization.class, orgId);
        if (org.getObretcoPricelist() != null) {
          return org.getObretcoPricelist();
        }
      }
    } catch (Exception e) {
      log.error("Error getting PriceList by Terminal value: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static PriceList getPriceListByTerminalId(String terminalId) {
    try {
      OBContext.setAdminMode(false);
      final List<String> orgList = getOrgListByTerminalId(terminalId);
      for (String orgId : orgList) {
        final Organization org = OBDal.getInstance().get(Organization.class, orgId);
        if (org.getObretcoPricelist() != null) {
          return org.getObretcoPricelist();
        }
      }
    } catch (Exception e) {
      log.error("Error getting PriceList by Terminal id: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static PriceListVersion getPriceListVersionForPriceList(String priceListId,
      Date terminalDate) {
    try {
      OBContext.setAdminMode(true);
      Query<PriceListVersion> priceListVersionQuery = OBDal.getInstance()
          .getSession()
          .createQuery("from PricingPriceListVersion AS plv "
              + "where plv.priceList.id = :priceList and plv.active=true "
              + "and plv.validFromDate = (select max(pplv.validFromDate) "
              + "from PricingPriceListVersion as pplv where pplv.active=true "
              + "and pplv.priceList.id = :priceList "
              + "and pplv.validFromDate <= :terminalDate)", PriceListVersion.class);
      priceListVersionQuery.setParameter("priceList", priceListId);
      priceListVersionQuery.setParameter("terminalDate", terminalDate);
      for (PriceListVersion plv : priceListVersionQuery.list()) {
        return plv;
      }
    } catch (Exception e) {
      log.error("Error getting PriceListVersion: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static PriceListVersion getPriceListVersionByTerminalId(String terminalId,
      Date terminalDate) {
    PriceList priceList = getPriceListByTerminalId(terminalId);
    if (priceList == null) {
      throw new OBException(Utility.messageBD(new DalConnectionProvider(false),
          "OBPOS_errorLoadingPriceList",
          RequestContext.get().getVariablesSecureApp().getLanguage()));
    }
    return getPriceListVersionForPriceList(priceList.getId(), terminalDate);
  }

  public static PriceListVersion getPriceListVersionByOrgId(String orgId, Date terminalDate) {
    PriceList priceList = getPriceListByOrgId(orgId);
    if (priceList == null) {
      return null;
    }
    return getPriceListVersionForPriceList(priceList.getId(), terminalDate);
  }

  /**
   * @return product list id for assortment SQL, or null
   */
  public static String getProductListIdForPosterminalId(String posterminalId) {
    OBRETCOProductList pl = getProductListByPosterminalId(posterminalId);
    return pl == null ? null : pl.getId();
  }

  public static OBRETCOProductList getProductListByPosterminalId(String posterminalId) {
    try {
      OBContext.setAdminMode(false);
      OBPOSApplications posterminal = getTerminalById(posterminalId);
      if (posterminal == null) {
        return null;
      }
      TerminalType terminalType = OBDal.getInstance()
          .get(TerminalType.class, posterminal.getObposTerminaltype().getId());
      if (terminalType.getObretcoProductlist() != null) {
        return terminalType.getObretcoProductlist();
      } else {
        final List<String> orgList = getStoreList(posterminal.getOrganization().getId());
        for (String currentOrgId : orgList) {
          final Organization org = OBDal.getInstance().get(Organization.class, currentOrgId);
          if (org.getObretcoProductlist() != null) {
            return org.getObretcoProductlist();
          }
        }
      }
    } catch (Exception e) {
      log.error("Error getting ProductList by Posterminal: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return null;
  }

  public static Warehouse getWarehouseForTerminal(OBPOSApplications pOSTerminal) {
    OBContext.setAdminMode(false);
    try {
      Organization org = pOSTerminal.getOrganization();
      OBQuery<OrgWarehouse> warehouses = OBDal.getInstance()
          .createQuery(OrgWarehouse.class,
              " e where e.organization=:org and e.warehouse.active=true order by priority, id");
      warehouses.setNamedParameter("org", org);
      List<OrgWarehouse> warehouseList = warehouses.list();
      if (warehouseList.size() == 0) {
        return null;
      }
      return warehouseList.get(0).getWarehouse();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  public static boolean getPreference(final String preference) {
    OBContext.setAdminMode(false);
    boolean value;
    try {
      value = org.apache.commons.lang.StringUtils
          .equals(Preferences.getPreferenceValue(preference, true,
              OBContext.getOBContext().getCurrentClient(),
              OBContext.getOBContext().getCurrentOrganization(), OBContext.getOBContext().getUser(),
              OBContext.getOBContext().getRole(), null), "Y");
    } catch (PropertyException e) {
      value = false;
    } finally {
      OBContext.restorePreviousMode();
    }
    return value;
  }

  public static boolean isCrossStoreEnabled(final OBPOSApplications posTerminal) {
    org.openbravo.model.common.enterprise.Organization crossOrganization;
    OBContext.setAdminMode(true);
    try {
      crossOrganization = posTerminal.getOrganization().getOBRETCOCrossStoreOrganization();
    } finally {
      OBContext.restorePreviousMode();
    }
    return crossOrganization != null;
  }

  public static boolean filterTaxesFromStoreLocation(OBPOSApplications pos) {
    final boolean organizationWithMultipleLocations = getPreference(
        "OBPOS_allowMultipleLocationsPerStore");
    return !organizationWithMultipleLocations && !isCrossStoreEnabled(pos);
  }

  public static Long getLastTerminalDocumentSequence(final OBPOSApplications posTerminal,
      final String sequenceName, final boolean isInvoiceSequence) {
    final long maxNumberInTerminal = posTerminal.getEntity().hasProperty(sequenceName)
        ? (Long) ObjectUtils.defaultIfNull(posTerminal.get(sequenceName), 0L)
        : 0L;

    final List<OBPOSErrors> errors = OBDal.getInstance()
        .createCriteria(OBPOSErrors.class)
        .add(Restrictions.eq(OBPOSErrors.PROPERTY_OBPOSAPPLICATIONS, posTerminal))
        .add(Restrictions.eq(OBPOSErrors.PROPERTY_TYPEOFDATA, "Order"))
        .add(Restrictions.eq(OBPOSErrors.PROPERTY_ORDERSTATUS, "N"))
        .list();

    long maxNumberInErrors = errors.stream().mapToLong(error -> {
      try {
        JSONObject jsonError = new JSONObject(error.getJsoninfo());
        if (isInvoiceSequence && jsonError.has("calculatedInvoice")) {
          jsonError = jsonError.getJSONObject("calculatedInvoice");
        }
        if (jsonError.has("obposSequencename") && jsonError.has("obposSequencenumber")
            && jsonError.getString("obposSequencename").equals(sequenceName)) {
          return jsonError.getLong("obposSequencenumber");
        }
        return 0;
      } catch (Exception e) {
        return 0;
      }
    }).max().orElse(0L);

    return Math.max(maxNumberInTerminal, maxNumberInErrors);
  }

  /**
   * After a successful native OCOrder import, bumps document-sequence fields on
   * {@link OBPOSApplications} to max(stored, reported), matching retail
   * {@code OrderLoader} behavior and keeping {@link #getLastTerminalDocumentSequence} aligned.
   * <p>
   * Applies the order-level {@code obposSequencename} / {@code obposSequencenumber} and, when
   * present, the same inside {@code calculatedInvoice} (e.g. credit sales with a separate
   * invoice number).
   */
  public static void applyReportedDocumentSequences(OBPOSApplications terminal, JSONObject orderJson) {
    if (terminal == null || orderJson == null) {
      return;
    }
    applyOneReportedSequence(terminal, orderJson, false);
    if (orderJson.has("calculatedInvoice") && !orderJson.isNull("calculatedInvoice")) {
      try {
        JSONObject inv = orderJson.getJSONObject("calculatedInvoice");
        applyOneReportedSequence(terminal, inv, true);
      } catch (Exception e) {
        log.warn("applyReportedDocumentSequences: could not read calculatedInvoice: {}", e.getMessage());
      }
    }
  }

  private static void applyOneReportedSequence(OBPOSApplications terminal, JSONObject part,
      boolean fromNestedInvoice) {
    if (part == null) {
      return;
    }
    if (!part.has("obposSequencename") || part.isNull("obposSequencename")
        || !part.has("obposSequencenumber") || part.isNull("obposSequencenumber")) {
      return;
    }
    String sequenceName = part.optString("obposSequencename", null);
    if (StringUtils.isBlank(sequenceName) || !terminal.getEntity().hasProperty(sequenceName)) {
      if (StringUtils.isNotBlank(sequenceName)) {
        log.debug("applyOneReportedSequence: unknown or missing property on terminal: {} (invoice={})",
            sequenceName, fromNestedInvoice);
      }
      return;
    }
    long reported;
    try {
      reported = part.getLong("obposSequencenumber");
    } catch (Exception e) {
      log.debug("applyOneReportedSequence: invalid obposSequencenumber (invoice={}): {}", fromNestedInvoice,
          e.getMessage());
      return;
    }
    if (reported <= 0) {
      return;
    }
    long current = 0L;
    try {
      Object cur = terminal.get(sequenceName);
      if (cur instanceof Number) {
        current = ((Number) cur).longValue();
      } else if (cur != null) {
        current = Long.parseLong(cur.toString());
      }
    } catch (Exception e) {
      current = 0L;
    }
    long next = Math.max(current, reported);
    if (next > current) {
      terminal.set(sequenceName, next);
      OBDal.getInstance().save(terminal);
      log.info("applyOneReportedSequence: terminal={} property={} {} -> {} (fromInvoiceJson={})",
          terminal.getSearchKey(), sequenceName, current, next, fromNestedInvoice);
    }
  }

  public static Date getCurrentDate() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTime();
  }
}
