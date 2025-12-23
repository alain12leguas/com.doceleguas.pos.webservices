package com.doceleguas.pos.webservices;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.service.OBDal;
import org.openbravo.mobile.core.utils.OBMOBCUtils;
import org.openbravo.retail.posterminal.POSUtils;

public class OCProduct extends Model {
  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);
    String selectList = jsonParams.getString("selectList");
    String sql = "SELECT DISTINCT " + selectList + " " //
        + " FROM  m_product e" //
        + "       LEFT OUTER JOIN ad_image adimage1_" //
        + "                    ON e.ad_image_id = adimage1_.ad_image_id" //
        + "       LEFT OUTER JOIN m_attributeset attributes2_" //
        + "                    ON e.m_attributeset_id =" //
        + "                       attributes2_.m_attributeset_id" //
        + "       LEFT OUTER JOIN obretco_prol_product obretcopro3_" //
        + "                    ON e.m_product_id = obretcopro3_.m_product_id" //
        + "       LEFT OUTER JOIN m_productprice m_productprice_" //
        + "                    ON e.m_product_id = m_productprice_.m_product_id" //
        + "       INNER JOIN c_uom c_uom_ on e.c_uom_id = c_uom_.c_uom_id" //
        + "       INNER JOIN m_product_category m_product_category_ on e.m_product_category_id=m_product_category_.m_product_category_id" //
        + " WHERE e.c_uom_id = c_uom_.c_uom_id" //
        + "       AND e.m_product_category_id = m_product_category_.m_product_category_id" //
        + "       AND 1 = 1" //
        + "       AND m_productprice_.m_pricelist_version_id = :priceLisVersionId" //
        + "       AND e.isactive = 'Y'" //
        + "       AND ( EXISTS (SELECT 1" //
        + "                     FROM   m_product product16_" //
        + "                            LEFT OUTER JOIN obretco_prol_product obretcopro17_" //
        + "                                         ON product16_.m_product_id =" //
        + "                                            obretcopro17_.m_product_id" //
        + "                            INNER JOIN m_productprice pricingpro18_ on product16_.m_product_id=pricingpro18_.m_product_id" //
        + "                     WHERE  product16_.m_product_id =" //
        + "                                pricingpro18_.m_product_id" //
        + "                            AND pricingpro18_.m_pricelist_version_id = :priceLisVersionId" //
        + "                            AND obretcopro17_.obretco_productlist_id = :productListId) )" //
        + "       AND 1 = 1 "; //
    if (lastUpdated != null) {
      sql += " AND e.updated > :lastUpdated";
    }
    if (lastId != null) {
      sql += " AND e.m_product_id > :lastId";
    }
    sql += " ORDER  BY e.m_product_id " //
        + " LIMIT :limit";
    String organization = jsonParams.getString("organization");
    final String posId = getTerminalId(jsonParams);
    final String productListId = POSUtils.getProductListId(posId, jsonParams);
    final String priceListVersionId = POSUtils.getPriceListVersionByOrgId(organization, new Date())
        .getId();
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("priceLisVersionId", priceListVersionId)
        .setParameter("limit", limit);
    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }
    return query;
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
