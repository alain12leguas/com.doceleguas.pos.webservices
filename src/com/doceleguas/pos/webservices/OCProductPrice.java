package com.doceleguas.pos.webservices;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.config.OBRETCOProductList;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.POSUtils;

/**
 * Backend model for ProductPrice masterdata.
 *
 * Serves product prices for ALL price lists configured on the terminal, so the POS can look up
 * prices when a Business Partner has a different price list than the terminal's default.
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
    OBPOSApplications posterminal = POSUtils.getTerminalById(jsonParams.getString("pos"));
    OBRETCOProductList productList = POSUtils
        .getProductListByPosterminalId(jsonParams.getString("pos"));

    PriceList pricelist = POSUtils.getPriceListByTerminal(posterminal.getSearchKey());
    String sql = "SELECT DISTINCT " + selectList //
        + "FROM OBRETCO_Prol_Product pli " //
        + "INNER JOIN M_ProductPrice ppp ON pli.M_Product_ID = ppp.M_Product_ID " //
        + "LEFT JOIN M_PriceList_Version plv ON ppp.M_PriceList_Version_ID = plv.M_PriceList_Version_ID " //
        + "WHERE pli.OBRETCO_Productlist_ID = :productList " //
        + "AND plv.IsActive = 'Y' " //
        + "AND EXISTS ( " //
        + "  SELECT 1 " //
        + "  FROM M_PriceList_Version plv_sub " //
        + "  INNER JOIN C_BPartner bp ON plv_sub.M_PriceList_ID = bp.M_PriceList_ID " //
        + "  WHERE plv_sub.IsActive = 'Y' " //
        + "  AND bp.IsCustomer = 'Y' " //
        + "  AND plv_sub.M_PriceList_ID <> :priceList " //
        + "  AND plv_sub.M_PriceList_Version_ID = ppp.M_PriceList_Version_ID " //
        + "  AND plv_sub.ValidFrom = ( " //
        + "    SELECT MAX(plv2.ValidFrom) " //
        + "    FROM M_PriceList_Version plv2 " //
        + "    WHERE plv_sub.M_PriceList_ID = plv2.M_PriceList_ID " //
        + "    AND plv2.ValidFrom <= :terminalDate " //
        + "  ) " //
        + ") " //
        + " AND ( pli.ad_client_id IN :clients ) "//
        + " AND ( pli.ad_org_id IN :orgs ) ";//

    if (lastUpdated != null) {
      sql += " AND ppp.updated > :lastUpdated";
    } else {
      sql += " AND ppp.isactive = 'Y'";
    }
    if (lastId != null) {
      sql += " AND ppp.m_productprice_id > :lastId";
    }
    sql += " ORDER BY ppp.m_productprice_id" //
        + " LIMIT :limit";

    final Date terminalDate = new Date();

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(jsonParams.getString("organization")))
        .setParameter("productList", productList.getId())
        .setParameter("priceList", pricelist.getId())
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

}
