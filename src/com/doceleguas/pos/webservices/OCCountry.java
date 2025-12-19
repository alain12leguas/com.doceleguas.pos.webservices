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

public class OCCountry extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public JSONArray exec(JSONObject jsonParams) throws JSONException {

    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", -1);
    String sql = "SELECT " + selectList + " " //
        + " FROM c_country e " //
        + "  WHERE e.isactive='Y' "//
        + "   AND      (e.ad_client_id IN :clients) "//
        + "  ORDER BY e.NAME ASC "//
        + " LIMIT :limit ";
    if (offset != -1) {
      sql += "OFFSET :offset";
    }
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameter("limit", limit);
    if (offset != -1) {
      query.setParameter("offset", offset);
    }
    query.scroll(ScrollMode.FORWARD_ONLY);
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
    return "Country";
  }

}
