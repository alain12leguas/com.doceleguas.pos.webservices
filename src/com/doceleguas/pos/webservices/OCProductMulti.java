package com.doceleguas.pos.webservices;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.retail.posterminal.POSUtils;

public class OCProductMulti extends Model {
  private static final Logger log = LogManager.getLogger();

  public static final String QUERY_REGULAR = "regular";
  public static final String QUERY_CROSSSTORE = "crossstore";
  public static final String QUERY_PACKS = "packs";
  public static final String QUERY_GENERIC = "generic";

  private String productListId;
  private String priceListVersionId;
  private String orgId;
  private Date terminalDate;

  @Override
  public List<NativeQuery<?>> createQueries(JSONObject jsonParams) throws JSONException {
    List<NativeQuery<?>> queries = new ArrayList<>();

    String posId = getTerminalId(jsonParams);
    String organization = jsonParams.getString("organization");

    terminalDate = new Date();
    productListId = POSUtils.getProductListByPosterminalId(posId).getId();
    priceListVersionId = POSUtils.getPriceListVersionByOrgId(organization, terminalDate).getId();
    orgId = OBContext.getOBContext().getCurrentOrganization().getId();

    queries.add(createRegularProductsQuery(jsonParams));
    // queries.add(createCrossStoreProductsQuery(jsonParams));
    // queries.add(createPacksQuery(jsonParams));
    // queries.add(createGenericProductsQuery(jsonParams));

    return queries;
  }

  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {
    return createQueries(jsonParams).get(0);
  }

