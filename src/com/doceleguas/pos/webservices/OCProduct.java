package com.doceleguas.pos.webservices;

import java.util.Date;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.service.OBDal;
import org.openbravo.mobile.core.utils.OBMOBCUtils;
import org.openbravo.retail.posterminal.POSUtils;

public class OCProduct extends Model {
  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings("deprecation")
  @Override
  public JSONArray exec(JSONObject jsonParams) throws JSONException {

    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId");
    String selectList = jsonParams.getString("selectList");
    String sql = "SELECT " + selectList + " " //
        + " FROM  m_product e" //
        + "       LEFT OUTER JOIN ad_image adimage1_" //
        + "                    ON e.ad_image_id = adimage1_.ad_image_id" //
        + "       LEFT OUTER JOIN m_attributeset attributes2_" //
        + "                    ON e.m_attributeset_id =" //
        + "                       attributes2_.m_attributeset_id" //
        + "       LEFT OUTER JOIN obretco_prol_product obretcopro3_" //
        + "                    ON e.m_product_id = obretcopro3_.m_product_id" //
        + "       LEFT OUTER JOIN m_productprice pricingpro4_" //
        + "                    ON e.m_product_id = pricingpro4_.m_product_id" //
        + "       CROSS JOIN m_product product_co5_" //
        + "       CROSS JOIN c_uom uom13_" //
        + "       CROSS JOIN m_product_category productcat15_" //
        + " WHERE  e.m_product_id = product_co5_.m_product_id" //
        + "       AND e.c_uom_id = uom13_.c_uom_id" //
        + "       AND e.m_product_category_id = productcat15_.m_product_category_id" //
        + "       AND 1 = 1" //
        + "       AND pricingpro4_.m_pricelist_version_id = :priceLisVersionId" //
        + "       AND e.isactive = 'Y'" //
        + "       AND ( EXISTS (SELECT 1" //
        + "                     FROM   m_product product16_" //
        + "                            LEFT OUTER JOIN obretco_prol_product obretcopro17_" //
        + "                                         ON product16_.m_product_id =" //
        + "                                            obretcopro17_.m_product_id" //
        + "                            CROSS JOIN m_productprice pricingpro18_" //
        + "                     WHERE  product16_.m_product_id =" //
        + "                                pricingpro18_.m_product_id" //
        + "                            AND pricingpro18_.m_pricelist_version_id = :priceLisVersionId" //
        + "                            AND obretcopro17_.obretco_productlist_id = :productListId) )" //
        + "       AND 1 = 1 "; //
    if (lastId != null) {
      sql += " AND e.m_product_id > :lastId";
    }
    sql += " ORDER  BY e.m_product_id ";

    String organization = jsonParams.getString("organization");
    final String posId = getTerminalId(jsonParams);
    final String productListId = POSUtils.getProductListId(posId, jsonParams);
    final String priceListVersionId = POSUtils.getPriceListVersionByOrgId(organization, new Date())
        .getId();
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("priceLisVersionId", priceListVersionId)
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
    return "Product";
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

  private static Date getTerminalDate(JSONObject jsonsent) throws JSONException {
    return OBMOBCUtils.calculateServerDate(
        jsonsent.getJSONObject("parameters").getString("terminalTime"),
        jsonsent.getJSONObject("parameters").getJSONObject("terminalTimeOffset").getLong("value"));
  }

  private Long getLastUpdated(final JSONObject jsonsent) throws JSONException {
    return jsonsent.has("lastUpdated") && !jsonsent.get("lastUpdated").equals("undefined")
        && !jsonsent.get("lastUpdated").equals("null") ? jsonsent.getLong("lastUpdated") : null;
  }

}
