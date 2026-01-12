package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

public class OCRegion extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String sql = "SELECT " + selectList + ", " //
        + " e.isactive as \"isActive\" " //
        + " FROM C_Region e " //
        + "  WHERE (e.ad_client_id IN :clients) ";//
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += "  AND e.IsActive='Y'";
    }
    sql += "  ORDER BY e.NAME ASC ";//
    sql += " LIMIT :limit ";
    if (offset != 0) {
      sql += "OFFSET :offset";
    }
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameter("limit", limit);
    if (offset != 0) {
      query.setParameter("offset", offset);
    }
    return query;
  }

  @Override
  public String getName() {
    return "Region";
  }

}
