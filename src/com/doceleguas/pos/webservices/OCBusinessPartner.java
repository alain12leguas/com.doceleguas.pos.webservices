package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

public class OCBusinessPartner extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {
    // OBContext.getOBContext()
    // .getOrganizationStructureProvider()
    // .getNaturalTree(jsonsent.getString("organization"));
    // String clientList = StringUtils.join(OBContext.getOBContext().getReadableClients(), ",");
    // String organization = jsonParams.getString("organization");
    // String organizationList = StringUtils
    // .join(new OrganizationStructureProvider().getNaturalTree(organization), ",");
    String lastUpdated = jsonParams.optString("lastUpdated", null);
    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    String lastId = jsonParams.optString("lastId", null);
    String sql = "SELECT " + selectList + ", " //
        + locations() + " "//
        + " FROM  c_bpartner e"//
        + "       INNER JOIN m_pricelist pricingpri1_" //
        + "               ON e.m_pricelist_id = pricingpri1_.m_pricelist_id"//
        + "       LEFT OUTER JOIN ad_language adlanguage2_"//
        + "                    ON e.ad_language = adlanguage2_.ad_language"//
        + "       LEFT OUTER JOIN c_greeting greeting3_"//
        + "                    ON e.c_greeting_id = greeting3_.c_greeting_id"//
        + "       LEFT OUTER JOIN ad_user aduserlist4_"//
        + "                    ON e.c_bpartner_id = aduserlist4_.c_bpartner_id"//
        + "       CROSS JOIN c_bp_group businesspa5_ "
        + " WHERE  e.c_bp_group_id = businesspa5_.c_bp_group_id" //
        + " AND 1 = 1" //
        + " AND e.iscustomer = 'Y'" //
        + " AND e.isactive = 'Y'"//
        + " AND ( e.ad_client_id IN :clients )"//
        + " AND ( e.ad_org_id IN :orgs )"//
        + " AND (aduserlist4_.AD_User_ID in (select max(aduser6_.AD_User_ID) "//
        + "     FROM AD_User aduser6_ where aduser6_.C_BPartner_ID=e.C_BPartner_ID)) ";
    if (lastUpdated != null) {
      sql += " AND e.updated > :lastUpdated";
    }
    if (lastId != null) {
      sql += " AND e.c_bpartner_id > :lastId";
    }
    sql += " ORDER  BY e.c_bpartner_id " //
        + " LIMIT :limit";

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(jsonParams.getString("organization")))
        .setParameter("limit", limit);
    if (lastId != null) {
      query.setParameter("lastId", lastId);
    }
    return query;
  }

  @Override
  public String getName() {
    return "BusinessPartner";
  }

  private String locations() {
    //@formatter:off
    return " (SELECT CAST(json_agg("
        + "     json_build_object("
        + "         'id', c_bpartner_location_.c_bpartner_location_id,"
        + "         'isShipTo', c_bpartner_location_.isshipto,"
        + "         'isBillTo', c_bpartner_location_.isbillto,"
        + "         'name', c_bpartner_location_.name,"
        + "         'adressLine1', c_location_.address1,"         
        + "         'adressLine2', c_location_.address2,"
        + "         'regionName', c_location_.regionname,"
        + "         'postalCode', c_location_.postal,"   
        + "         'cityName', c_city_.name,"           
        + "         '_identifier', COALESCE(c_location_.address1 || CASE WHEN c_location_.address2 IS NOT NULL THEN ' ' END || c_location_.address2, c_location_.address1, c_location_.address2, c_location_.postal, c_city_.name) )) AS TEXT)"
        + "     FROM c_bpartner_location c_bpartner_location_ " 
        + "       INNER JOIN c_location c_location_ ON c_location_.c_location_id=c_bpartner_location_.c_location_id"
        + "       LEFT JOIN c_city c_city_ ON c_city_.c_city_id=c_location_.c_city_id"        
        + "     WHERE c_bpartner_location_.c_bpartner_id=e.c_bpartner_id) AS locations";
    //@formatter:on
  }

  @Override
  public void transformResult(JSONArray data) throws JSONException {

    for (int i = 0; i < data.length(); i++) {
      JSONObject record = data.getJSONObject(i);
      if (record.optString("locations", null) != null) {
        record.put("locations", new JSONArray(record.getString("locations")));
      }

    }
  }
}
