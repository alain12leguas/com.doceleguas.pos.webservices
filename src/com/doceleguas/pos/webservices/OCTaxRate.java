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
import org.openbravo.model.common.enterprise.OrganizationInformation;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Region;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.POSUtils;

public class OCTaxRate extends Model {
  @SuppressWarnings("deprecation")
  @Override
  public JSONArray exec(JSONObject jsonParams) throws JSONException {

    String sql = "SELECT financialm0_.EM_Ef_Group AS col_0_0_," //
        + "       financialm0_.EM_Saft_Taxexemption_ID AS col_1_0_," //
        + "       financialm0_.EM_Saft_Taxcode AS col_2_0_," //
        + "       financialm0_.C_Tax_ID AS col_4_0_," //
        + "       financialm0_.Description AS col_5_0_," //
        + "       financialm0_.TaxIndicator AS col_6_0_," //
        + "       financialm0_.IsActive AS col_7_0_," //
        + "       financialm0_.IsSummary AS col_8_0_," //
        + "       financialm0_.Rate AS col_9_0_," //
        + "       financialm0_.Parent_Tax_ID AS col_10_0_," //
        + "       financialm0_.ValidFrom AS col_11_0_," //
        + "       financialm1_.C_TaxCategory_ID AS col_12_0_," //
        + "       financialm1_.Asbom AS col_13_0_," // "
        + "       financialm0_.C_BP_TaxCategory_ID AS col_14_0_," //
        + "       financialm0_.SOPOType AS col_15_0_," //
        + "       financialm0_.DocTaxAmount AS col_16_0_," //
        + "       financialm0_.C_Country_ID AS col_17_0_," //
        + "       financialm0_.C_Region_ID AS col_18_0_," //
        + "       financialm0_.To_Country_ID AS col_19_0_," //
        + "       financialm0_.To_Region_ID AS col_20_0_," //
        + "       financialm0_.Line AS col_21_0_," //
        + "       financialm0_.Cascade AS col_22_0_," //
        + "       financialm0_.BaseAmount AS col_23_0_," //
        + "       financialm0_.C_TaxBase_ID AS col_24_0_," //
        + "       financialm0_.IsDefault AS col_25_0_," //
        + "       financialm0_.IsTaxExempt AS col_26_0_," //
        + "       financialm0_.IsWithholdingTax AS col_27_0_," //
        + "       financialm0_.IsNoTaxable AS col_28_0_," //
        + "       financialm0_.IsTaxUndeductable AS col_29_0_," //
        + "       financialm0_.IsTaxDeductable AS col_30_0_," //
        + "       financialm0_.Originalrate AS col_31_0_," //
        + "       financialm0_.Deducpercent AS col_32_0_," //
        + "       financialm0_.IsNoVAT AS col_33_0_," //
        + "       financialm0_.IsCashVAT AS col_34_0_," //
        + "       financialm0_.IsSpecialTax AS col_35_0_," //
        + "       financialm0_.EM_Obrtser_Vatindicationcode AS col_36_0_," //
        + "       financialm0_.EM_Obrtser_Vatexclusioncode AS col_37_0_," //
        + "       financialm0_.EM_Rtp_Department AS col_38_0_," //
        + "       financialm0_.EM_Rtp_Index AS col_39_0_," //
        + "       financialm0_.EM_Rtp_Type AS col_40_0_" //
        + " FROM C_Tax financialm0_" // "
        + " INNER JOIN C_TaxCategory financialm1_ ON financialm0_.C_TaxCategory_ID=financialm1_.C_TaxCategory_ID" // "
        + " WHERE (financialm0_.AD_Client_ID IN :clients)" // "
        + "  AND (financialm0_.AD_Org_ID IN :orgs)" //
        + "  AND financialm0_.IsActive='Y'" //
        + "  AND (financialm0_.SOPOType IN ('S', 'B'))" //
        + "  AND (financialm0_.IsSummary='N'" //
        + "       OR financialm1_.Asbom='Y')" //
        + "  AND (financialm0_.C_Country_ID=:countryId" //
        + "       OR (financialm0_.C_Country_ID IS NULL)" //
        + "       AND NOT (EXISTS" // "
        + "                  (SELECT 1" // "
        + "                   FROM C_Tax_Zone financialm3_" // "
        + "                   WHERE financialm3_.C_Tax_ID=financialm0_.C_Tax_ID))" // "
        + "       OR EXISTS" //
        + "         (SELECT 1" //
        + "          FROM C_Tax_Zone financialm4_" //
        + "          WHERE financialm4_.C_Tax_ID=financialm0_.C_Tax_ID" //
        + "            AND financialm4_.From_Country_ID=:countryId)" //
        + "       OR EXISTS" //
        + "         (SELECT 1" //
        + "          FROM C_Tax_Zone financialm5_" //
        + "          WHERE financialm5_.C_Tax_ID=financialm0_.C_Tax_ID" //
        + "            AND (financialm5_.From_Country_ID IS NULL)))" //
        + "  AND (financialm0_.C_Region_ID=:regionId" //
        + "       OR (financialm0_.C_Region_ID IS NULL)" //
        + "       AND NOT (EXISTS" //
        + "                  (SELECT 1" //
        + "                   FROM C_Tax_Zone financialm6_" // "
        + "                   WHERE financialm6_.C_Tax_ID=financialm0_.C_Tax_ID))" // "
        + "       OR EXISTS" //
        + "         (SELECT 1" //
        + "          FROM C_Tax_Zone financialm7_" //
        + "          WHERE financialm7_.C_Tax_ID=financialm0_.C_Tax_ID" //
        + "            AND financialm7_.From_Region_ID=:regionId)" //
        + "       OR EXISTS" //
        + "         (SELECT 1" //
        + "          FROM C_Tax_Zone financialm8_" //
        + "          WHERE financialm8_.C_Tax_ID=financialm0_.C_Tax_ID" //
        + "            AND (financialm8_.From_Region_ID IS NULL)))";

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
    return "TaxRate";
  }

}
