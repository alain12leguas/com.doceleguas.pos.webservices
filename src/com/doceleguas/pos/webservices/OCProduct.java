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
    String sql = "SELECT DISTINCT " + selectList + ", " //
        + "e.isactive as \"isActive\" " //
        + " FROM  m_product e" //
        + "       LEFT OUTER JOIN ad_image adimage1_" //
        + "                    ON e.ad_image_id = adimage1_.ad_image_id" //
        + "       LEFT OUTER JOIN m_attributeset attributes2_" //
        + "                    ON e.m_attributeset_id =" //
        + "                       attributes2_.m_attributeset_id" //
        + "       iNNER JOIN obretco_prol_product obretcopro_" //
        + "                    ON e.m_product_id = obretcopro_.m_product_id" //
        + "       INNER JOIN m_productprice m_productprice_" //
        + "                    ON e.m_product_id = m_productprice_.m_product_id" //
        + "       INNER JOIN c_uom c_uom_ on e.c_uom_id = c_uom_.c_uom_id" //
        + "       INNER JOIN m_product_category m_product_category_ on e.m_product_category_id=m_product_category_.m_product_category_id" //
        + " WHERE e.c_uom_id = c_uom_.c_uom_id" //
        + "       AND e.m_product_category_id = m_product_category_.m_product_category_id" //
        + "       AND m_productprice_.m_pricelist_version_id = :priceLisVersionId" //
        + "       AND obretcopro_.obretco_productlist_id = :productListId";
    if (lastUpdated != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += "  AND e.IsActive='Y'";
    }
    if (lastId != null) {
      sql += " AND e.m_product_id > :lastId";
    }
    sql += " ORDER  BY e.m_product_id " //
        + " LIMIT :limit";
    //final Date terminalDate = getTerminalDate(jsonParams);
    final Date terminalDate = new Date();
    String organization = jsonParams.getString("organization");
    final String posId = getTerminalId(jsonParams);
    final String productListId = POSUtils.getProductListByPosterminalId(posId).getId();
    final String priceListVersionId = POSUtils.getPriceListVersionByOrgId(organization, terminalDate)
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

}
