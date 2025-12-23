package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.posterminal.POSUtils;

public class OCDiscount extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String client = jsonParams.getString("client");
    String organization = jsonParams.getString("organization");
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String sql = "SELECT " + selectList + " " //
        + " FROM M_Offer e" //
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

  @Override
  public String getName() {
    return "Discount";
  }

}
