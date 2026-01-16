package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.enterprise.OrganizationInformation;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Region;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.POSUtils;

public class OCTaxZone extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    String sql = "SELECT " + selectList + ", " //
        + "e.isactive as \"isActive\" " //
        + " FROM C_Tax_Zone e" //
        + "       INNER JOIN C_Tax c_tax_ on e.C_Tax_ID=c_tax_.C_Tax_ID" //
        + " WHERE (e.AD_Client_ID IN :clients)" //
        + "  AND (e.AD_Org_ID IN :orgs)" //
        + "  AND (e.C_Tax_ID IN " //
        + "       (SELECT financialm2_.C_Tax_ID" //
        + "        FROM C_Tax financialm2_" //
        + "        WHERE (financialm2_.SOPOType IN ('S', 'B'))" //
        + "          AND (financialm2_.IsSummary='N'" //
        + "               OR EXISTS (SELECT 1" //
        + "                          FROM C_TaxCategory financialm3_" //
        + "                          WHERE financialm2_.C_TaxCategory_ID=financialm3_.C_TaxCategory_ID" //
        + "                            AND financialm3_.Asbom='Y'))" //
        + "          AND (financialm2_.C_Country_ID=:countryId" //
        + "               OR (financialm2_.C_Country_ID IS NULL)" //
        + "               AND NOT (EXISTS (SELECT 1" //
        + "                                FROM C_Tax_Zone financialm4_" //
        + "                                WHERE financialm4_.C_Tax_ID=financialm2_.C_Tax_ID))" //
        + "               OR EXISTS (SELECT 1" //
        + "                          FROM C_Tax_Zone financialm5_" //
        + "                          WHERE financialm5_.C_Tax_ID=financialm2_.C_Tax_ID" //
        + "                            AND financialm5_.From_Country_ID=:countryId)" //
        + "               OR EXISTS (SELECT 1" //
        + "                          FROM C_Tax_Zone financialm6_" //
        + "                          WHERE financialm6_.C_Tax_ID=financialm2_.C_Tax_ID" //
        + "                            AND (financialm6_.From_Country_ID IS NULL)))" //
        + "          AND (financialm2_.C_Region_ID=:regionId" //
        + "               OR (financialm2_.C_Region_ID IS NULL)" //
        + "               AND NOT (EXISTS (SELECT 1" //
        + "                                FROM C_Tax_Zone financialm7_" //
        + "                                WHERE financialm7_.C_Tax_ID=financialm2_.C_Tax_ID))" //
        + "               OR EXISTS (SELECT 1" //
        + "                          FROM C_Tax_Zone financialm8_" //
        + "                          WHERE financialm8_.C_Tax_ID=financialm2_.C_Tax_ID" //
        + "                            AND financialm8_.From_Region_ID=:regionId)" //
        + "               OR EXISTS (SELECT 1" //
        + "                          FROM C_Tax_Zone financialm9_" //
        + "                          WHERE financialm9_.C_Tax_ID=financialm2_.C_Tax_ID" //
        + "                            AND (financialm9_.From_Region_ID IS NULL)))))" //
        + "  AND (e.From_Country_ID=:countryId OR e.From_Country_ID IS NULL)" //
        + "  AND (e.From_Region_ID=:regionId OR e.From_Region_ID IS NULL)";//
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += " AND e.IsActive='Y'";
    }
    sql += " ORDER BY e.C_Tax_Zone_ID ASC";
    sql += " LIMIT :limit ";
    if (offset != 0) {
      sql += " OFFSET :offset";
    }
    OBPOSApplications posDetail = POSUtils.getTerminalById(jsonParams.getString("pos"));
    final OrganizationInformation storeInfo = posDetail.getOrganization()
        .getOrganizationInformationList()
        .get(0);
    final Country fromCountry = storeInfo.getLocationAddress().getCountry();
    final Region fromRegion = storeInfo.getLocationAddress().getRegion();
    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameterList("orgs",
            OBContext.getOBContext()
                .getOrganizationStructureProvider()
                .getNaturalTree(jsonParams.getString("organization")))
        .setParameter("countryId", fromCountry.getId())
        .setParameter("regionId", fromRegion.getId())
        .setParameter("limit", limit);
    if (offset != 0) {
      query.setParameter("offset", offset);
    }
    return query;
  }

  @Override
  public String getName() {
    return "TaxZone";
  }

}
