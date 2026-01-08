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
    Long offset = jsonParams.optLong("offset", 0);
    //@formatter:off
    String sql = "SELECT " + selectList + ", " //
        + "e.isactive as \"isActive\", " //
        + filterBpCategory() + ","
        + filterBusinessPartner() + ","
        + filterProductCategory() + ","
        + filterProducts()
        + " FROM M_Offer e"
        + " WHERE e.AD_Client_ID=:clientId" //
        + "  AND (e.Pricelist_Selection='Y'" //
        + "       AND NOT (EXISTS" //
        + "                  (SELECT 1" //
        + "                   FROM M_Offer_PriceList pricingadj4_" //
        + "                   WHERE pricingadj4_.IsActive='Y'" //
        + "                     AND pricingadj4_.M_Offer_ID=e.M_Offer_ID" //
        + "                     AND pricingadj4_.M_PriceList_ID=:priceListId))" //
        + "       OR e.Pricelist_Selection='N'" //
        + "       AND (EXISTS" //
        + "              (SELECT 1" //
        + "               FROM M_Offer_PriceList pricingadj5_" //
        + "               WHERE pricingadj5_.IsActive='Y'" //
        + "                 AND pricingadj5_.M_Offer_ID=e.M_Offer_ID" //
        + "                 AND pricingadj5_.M_PriceList_ID=:priceListId)))" //
        + "  AND (e.AD_Org_ID IN :orgs)" //
        + "  AND (e.Org_Selection='Y'" //
        + "       AND NOT (EXISTS" //
        + "                  (SELECT 1" //
        + "                   FROM M_Offer_Organization pricingadj6_" //
        + "                   WHERE pricingadj6_.Isactive='Y'" //
        + "                     AND pricingadj6_.M_Offer_ID=e.M_Offer_ID" //
        + "                     AND pricingadj6_.AD_Org_ID=:orgId))" //
        + "       OR e.Org_Selection='N'" //
        + "       AND (EXISTS" //
        + "              (SELECT 1" //
        + "               FROM M_Offer_Organization pricingadj7_" //
        + "               WHERE pricingadj7_.Isactive='Y'" //
        + "                 AND pricingadj7_.M_Offer_ID=e.M_Offer_ID" //
        + "                 AND pricingadj7_.AD_Org_ID=:orgId)))" //
        + "  AND (e.EM_OBDISC_C_Currency_ID IS NULL" //
        + "       OR e.EM_OBDISC_C_Currency_ID=:currencyId)";//
    //@formatter:on
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += "  AND e.IsActive='Y'";
    }
    sql += " LIMIT :limit ";
    if (offset != 0) {
      sql += " OFFSET :offset";
    }
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
    if (offset != 0) {
      query.setParameter("offset", offset);
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
