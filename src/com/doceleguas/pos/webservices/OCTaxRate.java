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

public class OCTaxRate extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {

    String selectList = jsonParams.getString("selectList");
    Long limit = jsonParams.optLong("limit", 1000);
    Long offset = jsonParams.optLong("offset", 0);
    OBPOSApplications posDetail = POSUtils.getTerminalById(jsonParams.getString("pos"));
    final OrganizationInformation storeInfo = posDetail.getOrganization()
        .getOrganizationInformationList()
        .get(0);

    Country fromCountry = storeInfo.getLocationAddress().getCountry();
    final Region fromRegion = storeInfo.getLocationAddress().getRegion();
    String sql = "SELECT " + selectList + ", "
            + "e.isactive as \"isActive\" "
            + " FROM C_Tax e"
            + " INNER JOIN C_TaxCategory financialm1_ ON e.C_TaxCategory_ID=financialm1_.C_TaxCategory_ID"
            + " WHERE (e.AD_Client_ID IN :clients)"
            + "  AND (e.AD_Org_ID IN :orgs)"
            + "  AND (e.SOPOType IN ('S', 'B'))"
            + "  AND (e.IsSummary='N'"
            + "       OR financialm1_.Asbom='Y')";

    if (fromCountry != null) {
        sql += "  AND (e.C_Country_ID=:countryId"
            + "       OR (e.C_Country_ID IS NULL)"
            + "       AND NOT (EXISTS"
            + "                  (SELECT 1"
            + "                   FROM C_Tax_Zone financialm3_"
            + "                   WHERE financialm3_.C_Tax_ID=e.C_Tax_ID))"
            + "       OR EXISTS"
            + "         (SELECT 1"
            + "          FROM C_Tax_Zone financialm4_"
            + "          WHERE financialm4_.C_Tax_ID=e.C_Tax_ID"
            + "            AND financialm4_.From_Country_ID=:countryId)"
            + "       OR EXISTS"
            + "         (SELECT 1"
            + "          FROM C_Tax_Zone financialm5_"
            + "          WHERE financialm5_.C_Tax_ID=e.C_Tax_ID"
            + "            AND (financialm5_.From_Country_ID IS NULL)))";
    }

    if (fromRegion != null) {
        sql += "  AND (e.C_Region_ID=:regionId"
            + "       OR (e.C_Region_ID IS NULL)"
            + "       AND NOT (EXISTS"
            + "                  (SELECT 1"
            + "                   FROM C_Tax_Zone financialm6_"
            + "                   WHERE financialm6_.C_Tax_ID=e.C_Tax_ID))"
            + "       OR EXISTS"
            + "         (SELECT 1"
            + "          FROM C_Tax_Zone financialm7_"
            + "          WHERE financialm7_.C_Tax_ID=e.C_Tax_ID"
            + "            AND financialm7_.From_Region_ID=:regionId)"
            + "       OR EXISTS"
            + "         (SELECT 1"
            + "          FROM C_Tax_Zone financialm8_"
            + "          WHERE financialm8_.C_Tax_ID=e.C_Tax_ID"
            + "            AND (financialm8_.From_Region_ID IS NULL)))";
    }
    if (jsonParams.optString("lastUpdated", null) != null) {
      sql += " AND e.updated > :lastUpdated";
    } else {
      sql += " AND e.IsActive='Y'";
    }
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
    if (fromCountry !=null) {
    	query.setParameter("countryId", fromCountry.getId());            
    }
	if (fromRegion!=null) {
	    query.setParameter("regionId", fromRegion.getId());    
	}
    if (offset != 0) {
      query.setParameter("offset", offset);
    }    
    return query;
  }

  @Override
  public String getName() {
    return "TaxRate";
  }

}
