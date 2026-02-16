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
    StringBuilder sql = new StringBuilder();
    
    sql.append("SELECT ");
    sql.append(     selectList);
    sql.append("    ,e.isactive ");
    sql.append("    ,CAST(bp_cat.js AS text) AS \"" + FILTER_BPCATEGORY_ALIAS + "\"");
    sql.append("    ,CAST(bp_part.js AS text) AS \"" + FILTER_BPARTNER_ALIAS +"\"");
    sql.append("    ,CAST(prod_cat.js AS text) AS \"" + FILTER_PRODUCTCATEGORY_ALIAS +"\"");
    sql.append("    ,CAST(prod.js AS text) AS \"" + FILTER_PRODUCT_ALIAS + "\" ");
    sql.append("FROM M_Offer e ");
    sql.append("LEFT JOIN LATERAL ( ");
    sql.append("    SELECT json_agg( ");
    sql.append("        json_build_object( ");
    sql.append("            'id', m_obg.m_offer_bp_group_id, ");
    sql.append("            'c_bp_group_id', cbg.c_bp_group_id, ");
    sql.append("            '_identifier', COALESCE(e.name, '') || ' - ' || COALESCE(cbg.name, '') ");
    sql.append("        ) ");
    sql.append("    ) AS js ");
    sql.append("    FROM m_offer_bp_group m_obg  ");
    sql.append("    INNER JOIN c_bp_group cbg ON cbg.c_bp_group_id = m_obg.c_bp_group_id ");
    sql.append("    WHERE m_obg.m_offer_id = e.m_offer_id AND m_obg.isactive = 'Y' ");
    sql.append(") bp_cat ON TRUE  ");
    sql.append("LEFT JOIN LATERAL ( ");
    sql.append("    SELECT json_agg( ");
    sql.append("        json_build_object( ");
    sql.append("            'id', mobp.m_offer_bpartner_id, ");
    sql.append("            'c_bpartner_id', cbp.c_bpartner_id, ");
    sql.append("            '_identifier', COALESCE(e.name, '') || ' - ' || COALESCE(cbp.name, '') ");
    sql.append("        ) ");
    sql.append("    ) AS js ");
    sql.append("    FROM m_offer_bpartner mobp ");
    sql.append("    INNER JOIN c_bpartner cbp ON cbp.c_bpartner_id = mobp.c_bpartner_id ");
    sql.append("    WHERE mobp.m_offer_id = e.m_offer_id AND mobp.isactive = 'Y' ");
    sql.append(") bp_part ON TRUE  ");
    sql.append("LEFT JOIN LATERAL ( ");
    sql.append("    SELECT json_agg( ");
    sql.append("        json_build_object( ");
    sql.append("            'id', mopc.m_offer_prod_cat_id, ");
    sql.append("            'm_product_category_id', mpc.m_product_category_id, ");
    sql.append("            '_identifier', COALESCE(e.name, '') || ' - ' || COALESCE(mpc.name, '') ");
    sql.append("        ) ");
    sql.append("    ) AS js ");
    sql.append("    FROM m_offer_prod_cat mopc  ");
    sql.append("    INNER JOIN m_product_category mpc ON mpc.m_product_category_id = mopc.m_product_category_id ");
    sql.append("    WHERE mopc.m_offer_id = e.m_offer_id AND mopc.isactive = 'Y' ");
    sql.append(") prod_cat ON TRUE ");
    sql.append("LEFT JOIN LATERAL ( ");
    sql.append("    SELECT json_agg( ");
    sql.append("        json_build_object( ");
    sql.append("            'id', mop.m_offer_product_id, ");
    sql.append("            'm_product_id', mp.m_product_id, ");
    sql.append("            'm_product_value', mp.value, "); 
    sql.append("            'm_offer_disc_qty', mop.em_obdisc_qty, ");
    sql.append("            '_identifier', COALESCE(e.name, '') || ' - ' || COALESCE(mp.name, '') ");
    sql.append("        ) ");
    sql.append("    ) AS js ");
    sql.append("    FROM m_offer_product mop ");
    sql.append("    INNER JOIN m_product mp ON mp.m_product_id = mop.m_product_id ");
    sql.append("    WHERE mop.m_offer_id = e.m_offer_id AND mop.isactive = 'Y' ");
    sql.append(") prod ON TRUE ");
    sql.append("WHERE e.isactive = 'Y'  ");
    sql.append("  AND e.ad_client_id = :clientId ");
    sql.append("  AND (e.em_obdisc_c_currency_id IS NULL OR e.em_obdisc_c_currency_id = :currencyId) ");
    sql.append("  AND e.ad_org_id IN :orgs ");
    sql.append("  AND CASE e.pricelist_selection ");
    sql.append("          WHEN 'Y' THEN NOT EXISTS ( ");
    sql.append("              SELECT 1 FROM m_offer_pricelist p  ");
    sql.append("              WHERE p.m_offer_id = e.m_offer_id  ");
    sql.append("                AND p.m_pricelist_id = :priceListId ");
    sql.append("                AND p.isactive = 'Y' ");
    sql.append("          ) ");
    sql.append("          WHEN 'N' THEN EXISTS ( ");
    sql.append("              SELECT 1 FROM m_offer_pricelist p  ");
    sql.append("              WHERE p.m_offer_id = e.m_offer_id  ");
    sql.append("                AND p.m_pricelist_id = :priceListId ");
    sql.append("                AND p.isactive = 'Y' ");
    sql.append("          ) ");
    sql.append("      END ");
    sql.append("  AND CASE e.org_selection ");
    sql.append("          WHEN 'Y' THEN NOT EXISTS ( ");
    sql.append("              SELECT 1 FROM m_offer_organization o  ");
    sql.append("              WHERE o.m_offer_id = e.m_offer_id  ");
    sql.append("                AND o.ad_org_id = :orgId  ");
    sql.append("                AND o.isactive = 'Y' ");
    sql.append("          ) ");
    sql.append("          WHEN 'N' THEN EXISTS ( ");
    sql.append("              SELECT 1 FROM m_offer_organization o  ");
    sql.append("              WHERE o.m_offer_id = e.m_offer_id  ");
    sql.append("                AND o.ad_org_id = :orgId  ");
    sql.append("                AND o.isactive = 'Y' ");
    sql.append("          ) ");
    sql.append("      END ");

    if (lastUpdated != null) {
        sql.append(" AND e.updated > :lastUpdated ");
      } else {
    	  sql.append("  AND e.IsActive='Y' ");
      }
      if (lastId != null) {
    	  sql.append(" AND e.m_offer_id > :lastId ");
      }
      sql.append(" ORDER  BY e.m_offer_id ");
      sql.append(" LIMIT :limit ");
    
    String finalQuery = sql.toString();      
    PriceList priceList = POSUtils.getPriceListByOrgId(organization);
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(finalQuery);
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
