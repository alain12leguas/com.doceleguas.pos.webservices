package com.doceleguas.pos.webservices.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.businessUtility.Preferences.QueryFilter;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.retail.posterminal.OBPOSApplications;

public class WebServiceUtils {
  public static OBPOSApplications getTerminalByKeyIdentifier(String terminalKeyIdentifier)
      throws TerminalAuthenticationException {
    OBCriteria<OBPOSApplications> qApp = OBDal.getInstance()
        .createCriteria(OBPOSApplications.class);
    qApp.add(Restrictions.eq(OBPOSApplications.PROPERTY_TERMINALKEY, terminalKeyIdentifier));
    qApp.setFilterOnReadableOrganization(false);
    qApp.setFilterOnReadableClients(false);
    List<OBPOSApplications> apps = qApp.list();
    if (apps.size() == 1) {
      OBPOSApplications terminal = ((OBPOSApplications) apps.get(0));
      return terminal;
    } else {
      throw new TerminalAuthenticationException(
          OBMessageUtils.getI18NMessage("OBPOS_WrongTerminalKeyIdentifier", null));
    }
  }

  public static String getTerminalAuthentication() throws PropertyException {
    String terminalAuthenticationValue;
    try {
      Map<QueryFilter, Boolean> terminalAuthenticationQueryFilters = new HashMap<>();
      terminalAuthenticationQueryFilters.put(QueryFilter.ACTIVE, true);
      terminalAuthenticationQueryFilters.put(QueryFilter.CLIENT, false);
      terminalAuthenticationQueryFilters.put(QueryFilter.ORGANIZATION, false);
      terminalAuthenticationValue = Preferences.getPreferenceValue("OBPOS_TerminalAuthentication",
          true, null, null, null, null, (String) null, terminalAuthenticationQueryFilters);
    } catch (PropertyException e) {
      terminalAuthenticationValue = "Y";
    }
    return terminalAuthenticationValue;
  }

  public static class TerminalAuthenticationException extends Exception {
    private static final long serialVersionUID = 1L;

    public TerminalAuthenticationException(String message) {
      super(message);
    }
  }
}
