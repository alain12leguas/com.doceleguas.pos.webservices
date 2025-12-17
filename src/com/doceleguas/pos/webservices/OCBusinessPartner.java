package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

public class OCBusinessPartner extends Model {

  @Override
  public JSONObject exec(JSONObject jsonParams) throws JSONException {
    // OBContext.getOBContext()
    // .getOrganizationStructureProvider()
    // .getNaturalTree(jsonsent.getString("organization"));
    // String organization = jsonParams.getString("organization");
    // String organizationList = StringUtils
    // .join(new OrganizationStructureProvider().getNaturalTree(organization), "','");
    String sql = "SELECT businesspa0_.c_bpartner_id  AS id,"
        + "       businesspa0_.ad_org_id AS ad_org_id," //
        + "       businesspa0_.NAME AS name," //
        + "       businesspa0_.value," //
        + "       businesspa0_.description," //
        + "       businesspa0_.taxid," //
        + "       businesspa0_.so_bp_taxcategory_id," //
        + "       businesspa0_.m_pricelist_id," //
        + "       businesspa0_.fin_paymentmethod_id," //
        + "       businesspa0_.c_paymentterm_id," //
        + "       businesspa0_.invoicerule," //
        + "       aduserlist4_.email," + " //" + "      aduserlist4_.ad_user_id," //
        + "       aduserlist4_.phone," //
        + "       aduserlist4_.phone2," //
        + "       aduserlist4_.firstname," //
        + "       aduserlist4_.lastname," //
        + "       pricingpri1_.istaxincluded," //
        + "       pricingpri1_.c_currency_id," //
        + "       pricingpri1_.NAME," //
        + "       businesspa0_.c_bp_group_id," //
        + "       adlanguage2_.ad_language," //
        + "       adlanguage2_.NAME," //
        + "       greeting3_.c_greeting_id,"//
        + "       greeting3_.NAME,"//
        + "       aduserlist4_.comments,"//
        + "       businesspa0_.so_creditlimit - businesspa0_.so_creditused," //
        + "       aduserlist4_.commercialauth,"//
        + "       aduserlist4_.viaemail,"//
        + "       aduserlist4_.viasms,"//
        + "       aduserlist4_.commercialdate," //
        + "       Now() "//
        + "FROM   c_bpartner businesspa0_"//
        + "       INNER JOIN m_pricelist pricingpri1_" //
        + "               ON businesspa0_.m_pricelist_id = pricingpri1_.m_pricelist_id"//
        + "       LEFT OUTER JOIN ad_language adlanguage2_"//
        + "                    ON businesspa0_.ad_language = adlanguage2_.ad_language"//
        + "       LEFT OUTER JOIN c_greeting greeting3_"//
        + "                    ON businesspa0_.c_greeting_id = greeting3_.c_greeting_id"//
        + "       LEFT OUTER JOIN ad_user aduserlist4_"//
        + "                    ON businesspa0_.c_bpartner_id = aduserlist4_.c_bpartner_id"//
        + "       CROSS JOIN c_bp_group businesspa5_ "
        + "WHERE  businesspa0_.c_bp_group_id = businesspa5_.c_bp_group_id" //
        + "       AND 1 = 1" //
        + "       AND businesspa0_.iscustomer = 'Y'" //
        + "       AND businesspa0_.isactive = 'Y'"//
        + "       AND ( businesspa0_.ad_client_id IN :clients )"//
        + "       AND ( businesspa0_.ad_org_id IN :orgs )"//
        + "       AND NOT ( EXISTS (SELECT 1" //
        + "                         FROM   ad_user aduser6_"//
        + "                         WHERE  aduser6_.c_bpartner_id ="//
        + "                                businesspa0_.c_bpartner_id) ) "//
        + "ORDER  BY businesspa0_.c_bpartner_id ";

    final ScrollableResults scroll = OBDal.getInstance()
        .getSession()
        .createNativeQuery(sql)
        .setParameterList("clients", OBContext.getOBContext().getReadableClients())
        .setParameterList("orgs", OBContext.getOBContext().getReadableOrganizations())
        .scroll(ScrollMode.FORWARD_ONLY);

    int i = 0;
    try {
      while (scroll.next()) {
        final Object[] resultSet = scroll.get();
        final String p = (String) resultSet[0];
      }
      i++;
      if (i % 100 == 0) {
        OBDal.getInstance().flush();
        OBDal.getInstance().getSession().clear();
      }
    } finally {
      scroll.close();
    }
    return jsonParams;
  }

  @Override
  public String getName() {
    return "BusinessPartner";
  }

}
