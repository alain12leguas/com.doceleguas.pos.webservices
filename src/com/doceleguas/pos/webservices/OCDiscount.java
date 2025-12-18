package com.doceleguas.pos.webservices;

import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.pricing.pricelist.PriceList;
import org.openbravo.retail.posterminal.POSUtils;

public class OCDiscount extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public JSONArray exec(JSONObject jsonParams) throws JSONException {
    // OBContext.getOBContext()
    // .getOrganizationStructureProvider()
    // .getNaturalTree(jsonsent.getString("organization"));
    // String clientList = StringUtils.join(OBContext.getOBContext().getReadableClients(), ",");
    String client = jsonParams.getString("client");
    String organization = jsonParams.getString("organization");
    // String organizationList = StringUtils
    // .join(new OrganizationStructureProvider().getNaturalTree(organization), ",");
    String sql = "SELECT " //
        + "       pricingadj0_.M_Offer_ID AS col_1_0_," //
        + "       pricingadj0_.AD_Client_ID AS col_2_0_," //
        + "       pricingadj0_.AD_Org_ID AS col_3_0_," //
        + "       pricingadj0_.Name AS col_4_0_," //
        + "       pricingadj0_.Priority AS col_5_0_," //
        + "       pricingadj0_.Addamt AS col_6_0_," //
        + "       pricingadj0_.Discount AS col_7_0_," //
        + "       pricingadj0_.Fixed AS col_8_0_," //
        + "       pricingadj0_.DateFrom AS col_9_0," //
        + "       pricingadj0_.Name AS col_107_0_" //
        + " FROM M_Offer pricingadj0_" //
        + " WHERE pricingadj0_.AD_Client_ID=:clientId" //
        + "  AND pricingadj0_.IsActive='Y'" //
        + "  AND (pricingadj0_.Pricelist_Selection='Y'" //
        + "       AND NOT (EXISTS" //
        + "                  (SELECT 1" //
        + "                   FROM M_Offer_PriceList pricingadj4_" //
        + "                   WHERE pricingadj4_.IsActive='Y'" //
        + "                     AND pricingadj4_.M_Offer_ID=pricingadj0_.M_Offer_ID" //
        + "                     AND pricingadj4_.M_PriceList_ID=:priceListId))" //
        + "       OR pricingadj0_.Pricelist_Selection='N'" //
        + "       AND (EXISTS" //
        + "              (SELECT 1" //
        + "               FROM M_Offer_PriceList pricingadj5_" //
        + "               WHERE pricingadj5_.IsActive='Y'" //
        + "                 AND pricingadj5_.M_Offer_ID=pricingadj0_.M_Offer_ID" //
        + "                 AND pricingadj5_.M_PriceList_ID=:priceListId)))" //
        + "  AND (pricingadj0_.AD_Org_ID IN :orgs)" //
        + "  AND (pricingadj0_.Org_Selection='Y'" //
        + "       AND NOT (EXISTS" //
        + "                  (SELECT 1" //
        + "                   FROM M_Offer_Organization pricingadj6_" //
        + "                   WHERE pricingadj6_.Isactive='Y'" //
        + "                     AND pricingadj6_.M_Offer_ID=pricingadj0_.M_Offer_ID" //
        + "                     AND pricingadj6_.AD_Org_ID=:orgId))" //
        + "       OR pricingadj0_.Org_Selection='N'" //
        + "       AND (EXISTS" //
        + "              (SELECT 1" //
        + "               FROM M_Offer_Organization pricingadj7_" //
        + "               WHERE pricingadj7_.Isactive='Y'" //
        + "                 AND pricingadj7_.M_Offer_ID=pricingadj0_.M_Offer_ID" //
        + "                 AND pricingadj7_.AD_Org_ID=:orgId)))" //
        + "  AND (pricingadj0_.EM_OBDISC_C_Currency_ID IS NULL" //
        + "       OR pricingadj0_.EM_OBDISC_C_Currency_ID=:currencyId)";
    PriceList priceList = POSUtils.getPriceListByOrgId(organization);
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("clientId", client)
        .setParameter("orgId", organization)
        .setParameter("priceListId", priceList.getId())
        .setParameter("currencyId", priceList.getCurrency().getId())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(organization))
        .scroll(ScrollMode.FORWARD_ONLY);
    ScrollableResults scroll = query.scroll(ScrollMode.FORWARD_ONLY);
    int i = 0;
    JSONArray dataArray = new JSONArray();
    try {
      while (scroll.next()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = (Map<String, Object>) scroll.get()[0];
        JSONObject res = new JSONObject(rowMap);
        dataArray.put(res);
      }
      i++;
      if (i % 100 == 0) {
        OBDal.getInstance().flush();
        OBDal.getInstance().getSession().clear();
      }
    } finally {
      scroll.close();
    }

    return dataArray;
  }

  @Override
  public String getName() {
    return "Discount";
  }

}
