package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

public class OCTaxCategory extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String sql = "SELECT " + selectList + ", " //
        + "e.isactive as \"isActive\" " //
        + " FROM C_TaxCategory e" //
        + " WHERE (e.AD_Client_ID IN :clients)" //
        + "  AND (e.AD_Org_ID IN :orgs)" //
        + "  AND e.Asbom='N'";//
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += " AND e.IsActive='Y'";
    }
    sql += " ORDER BY e.IsDefault DESC, e.Name ASC, e.C_TaxCategory_ID ASC";
    sql += " LIMIT :limit ";
    if (offset != 0) {
      sql += " OFFSET :offset";
    }
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(jsonParams.getString("organization")))
        .setParameter("limit", limit);
    if (offset != 0) {
      query.setParameter("offset", offset);
    }
    return query;
  }

  @Override
  public String getName() {
    return "TaxCategory";
  }

}
