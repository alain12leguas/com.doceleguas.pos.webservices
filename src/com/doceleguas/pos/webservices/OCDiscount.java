package com.doceleguas.pos.webservices;

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

  final String FILTER_BPCATEGORY = "  "//
      + " CAST(json_agg(" //
      + "  json_build_object(" //
      + "         'id', m_offer_bp_group_.m_offer_bp_group_id,"//
      + "         'c_bp_group_id', c_bp_group_.c_bp_group_id," //
      + "         'm_offer_id',  e.m_offer_id,"//
      + "         '_identifier', e.name || ' - ' || c_bp_group_.name" //
      + "  )"
      + ") FILTER (WHERE m_offer_bp_group_.m_offer_bp_group_id IS NOT NULL) AS TEXT) AS \"discountFilterBPGroup\"";
  final String JOIN_FILTER_BPCATEGORY = " "//
      + " LEFT JOIN M_Offer_BP_Group m_offer_bp_group_ ON m_offer_bp_group_.m_offer_id=e.m_offer_id " //
      + " LEFT JOIN C_BP_Group c_bp_group_ ON c_bp_group_.c_bp_group_id=m_offer_bp_group_.c_bp_group_id ";

  final String FILTER_BPARTNER = "  "//
      + " CAST(json_agg(" //
      + "  json_build_object(" //
      + "         'id', m_offer_bpartner_.m_offer_bpartner_id,"//
      + "         'c_bpartner_id', c_bpartner_.c_bpartner_id," //
      + "         'm_offer_id',  e.m_offer_id,"//
      + "         '_identifier', e.name || ' - ' || c_bpartner_.name" //
      + "  )"
      + ") FILTER (WHERE m_offer_bpartner_.m_offer_bpartner_id IS NOT NULL) AS TEXT) AS \"discountFilterBPartner\"";
  final String JOIN_FILTER_BPARTNER = " "//
      + " LEFT JOIN M_Offer_BPArtner m_offer_bpartner_ ON m_offer_bpartner_.m_offer_id=e.m_offer_id " //
      + " LEFT JOIN c_bpartner c_bpartner_ ON c_bpartner_.c_bpartner_id=m_offer_bpartner_.c_bpartner_id ";

  final String FILTER_PRODUCTCATEGORY = "  "//
      + " CAST(json_agg(" //
      + "  json_build_object(" //
      + "         'id', m_offer_prod_cat_.m_offer_prod_cat_id,"//
      + "         'm_product_category_id', m_product_category_.m_product_category_id," //
      + "         'm_offer_id',  e.m_offer_id,"//
      + "         '_identifier', e.name || ' - ' || m_product_category_.name" //
      + "  )"
      + ") FILTER (WHERE m_offer_prod_cat_.m_offer_prod_cat_id IS NOT NULL) AS TEXT) AS \"discountFilterProdCategory\"";
  final String JOIN_FILTER_PRODUCTCATEGORY = " "//
      + " LEFT JOIN M_Offer_Prod_Cat m_offer_prod_cat_ ON m_offer_prod_cat_.m_offer_id=e.m_offer_id " //
      + " LEFT JOIN M_Product_Category m_product_category_ ON m_product_category_.m_product_category_id=m_offer_prod_cat_.m_product_category_id ";

  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String client = jsonParams.getString("client");
    String organization = jsonParams.getString("organization");
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String sql = "SELECT " + selectList + ", " //
        + "${FILTER_BPCATEGORY}," //
        + "${FILTER_BPARTNER}," //
        + "${FILTER_PRODUCTCATEGORY}" //
        + " FROM M_Offer e" //
        + " ${JOIN_FILTER_BPCATEGORY} "//
        + " ${JOIN_FILTER_BPARTNER} "//
        + " ${JOIN_FILTER_PRODUCTCATEGORY} "//
        // + " LEFT JOIN m_product m_product_ ON m_product_.m_product_id="
        + " WHERE e.AD_Client_ID=:clientId" //
        + "  AND e.IsActive='Y'" //
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
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    }
    sql += " GROUP BY e.m_offer_id "; //
    sql += " LIMIT :limit ";
    if (offset != 0) {
      sql += " OFFSET :offset";
    }
    sql = sql.replace("${FILTER_BPCATEGORY}", FILTER_BPCATEGORY)
        .replace("${JOIN_FILTER_BPCATEGORY}", JOIN_FILTER_BPCATEGORY)
        .replace("${FILTER_BPARTNER}", FILTER_BPARTNER)
        .replace("${JOIN_FILTER_BPARTNER}", JOIN_FILTER_BPARTNER)
        .replace("${FILTER_PRODUCTCATEGORY}", FILTER_PRODUCTCATEGORY)
        .replace("${JOIN_FILTER_PRODUCTCATEGORY}", JOIN_FILTER_PRODUCTCATEGORY);
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

  @Override
  public void transformResult(JSONArray data) throws JSONException {

    for (int i = 0; i < data.length(); i++) {
      JSONObject record = data.getJSONObject(i);
      if (record.optString("discountFilterBPGroup", null) != null) {
        record.put("discountFilterBPGroup",
            new JSONArray(record.getString("discountFilterBPGroup")));
      }
      if (record.optString("discountFilterBPartner", null) != null) {
        record.put("discountFilterBPartner",
            new JSONArray(record.getString("discountFilterBPartner")));
      }
      if (record.optString("discountFilterProdCategory", null) != null) {
        record.put("discountFilterProdCategory",
            new JSONArray(record.getString("discountFilterProdCategory")));
      }
    }
  }

  @Override
  public String getName() {
    return "Discount";
  }

}
