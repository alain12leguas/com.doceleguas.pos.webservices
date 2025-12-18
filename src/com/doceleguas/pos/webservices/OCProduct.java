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

    String sql = "SELECT product0_.em_obpgc_printcard            AS col_6_0_,"
        + "       product0_.em_obgcne_expirationdays             AS col_9_0_,"
        + "       product0_.em_gcnv_giftcardtype                 AS col_10_0_,"
        + "       product0_.em_gcnv_amount                       AS col_11_0_,"
        + "       product0_.em_obretur_notusereturnreason        AS col_12_0_,"
        + "       product0_.em_obrdm_delivery_mode               AS col_14_0_,"
        + "       product0_.em_obrdm_delivery_mode_lyw           AS col_15_0_,"
        + "       product0_.em_obrdm_isdeliveryservice           AS col_16_0_,"
        + "       product0_.m_product_id                         AS col_19_0_,"
        + "       product0_.ad_org_id                            AS col_20_0_,"
        + "       product0_.value                                AS col_21_0_,"
        + "       product0_.NAME                                 AS col_22_0_,"
        + "       product0_.m_product_category_id                AS col_23_0_,"
        + "       product0_.em_obpos_scale                       AS col_24_0_,"
        + "       product0_.c_uom_id                             AS col_25_0_,"
        + "       uom13_.uomsymbol                               AS col_26_0_,"
        + "       product0_.description                          AS col_29_0_,"
        + "       product0_.em_obpos_groupedproduct              AS col_30_0_,"
        + "       product0_.isstocked                            AS col_31_0_,"
        + "       product0_.em_obpos_showstock                   AS col_32_0_,"
        + "       product0_.isgeneric                            AS col_33_0_,"
        + "       product0_.m_product_status_id                  AS col_34_0_,"
        + "       product0_.generic_product_id                   AS col_35_0_,"
        + "       product0_.characteristic_desc                  AS col_36_0_,"
        + "       product0_.characteristic_id_desc               AS col_38_0_,"
        + "       product0_.em_obpos_show_ch_desc                AS col_39_0_,"
        + "       product0_.producttype                          AS col_40_0_,"
        + "       product0_.prod_cat_selection                   AS col_41_0_,"
        + "       product0_.product_selection                    AS col_42_0_,"
        + "       product0_.product_inc_char_sel                 AS col_43_0_,"
        + "       product0_.product_exc_char_sel                 AS col_44_0_,"
        + "       product0_.print_description                    AS col_45_0_,"
        + "       product0_.em_obpos_allowanonymoussale          AS col_46_0_,"
        + "       product0_.returnable                           AS col_47_0_,"
        + "       product0_.overdue_return_days                  AS col_48_0_,"
        + "       product0_.ispricerulebased                     AS col_49_0_,"
        + "       product0_.em_obpos_proposal_type               AS col_50_0_,"
        + "       product0_.em_obpos_ismultiselectable           AS col_51_0_,"
        + "       product0_.islinkedtoproduct                    AS col_52_0_,"
        + "       product0_.ismodifytax                          AS col_53_0_,"
        + "       product0_.allow_deferred_sell                  AS col_54_0_,"
        + "       product0_.deferred_sell_max_days               AS col_55_0_,"
        + "       product0_.quantity_rule                        AS col_56_0_,"
        + "       product0_.em_obpos_printservices               AS col_57_0_,"
        + "       product0_.weight                               AS col_58_0_,"
        + "       product0_.em_obpos_maxpriceassocprod           AS col_60_0_,"
        + "       product0_.em_obpos_minpriceassocprod           AS col_61_0_,"
        + "       productcat15_.em_obpos_mandatoryissuance       AS col_62_0_,"
        + "       adimage1_.binarydata                           AS col_63_0_,"
        + "       adimage1_.ad_image_id                          AS col_64_0_,"
        + "       adimage1_.mimetype                             AS col_65_0_,"
        + "       obretcopro3_.bestseller                        AS col_66_0_,"
        + "       obretcopro3_.m_product_status_id               AS col_67_0_,"
        + "       pricingpro4_.pricelist                         AS col_68_0_,"
        + "       pricingpro4_.pricestd                          AS col_69_0_,"
        + "       pricingpro4_.pricelimit                        AS col_70_0_,"
        + "       pricingpro4_.cost                              AS col_71_0_,"
        + "       pricingpro4_.algorithm                         AS col_72_0_,"
        + "       product0_.em_obpos_editable_price              AS col_73_0_"
        + " FROM  m_product product0_" //
        + "       LEFT OUTER JOIN ad_image adimage1_" //
        + "                    ON product0_.ad_image_id = adimage1_.ad_image_id" //
        + "       LEFT OUTER JOIN m_attributeset attributes2_" //
        + "                    ON product0_.m_attributeset_id =" //
        + "                       attributes2_.m_attributeset_id" //
        + "       LEFT OUTER JOIN obretco_prol_product obretcopro3_" //
        + "                    ON product0_.m_product_id = obretcopro3_.m_product_id" //
        + "       LEFT OUTER JOIN m_productprice pricingpro4_" //
        + "                    ON product0_.m_product_id = pricingpro4_.m_product_id" //
        + "       CROSS JOIN m_product product_co5_" //
        + "       CROSS JOIN c_uom uom13_" //
        + "       CROSS JOIN m_product_category productcat15_" //
        + " WHERE  product0_.m_product_id = product_co5_.m_product_id" //
        + "       AND product0_.c_uom_id = uom13_.c_uom_id" //
        + "       AND product0_.m_product_category_id = productcat15_.m_product_category_id" //
        + "       AND 1 = 1" //
        + "       AND pricingpro4_.m_pricelist_version_id = :priceLisVersionId" //
        + "       AND product0_.isactive = 'Y'" //
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
        + "       AND 1 = 1 " //
        + " ORDER  BY product0_.m_product_id ";

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
