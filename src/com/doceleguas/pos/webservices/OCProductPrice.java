package com.doceleguas.pos.webservices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.posterminal.POSUtils;

/**
 * Backend model for ProductPrice masterdata.
 *
 * Serves product prices for ALL price lists configured on the terminal,
 * so the POS can look up prices when a Business Partner has a different
 * price list than the terminal's default.
 *
 * Each row represents one (product, priceList) price entry.
 */
public class OCProductPrice extends Model {
  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);
    String selectList = jsonParams.getString("selectList");

    String sql = "SELECT DISTINCT " + selectList //
        + " FROM m_productprice pp" //
        + "   INNER JOIN m_pricelist_version plv" //
        + "     ON pp.m_pricelist_version_id = plv.m_pricelist_version_id" //
        + " WHERE plv.m_pricelist_id IN (:priceListIds)" //
        + "   AND plv.isactive = 'Y'" //
        + "   AND plv.validfrom <= :terminalDate";

    if (lastUpdated != null) {
      sql += " AND pp.updated > :lastUpdated";
    } else {
      sql += " AND pp.isactive = 'Y'";
    }
    if (lastId != null) {
      sql += " AND pp.m_productprice_id > :lastId";
    }
    sql += " ORDER BY pp.m_productprice_id" //
        + " LIMIT :limit";

    final Date terminalDate = new Date();

    // Resolve price list IDs: prefer explicit list from frontend, fallback to terminal's PL
    final List<String> priceListIds = resolvePriceListIds(jsonParams);

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("priceListIds", priceListIds)
        .setParameter("terminalDate", terminalDate)
        .setParameter("limit", limit);
    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }
    return query;
  }

  @Override
  public String getName() {
    return "ProductPrice";
  }

  /**
   * Resolves the list of price list IDs to query.
   * If the frontend sends a "priceListIds" parameter (comma-separated string),
   * those IDs are used so the POS can support multiple price lists.
   * Otherwise falls back to the terminal's single configured price list.
   */
  private List<String> resolvePriceListIds(JSONObject jsonParams) throws JSONException {
    // Try explicit priceListIds from frontend (comma-separated)
    String priceListIdsParam = jsonParams.optString("priceListIds", null);
    if (priceListIdsParam != null && !priceListIdsParam.isEmpty()) {
      List<String> ids = new ArrayList<>();
      for (String id : priceListIdsParam.split(",")) {
        String trimmed = id.trim();
        if (!trimmed.isEmpty()) {
          ids.add(trimmed);
        }
      }
      if (!ids.isEmpty()) {
        return ids;
      }
    }

    // Fallback: terminal's single price list
    final String posId = getTerminalId(jsonParams);
    final PriceList terminalPriceList = POSUtils.getPriceListByTerminalId(posId);
    return terminalPriceList != null
        ? Collections.singletonList(terminalPriceList.getId())
        : Collections.emptyList();
  }

  private static String getTerminalId(final JSONObject jsonsent) {
    String terminalId = null;
    try {
      terminalId = jsonsent.getString("pos");
    } catch (JSONException e) {
      log.error("Error while getting pos " + e.getMessage(), e);
    }
    return terminalId;
  }
}