  private NativeQuery<?> createRegularProductsQuery(JSONObject jsonParams) throws JSONException {
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT ").append(selectList).append(", ");
    sql.append("e.isactive as \"isActive\", ");
    sql.append("'");
    sql.append(QUERY_REGULAR);
    sql.append("' as \"_queryType\" ");
    sql.append("FROM m_product e ");
    sql.append("LEFT OUTER JOIN ad_image adimage1_ ON e.ad_image_id = adimage1_.ad_image_id ");
    sql.append(
        "LEFT OUTER JOIN m_attributeset attributes2_ ON e.m_attributeset_id = attributes2_.m_attributeset_id ");
    sql.append(
        "INNER JOIN obretco_prol_product obretcopro_ ON e.m_product_id = obretcopro_.m_product_id ");
    sql.append(
        "INNER JOIN m_productprice m_productprice_ ON e.m_product_id = m_productprice_.m_product_id ");
    sql.append("INNER JOIN c_uom c_uom_ ON e.c_uom_id = c_uom_.c_uom_id ");
    sql.append(
        "INNER JOIN m_product_category m_product_category_ ON e.m_product_category_id = m_product_category_.m_product_category_id ");
    sql.append("WHERE e.c_uom_id = c_uom_.c_uom_id ");
    sql.append("AND e.m_product_category_id = m_product_category_.m_product_category_id ");
    sql.append("AND m_productprice_.m_pricelist_version_id = :priceListVersionId ");
    sql.append("AND obretcopro_.obretco_productlist_id = :productListId ");

    if (lastUpdated != null) {
      sql.append("AND e.updated > :lastUpdated ");
    } else {
      sql.append("AND e.IsActive='Y' ");
    }
    if (lastId != null) {
      sql.append("AND e.m_product_id > :lastId ");
    }
    sql.append("ORDER BY e.m_product_id LIMIT :limit");

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("priceListVersionId", priceListVersionId)
        .setParameter("limit", limit);

    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }
    return query;
  }

  private NativeQuery<?> createCrossStoreProductsQuery(JSONObject jsonParams) throws JSONException {
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);

    String orgIds = jsonParams.optString("orgIds", orgId);
    List<String> orgList = new ArrayList<>();
    orgList.add(orgId);

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT ").append(selectList).append(", ");
    sql.append("e.isactive as \"isActive\", ");
    sql.append("'").append(QUERY_CROSSSTORE).append("' as \"_queryType\" ");
    sql.append("FROM m_product e ");
    sql.append("LEFT OUTER JOIN ad_image adimage1_ ON e.ad_image_id = adimage1_.ad_image_id ");
    sql.append(
        "LEFT OUTER JOIN m_attributeset attributes2_ ON e.m_attributeset_id = attributes2_.m_attributeset_id ");
    sql.append("LEFT JOIN m_attributeuse eau on e.m_attributeset_id = eau.m_attributeset_id ");
    sql.append("WHERE e.isactive = 'Y' ");
    sql.append("AND EXISTS ( ");
    sql.append("  SELECT 1 FROM ad_org o ");
    sql.append("  WHERE o.ad_org_id IN (:orgIds) ");
    sql.append("  AND EXISTS ( ");
    sql.append("    SELECT 1 FROM obretco_prol_product pli ");
    sql.append("    WHERE pli.m_product_id = e.m_product_id ");
    sql.append("    AND pli.obretco_productlist_id = o.obretco_productlist_id ");
    sql.append("  ) ");
    sql.append("  AND EXISTS ( ");
    sql.append("    SELECT 1 FROM m_productprice ppp ");
    sql.append(
        "    JOIN m_pricelist_version plv ON ppp.m_pricelist_version_id = plv.m_pricelist_version_id ");
    sql.append("    WHERE ppp.m_product_id = e.m_product_id ");
    sql.append("    AND plv.m_pricelist_id = o.obretco_pricelist_id ");
    sql.append("    AND plv.validfromdate <= :terminalDate ");
    sql.append("  ) ");
    sql.append(") ");
    sql.append("AND NOT EXISTS ( ");
    sql.append("  SELECT 1 FROM obretco_prol_product pli ");
    sql.append("  WHERE pli.m_product_id = e.m_product_id ");
    sql.append("  AND pli.obretco_productlist_id = :productListId ");
    sql.append(") ");

    if (lastUpdated != null) {
      sql.append("AND e.updated > :lastUpdated ");
    }
    if (lastId != null) {
      sql.append("AND e.m_product_id > :lastId ");
    }
    sql.append("ORDER BY e.m_product_id LIMIT :limit");

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("terminalDate", new java.sql.Timestamp(terminalDate.getTime()))
        .setParameterList("orgIds", orgList)
        .setParameter("limit", limit);

    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }
    return query;
  }

  private NativeQuery<?> createPacksQuery(JSONObject jsonParams) throws JSONException {
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);

    final Calendar now = Calendar.getInstance();

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT ").append(selectList).append(", ");
    sql.append("e.isactive as \"isActive\", ");
    sql.append("'").append(QUERY_PACKS).append("' as \"_queryType\" ");
    sql.append("FROM m_offer e ");
    sql.append("JOIN m_discounttype dt ON e.m_discounttype_id = dt.m_discounttype_id ");
    sql.append("LEFT OUTER JOIN ad_image adimage1_ ON e.ad_image_id = adimage1_.ad_image_id ");
    sql.append("WHERE dt.obposiscategory = true ");
    sql.append("AND dt.isactive = 'Y' ");
    sql.append("AND e.isactive = 'Y' ");
    sql.append("AND e.startingdate <= :startingDate ");
    sql.append("AND (e.endingdate IS NULL OR e.endingdate >= :endingDate) ");

    sql.append("AND ((e.includedproducts = 'N' ");
    sql.append("AND NOT EXISTS ( ");
    sql.append("  SELECT 1 FROM m_discount_product pap ");
    sql.append("  WHERE pap.m_discount_id = e.m_discount_id ");
    sql.append("  AND pap.isactive = 'Y' ");
    sql.append("  AND NOT EXISTS ( ");
    sql.append("    SELECT 1 FROM obretco_prol_product pli ");
    sql.append("    WHERE pli.m_product_id = pap.m_product_id ");
    sql.append("    AND pli.obretco_productlist_id = :productListId ");
    sql.append("    AND pli.isactive = 'Y' ");
    sql.append("  ) ");
    sql.append(")) ");

    sql.append("OR (e.includedproducts = 'Y' ");
    sql.append("AND NOT EXISTS ( ");
    sql.append("  SELECT 1 FROM m_discount_product pap ");
    sql.append("  WHERE pap.m_discount_id = e.m_discount_id ");
    sql.append("  AND pap.isactive = 'Y' ");
    sql.append("  AND EXISTS ( ");
    sql.append("    SELECT 1 FROM obretco_prol_product pli ");
    sql.append("    WHERE pli.m_product_id = pap.m_product_id ");
    sql.append("    AND pli.obretco_productlist_id = :productListId ");
    sql.append("    AND pli.isactive = 'Y' ");
    sql.append("  ) ");
    sql.append("))) ");

    sql.append("AND ((e.includedorganizations = 'N' ");
    sql.append("AND EXISTS ( ");
    sql.append("  SELECT 1 FROM m_discount_organization pao ");
    sql.append("  WHERE pao.m_discount_id = e.m_discount_id ");
    sql.append("  AND pao.ad_org_id = :orgId ");
    sql.append("  AND pao.isactive = 'Y' ");
    sql.append(")) ");
    sql.append("OR (e.includedorganizations = 'Y' ");
    sql.append("AND NOT EXISTS ( ");
    sql.append("  SELECT 1 FROM m_discount_organization pao ");
    sql.append("  WHERE pao.m_discount_id = e.m_discount_id ");
    sql.append("  AND pao.ad_org_id = :orgId ");
    sql.append("  AND pao.isactive = 'Y' ");
    sql.append("))) ");

    if (lastUpdated != null) {
      sql.append("AND e.updated > :lastUpdated ");
    }
    if (lastId != null) {
      sql.append("AND e.m_discount_id > :lastId ");
    }
    sql.append("ORDER BY e.m_discount_id LIMIT :limit");

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("orgId", orgId)
        .setParameter("limit", limit)
        .setParameter("startingDate", now.getTime())
        .setParameter("endingDate", now.getTime());

    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }

    return query;
  }

  private NativeQuery<?> createGenericProductsQuery(JSONObject jsonParams) throws JSONException {
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limitGeneric", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String lastUpdated = jsonParams.optString("lastUpdated", null);

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT DISTINCT ").append(selectList).append(", ");
    sql.append("e.isactive as \"isActive\", ");
    sql.append("'").append(QUERY_GENERIC).append("' as \"_queryType\" ");
    sql.append("FROM m_product e ");
    sql.append("LEFT OUTER JOIN ad_image adimage1_ ON e.ad_image_id = adimage1_.ad_image_id ");
    sql.append(
        "LEFT OUTER JOIN m_attributeset attributes2_ ON e.m_attributeset_id = attributes2_.m_attributeset_id ");
    sql.append(
        "LEFT JOIN m_productprice m_productprice_ ON e.m_product_id = m_productprice_.m_product_id ");
    sql.append(
        "LEFT JOIN obretco_prol_product obretcopro_ ON e.m_product_id = obretcopro_.m_product_id ");
    sql.append("WHERE e.isgeneric = 'Y' ");
    sql.append("AND e.isactive = 'Y' ");
    sql.append("AND m_productprice_.m_pricelist_version_id = :priceListVersionId ");
    sql.append("AND EXISTS ( ");
    sql.append("  SELECT 1 FROM m_product product2 ");
    sql.append("  JOIN obretco_prol_product pli2 ON product2.m_product_id = pli2.m_product_id ");
    sql.append("  JOIN m_productprice ppp2 ON product2.m_product_id = ppp2.m_product_id ");
    sql.append("  WHERE product2.m_product_id = e.generic_product_id ");
    sql.append("  AND ppp2.m_pricelist_version_id = :priceListVersionId ");
    sql.append("  AND pli2.obretco_productlist_id = :productListId ");
    sql.append(") ");

    if (lastUpdated != null) {
      sql.append("AND e.updated > :lastUpdated ");
    }
    if (lastId != null) {
      sql.append("AND e.m_product_id > :lastId ");
    }
    sql.append("ORDER BY e.m_product_id LIMIT :limit");

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql.toString());
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("productListId", productListId)
        .setParameter("priceListVersionId", priceListVersionId)
        .setParameter("limit", limit);

    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }

    return query;
  }

  @Override
  public JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException {
    return new JSONObject(rowMap);
  }

  @Override
  public String getName() {
    return "Product1";
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
}
