package com.doceleguas.pos.webservices;

import java.util.Map;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.posterminal.POSUtils;

/* TODO: Check  that exists this indexes:
 * m_offer_pricelist: (m_offer_id, m_pricelist_id, isactive)
 * m_offer_organization: (m_offer_id, ad_org_id, isactive)
 * m_offer: (ad_client_id, isactive, em_obdisc_c_currency_id)
 * */
public class OCDiscount extends Model {
  final String FILTER_BPCATEGORY_ALIAS = "discountFilterBPCategory";
  final String FILTER_BPARTNER_ALIAS = "discountFilterBPartner";
  final String FILTER_PRODUCTCATEGORY_ALIAS = "discountFilterProdCategory";
  final String FILTER_PRODUCT_ALIAS = "discountFilterProducts";

  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String client = jsonParams.getString("client");
    String organization = jsonParams.getString("organization");
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);
    //@formatter:off    
    String sql = "SELECT " + selectList + ", " //
            + "e.isactive as \"isActive\", " //
            + filterBpCategory() + ","
            + filterBusinessPartner() + ","
            + filterProductCategory() + ","
            + filterProducts()
            + " FROM M_Offer e" //
            + " LEFT JOIN LATERAL (" 
			+ "   SELECT json_agg(json_build_object('id', m_obg.m_offer_bp_group_id, 'c_bp_group_id', cbg.c_bp_group_id, '_identifier', e.name || ' - ' || cbg.name)) AS js"
			+ "   FROM m_offer_bp_group m_obg "
			+ "   INNER JOIN c_bp_group cbg ON cbg.c_bp_group_id = m_obg.c_bp_group_id"
			+ "   WHERE m_obg.m_offer_id = e.m_offer_id AND m_obg.isactive = 'Y' "
			+ " ) bp_cat ON TRUE "
			+ " LEFT JOIN LATERAL ("
			+ "   SELECT json_agg(json_build_object('id', mobp.m_offer_bpartner_id, 'c_bpartner_id', cbp.c_bpartner_id, '_identifier', e.name || ' - ' || cbp.name)) AS js"
			+ "   FROM m_offer_bpartner mobp"
			+ "   INNER JOIN c_bpartner cbp ON cbp.c_bpartner_id = mobp.c_bpartner_id"
			+ "   WHERE mobp.m_offer_id = e.m_offer_id AND mobp.isactive = 'Y'"
			+ " ) bp_part ON TRUE "			
			+ " LEFT JOIN LATERAL ("
			+ "   SELECT json_agg(json_build_object('id', mopc.m_offer_prod_cat_id, 'm_product_category_id', mpc.m_product_category_id, '_identifier', e.name || ' - ' || mpc.name)) AS js"
			+ "   FROM m_offer_prod_cat mopc "
			+ "   INNER JOIN m_product_category mpc ON mpc.m_product_category_id = mopc.m_product_category_id"
			+ "   WHERE mopc.m_offer_id = e.m_offer_id AND mopc.isactive = 'Y'"
			+ " ) prod_cat ON TRUE"			
			+ " LEFT JOIN LATERAL ("
			+ "   SELECT json_agg(json_build_object('id', mop.m_offer_product_id, 'm_product_id', mp.m_product_id, 'm_product_value', mp.value, 'm_offer_disc_qty', mop.em_obdisc_qty, '_identifier', e.name || ' - ' || mp.name)) AS js"
			+ "   FROM m_offer_product mop"
			+ "   INNER JOIN m_product mp ON mp.m_product_id = mop.m_product_id"
			+ "   WHERE mop.m_offer_id = e.m_offer_id AND mop.isactive = 'Y'"
			+ " ) prod ON TRUE"			
			+ " WHERE e.ad_client_id = :clientId"
			+ "  AND e.isactive = 'Y'"
			+ "  AND (e.em_obdisc_c_currency_id IS NULL OR e.em_obdisc_c_currency_id = :currencyId)"
			+ "  AND e.ad_org_id IN :orgs"
			+ " AND ("
			+ "      (e.pricelist_selection = 'Y' AND NOT EXISTS (SELECT 1 FROM m_offer_pricelist p WHERE p.m_offer_id = e.m_offer_id AND p.m_pricelist_id = :priceListId AND p.isactive = 'Y'))"
			+ "      OR" 
			+ "      (e.pricelist_selection = 'N' AND EXISTS (SELECT 1 FROM m_offer_pricelist p WHERE p.m_offer_id = e.m_offer_id AND p.m_pricelist_id = :priceListId AND p.isactive = 'Y'))"
			+ "  )"
			+ "  AND ("
			+ "     (e.org_selection = 'Y' AND NOT EXISTS (SELECT 1 FROM m_offer_organization o WHERE o.m_offer_id = e.m_offer_id AND o.ad_org_id = :orgId AND o.isactive = 'Y'))"
			+ "      OR "
			+ "      (e.org_selection = 'N' AND EXISTS (SELECT 1 FROM m_offer_organization o WHERE o.m_offer_id = e.m_offer_id AND o.ad_org_id = :orgId AND o.isactive = 'Y'))"
			+ "  )";
    if (lastUpdated != null) {
        sql += " AND e.updated > :lastUpdated";
      } else {
        sql += "  AND e.IsActive='Y'";
      }
      if (lastId != null) {
        sql += " AND e.m_offer_id > :lastId";
      }
      sql += " ORDER  BY e.m_offer_id " //
          + " LIMIT :limit";
    PriceList priceList = POSUtils.getPriceListByOrgId(organization);
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("clientId", client)
        .setParameter("orgId", organization)
        .setParameter("limit", limit)
        .setParameter("priceListId", priceList.getId())
        .setParameter("currencyId", priceList.getCurrency().getId())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(organization));
    if (lastId != null) {
        query.setParameter("lastId", lastId);
      }
    return query;
  }

  private String filterProducts() {
    //@formatter:off
    return " (SELECT CAST(json_agg(" //
        + "     json_build_object(" //
        + "         'id', m_offer_product_.m_offer_product_id,"//
        + "         'm_product_id', m_product_.m_product_id," //
        + "         'm_product_value', m_product_.value," //
        + "         'm_offer_disc_qty', m_offer_product_.em_obdisc_qty," //
        + "         'm_offer_id', e.m_offer_id,"//
        + "         '_identifier', e.name || ' - ' || m_product_.name)) FILTER (WHERE m_offer_product_.isactive='Y') AS TEXT)"
        + "     FROM M_Offer_Product m_offer_product_ " //
        + "       INNER JOIN m_product m_product_ ON m_product_.m_product_id=m_offer_product_.m_product_id"
        + "     WHERE m_offer_product_.m_offer_id=e.m_offer_id) AS \"" + FILTER_PRODUCT_ALIAS + "\"";
    //@formatter:on
  }

  private String filterProductCategory() {
  //@formatter:off
   return " (SELECT CAST(json_agg("
       + "    json_build_object(" //
       + "         'id', m_offer_prod_cat_.m_offer_prod_cat_id,"//
       + "         'm_product_category_id', m_product_category_.m_product_category_id," //
       + "         'm_offer_id',  e.m_offer_id,"//
       + "         '_identifier', e.name || ' - ' || m_product_category_.name)) FILTER (WHERE m_offer_prod_cat_.isactive='Y') AS TEXT)" //
       + "     FROM M_Offer_Prod_Cat m_offer_prod_cat_ "
       + "       INNER JOIN M_Product_Category m_product_category_ ON m_product_category_.m_product_category_id=m_offer_prod_cat_.m_product_category_id"
       + "     WHERE m_offer_prod_cat_.m_offer_id=e.m_offer_id) AS \"" + FILTER_PRODUCTCATEGORY_ALIAS + "\"";
       
  //@formatter:on

  }

  private String filterBpCategory() {
  //@formatter:off
    return " (SELECT CAST(JSON_AGG("
        + "      json_build_object("
        + "             'id', m_offer_bp_group_.m_offer_bp_group_id,"
        + "             'c_bp_group_id', c_bp_group_.c_bp_group_id,"
        + "             'm_offer_id',  e.m_offer_id,"
        + "             '_identifier', e.name || ' - ' || c_bp_group_.name)) FILTER (WHERE m_offer_bp_group_.isactive='Y') AS TEXT)"
        + "     FROM M_Offer_BP_Group m_offer_bp_group_ "
        + "      INNER JOIN C_BP_Group c_bp_group_ ON c_bp_group_.c_bp_group_id=m_offer_bp_group_.c_bp_group_id"
        + "     WHERE m_offer_bp_group_.m_offer_id=e.m_offer_id) AS \"" + FILTER_BPCATEGORY_ALIAS + "\"";
      //@formatter:on
  }

  private String filterBusinessPartner() {
    //@formatter:off
    return " (SELECT CAST(json_agg(" //
        + "    json_build_object(" //
        + "          'id', m_offer_bpartner_.m_offer_bpartner_id,"//
        + "          'c_bpartner_id', c_bpartner_.c_bpartner_id," //
        + "          'm_offer_id',  e.m_offer_id,"//
        + "          '_identifier', e.name || ' - ' || c_bpartner_.name)) FILTER (WHERE m_offer_bpartner_.isactive='Y') AS TEXT)"
        + "   FROM M_Offer_BPArtner m_offer_bpartner_ "
        + "    INNER JOIN c_bpartner c_bpartner_ ON c_bpartner_.c_bpartner_id=m_offer_bpartner_.c_bpartner_id"
        + "   WHERE m_offer_bpartner_.m_offer_id=e.m_offer_id) AS \"" + FILTER_BPARTNER_ALIAS + "\"";
    //@formatter:on
  }

  @Override
  public JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException {
    JSONObject recordJson = new JSONObject(rowMap);
    if (rowMap.get(FILTER_BPCATEGORY_ALIAS) != null) {
      recordJson.put(FILTER_BPCATEGORY_ALIAS,
          new JSONArray((String) rowMap.get(FILTER_BPCATEGORY_ALIAS)));
    }
    if (rowMap.get(FILTER_BPARTNER_ALIAS) != null) {
      recordJson.put(FILTER_BPARTNER_ALIAS,
          new JSONArray((String) rowMap.get(FILTER_BPARTNER_ALIAS)));
    }
    if (rowMap.get(FILTER_PRODUCTCATEGORY_ALIAS) != null) {
      recordJson.put(FILTER_PRODUCTCATEGORY_ALIAS,
          new JSONArray((String) rowMap.get(FILTER_PRODUCTCATEGORY_ALIAS)));
    }
    if (rowMap.get(FILTER_PRODUCT_ALIAS) != null) {
      recordJson.put(FILTER_PRODUCT_ALIAS,
          new JSONArray((String) rowMap.get(FILTER_PRODUCT_ALIAS)));
    }
    return recordJson;
  }

  @Override
  public String getName() {
    return "Discount";
  }

}
