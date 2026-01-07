/*
 ************************************************************************************
 * Copyright (C) 2012-2019 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 * You may obtain a copy of the License at http://www.openbravo.com/legal/obcl.html
 * or in the legal folder of this module distribution.
 ************************************************************************************
 */

package com.doceleguas.pos.webservices;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.LoginHandler;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.LoginUtils.RoleDefaults;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.application.window.servlet.CalloutHttpServletResponse;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;
import org.openbravo.erpCommon.obps.ActivationKey;
import org.openbravo.erpCommon.obps.ActivationKey.LicenseRestriction;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.mobile.core.MobileCoreConstants;
import org.openbravo.mobile.core.MobileCoreKernelUtils;
import org.openbravo.mobile.core.MobileDefaultsHandler;
import org.openbravo.mobile.core.MobileStaticResourceComponent;
import org.openbravo.mobile.core.authenticate.MobileAuthenticationKeyUtils;
import org.openbravo.mobile.core.authenticate.MobileKeyAuthenticationManager;
import org.openbravo.mobile.core.servercontroller.MobileServerController;
import org.openbravo.model.ad.access.FormAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.Session;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.db.DalConnectionProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OCMobileCoreLoginHandler extends LoginHandler {

  private static final long serialVersionUID = 1L;
  private static final String LOGIN_RESTRICTED_ROLES_ERROR = "RESTR";
  private static final List<String> NO_PARAMS = Collections.emptyList();

  @Inject
  private MobileDefaultsHandler defaultsHandler;

  @Inject
  private MobileStaticResourceComponent mobileStaticResourceComponent;

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    // always set the cors headers
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);

    // don't process any further requests otherwise sessions are created for OPTIONS
    // requests, the cors headers have already been set so can return
    if (request.getMethod().equals("OPTIONS")) {
      return;
    }

    super.service(request, response);
  }

  @Override
  public void doOptions(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // normally this is already handled in the service method above,
    // but to be complete implemented the doOptions as well
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    final boolean ignoreLoginController = ignoreLoginController();
    if (ignoreLoginController
        || OCMobileCoreLoginController.getInstance().shouldAllowLogin(this.getClass().getName())) {
      res.setContentType("application/json;charset=UTF-8");
      CalloutHttpServletResponse loginHandlerResponse = new CalloutHttpServletResponse(res);

      super.doPost(req, loginHandlerResponse);
      final VariablesSecureApp vars = new VariablesSecureApp(req);
      final String appName = vars.getStringParameter("appName");

      String user = (String) req.getSession().getAttribute("#AD_USER_ID");

      vars.setSessionValue("#AD_User_ID", user);

      Session session = null;

      try {
        JSONObject originalResult = loginHandlerResponse.getOutputFromWriter().equals("")
            ? new JSONObject()
            : new JSONObject(loginHandlerResponse.getOutputFromWriter());

        final String sessionId = vars.getSessionValue("#AD_Session_ID");
        OBContext.setAdminMode(false);

        if (!StringUtils.isEmpty(sessionId)) {
          session = OBDal.getInstance().get(Session.class, sessionId);
        }

        if (originalResult.has("showMessage") && originalResult.getBoolean("showMessage")) {
          boolean validMobileSession = (session != null
              && LOGIN_RESTRICTED_ROLES_ERROR.equals(session.getLoginStatus()))
              || ActivationKey.getInstance()
                  .checkOPSLimitations(sessionId,
                      getSessionType()) == LicenseRestriction.POS_TERMINALS_EXCEEDED;

          if (!validMobileSession) {
            // show invalid session message and go away
            PrintWriter q = res.getWriter();
            q.write(loginHandlerResponse.getOutputFromWriter());
            q.close();
            return;
          }
        }
        final String userId = (String) req.getSession().getAttribute("#Authenticated_user");
        Role role = getMobileRole(userId, appName);

        JSONObject jsonMsg = new JSONObject();

        if (role != null) {
          RoleDefaults defaults = getDefaults(req, res, userId, role.getId(), session);
          if (defaults == null) {
            return;
          }

          completeLogin(vars, userId, defaults);

          vars.setSessionValue("#AD_Role_ID", defaults.role);
          session.setLoginStatus(getSessionType());
          session.setSessionActive(true);

          jsonMsg.put("showMessage", false);
          jsonMsg.put("userId", userId);
          if (MobileKeyAuthenticationManager.isInstalled()) {
            jsonMsg.put("authenticationToken",
                MobileAuthenticationKeyUtils.getEncryptedAuthenticationToken(
                    OBContext.getOBContext().getCurrentClient().getId(),
                    OBContext.getOBContext().getCurrentOrganization().getId(), userId,
                    defaults.role,
                    (String) RequestContext.get().getSessionAttribute("POSTerminal")));
            jsonMsg.put("authenticationClient", defaults.client);
          }

          final Map<String, Object> parameters = MobileCoreKernelUtils
              .getParameterMap(getServletContext(), req);
          if (parameters.get("appName") != null) {
            parameters.put("_appName", parameters.get("appName"));
          }
          mobileStaticResourceComponent.setParameters(parameters);
          if (!MobileServerController.getInstance().isThisAStoreServer()) {
            jsonMsg.put("sourceVersion", mobileStaticResourceComponent.getStaticResourceFileName());
          }
        } else {
          errorLogin(res, vars, session, "OBMOBC_NO_POS_ROLE_TITLE", "OBMOBC_NO_POS_ROLE_MSG",
              Collections.singletonList(defaultsHandler.getDefaults(appName).getAppName()));
          return;
        }

        final PrintWriter out = res.getWriter();
        out.print(jsonMsg.toString());
        out.close();
      } catch (Exception e) {
        log4j.error("Error in POS login", e);
        try {
          errorLogin(res, vars, session, "Error", e.getMessage(),
              Collections.singletonList(defaultsHandler.getDefaults(appName).getAppName()));
        } catch (Exception e1) {
          log4j.error("Error setting error msg", e1);
        }
      } finally {
        OBDal.getInstance().flush(); // flushing in admin mode
        OBContext.restorePreviousMode();
      }
    } else {
      res.setContentType("application/json;charset=UTF-8");
      errorLoginNoSession(res, null, "OBMOBC_LOGIN_SYSOVERLOADED_TITLE",
          "OBMOBC_LOGIN_SYSOVERLOADED_MSG", NO_PARAMS);
    }
  }

  protected void errorLoginNoSession(HttpServletResponse res, VariablesSecureApp vars, String title,
      String msg, List<String> arguments) {

    Client systemClient = OBDal.getInstance().get(Client.class, "0");
    String language = systemClient.getLanguage().getLanguage();
    DalConnectionProvider cp = new DalConnectionProvider(false);
    String finalMsg = Utility.messageBD(cp, msg, language);
    int i = 0;
    for (String arg : arguments) {
      finalMsg = finalMsg.replace("%" + i, arg);
      i++;
    }

    JSONObject jsonMsg = new JSONObject();
    try {
      jsonMsg.put("showMessage", true);
      jsonMsg.put("messageType", "Error");
      jsonMsg.put("messageTitle", Utility.messageBD(cp, title, language));
      jsonMsg.put("messageText", finalMsg);
      PrintWriter out;
      out = res.getWriter();
      out.print(jsonMsg.toString());
      out.close();
    } catch (Exception e) {
      // Json exception. Won't happen.
    }
  }

  protected void errorLogin(HttpServletResponse res, VariablesSecureApp vars, Session session,
      String title, String msg, List<String> arguments) throws JSONException, IOException {
    session.setSessionActive(false);
    session.setLoginStatus("F");
    vars.clearSession(true);
    errorLoginNoSession(res, vars, title, msg, arguments);

  }

  private void completeLogin(VariablesSecureApp vars, String userId, RoleDefaults defaults)
      throws ServletException {
    String strLanguage = "";
    String strIsRTL = "";
    String strRole = "";
    String strClient = "";
    String strOrg = "";
    String strWarehouse = "";

    final User user = OBDal.getInstance().get(User.class, userId);

    strRole = defaults.role;
    strClient = defaults.client;
    strOrg = defaults.org;
    strWarehouse = defaults.warehouse;

    if (vars.getStringParameter("loginlanguage") != null
        && !vars.getStringParameter("loginlanguage").equals("")) {
      strLanguage = OBDal.getInstance()
          .get(Language.class, vars.getStringParameter("loginlanguage"))
          .getLanguage();
      strIsRTL = OBDal.getInstance()
          .get(Language.class, vars.getStringParameter("loginlanguage"))
          .isRTLLanguage() ? "Y" : "N";
    } else {
      Language lang = getDefaultLanguage(user, defaults);
      strLanguage = lang.getLanguage();
      strIsRTL = lang.isRTLLanguage() ? "Y" : "N";
    }

    // note fill session arguments will set the LOGGINGIN session var
    // to N
    DalConnectionProvider cp = new DalConnectionProvider(false);
    if (LoginUtils.fillSessionArguments(cp, vars, userId, strLanguage, strIsRTL, strRole, strClient,
        strOrg, strWarehouse)) {
      readProperties(vars, globalParameters.getOpenbravoPropertiesPath());
      readNumberFormat(vars, globalParameters.getFormatPath());
    } else {
      // Re-login
      log4j.error("Unable to fill session Arguments for: " + userId);
      throw new OBException(OBMessageUtils.messageBD("OBMOBC_NoLoginAccess"));
    }

    // Login process if finished, set the flag as not logging in
    // this flag may not be removed from the session, it must be set
    // to N to prevent re-initializing the session continuously
    vars.setSessionValue("#loggingIn", "N");
  }

  /**
   * Copied from HSAS
   */
  protected void readProperties(VariablesSecureApp vars, String strFileProperties) {
    // Read properties file.
    final Properties properties = new Properties();
    try {

      properties.load(new FileInputStream(strFileProperties));
      final String javaDateFormat = properties.getProperty("dateFormat.java");
      vars.setSessionValue("#AD_JavaDateFormat", javaDateFormat);

      final String javaDateTimeFormat = properties.getProperty("dateTimeFormat.java");
      vars.setSessionValue("#AD_JavaDateTimeFormat", javaDateTimeFormat);

      final String sqlDateTimeFormat = properties.getProperty("dateTimeFormat.sql");
      vars.setSessionValue("#AD_SqlDateTimeFormat", sqlDateTimeFormat);

      final String jsDateFormat = properties.getProperty("dateFormat.js");
      vars.setSessionValue("#AD_JsDateFormat", jsDateFormat);

      final String sqlDateFormat = properties.getProperty("dateFormat.sql");
      vars.setSessionValue("#AD_SqlDateFormat", sqlDateFormat);

      final String pentahoServer = properties.getProperty("pentahoServer");
      vars.setSessionValue("#pentahoServer", pentahoServer);

      final String sourcePath = properties.getProperty("source.path");
      vars.setSessionValue("#sourcePath", sourcePath);

      if (log4j.isDebugEnabled()) {
        log4j.debug("strFileProperties: " + strFileProperties);
        log4j.debug("javaDateFormat: " + javaDateFormat);
        log4j.debug("javaDateTimeFormat: " + javaDateTimeFormat);
        log4j.debug("jsDateFormat: " + jsDateFormat);
        log4j.debug("sqlDateFormat: " + sqlDateFormat);
        log4j.debug("pentahoServer: " + pentahoServer);
        log4j.debug("sourcePath: " + sourcePath);
      }
    } catch (final IOException e) {
      // catch possible io errors from readLine()
      log4j.error("Error reading properties", e);
    }
  }

  /**
   * Copied from HSAS
   */
  protected void readNumberFormat(VariablesSecureApp vars, String strFormatFile) {
    String strNumberFormat = "###,##0.00"; // Default number format
    String strGroupingSeparator = ","; // Default grouping separator
    String strDecimalSeparator = "."; // Default decimal separator
    final String formatNameforJrxml = "euroInform"; // Name of the format to use
    final HashMap<String, String> formatMap = new HashMap<String, String>();

    try {
      // Reading number format configuration
      final DocumentBuilder docBuilder = XMLUtil.getInstance().newDocumentBuilder();
      final Document doc = docBuilder.parse(new File(strFormatFile));
      doc.getDocumentElement().normalize();
      final NodeList listOfNumbers = doc.getElementsByTagName("Number");
      final int totalNumbers = listOfNumbers.getLength();
      for (int s = 0; s < totalNumbers; s++) {
        final Node NumberNode = listOfNumbers.item(s);
        if (NumberNode.getNodeType() == Node.ELEMENT_NODE) {
          final Element NumberElement = (Element) NumberNode;
          final String strNumberName = NumberElement.getAttributes()
              .getNamedItem("name")
              .getNodeValue();
          // store in session all the formats
          final String strFormatOutput = NumberElement.getAttributes()
              .getNamedItem("formatOutput")
              .getNodeValue();
          formatMap.put(strNumberName, strFormatOutput);
          vars.setSessionValue("#FormatOutput|" + strNumberName, strFormatOutput);
          vars.setSessionValue("#DecimalSeparator|" + strNumberName,
              NumberElement.getAttributes().getNamedItem("decimal").getNodeValue());
          vars.setSessionValue("#GroupSeparator|" + strNumberName,
              NumberElement.getAttributes().getNamedItem("grouping").getNodeValue());
          // set the numberFormat to be used in the renderJR function
          if (strNumberName.equals(formatNameforJrxml)) {
            strDecimalSeparator = NumberElement.getAttributes()
                .getNamedItem("decimal")
                .getNodeValue();
            strGroupingSeparator = NumberElement.getAttributes()
                .getNamedItem("grouping")
                .getNodeValue();
            strNumberFormat = strFormatOutput;
          }
        }
      }
    } catch (final Exception e) {
      log4j.error("error reading number format", e);
    }
    vars.setSessionObject("#FormatMap", formatMap);
    vars.setSessionValue("#AD_ReportNumberFormat", strNumberFormat);
    vars.setSessionValue("#AD_ReportGroupingSeparator", strGroupingSeparator);
    vars.setSessionValue("#AD_ReportDecimalSeparator", strDecimalSeparator);
  }

  protected Role getMobileRole(String userId, String appName) {
    final User user = OBDal.getInstance().get(User.class, userId);

    // get default POS role
    Role role = (Role) user.get(defaultsHandler.getDefaults(appName).getDefaultRoleProperty());
    if (role != null) {
      if (hasMobileAccess(role, appName)) {
        return role;
      } else {
        log4j.warn(
            "Default Mobile role (" + role.getName() + ") of user " + user + " has no access");
      }
    }

    // get standard default role
    role = user.getDefaultRole();
    if (role != null) {
      if (hasMobileAccess(role, appName)) {
        return role;
      }
    }

    // take first role with POS access
    for (UserRoles r : user.getADUserRolesList()) {
      role = r.getRole();
      if (hasMobileAccess(role, appName)) {
        return role;
      }
    }

    // no rule to use
    log4j.warn("User " + user + " has no Mobile role");
    return null;

  }

  protected boolean hasMobileAccess(Role role, String appName) {

    if (RequestContext.get().getSession() != null && Boolean.TRUE
        .equals(RequestContext.get().getSessionAttribute("MOBILE_ACCESS_" + appName))) {
      return true;
    }

    String formId = getFormId(appName);
    if (formId == null) {
      // Form ID is not defined for this app, access is granted by having access to the ERP
      if (RequestContext.get().getSession() != null) {
        RequestContext.get().setSessionAttribute("MOBILE_ACCESS_" + appName, Boolean.TRUE);
      }
      return true;
    }
    String hql = "select 1 from " + FormAccess.ENTITY_NAME
        + " where role.id=:roleId and specialForm.id=:formId and role.forPortalUsers = false and active='Y'";
    OBContext.setAdminMode(false);
    try {
      @SuppressWarnings("rawtypes")
      Query hbQuery = OBDal.getInstance().getSession().createQuery(hql);
      hbQuery.setParameter("roleId", role.getId());
      hbQuery.setParameter("formId", formId);
      hbQuery.setMaxResults(1);
      if (hbQuery.list().iterator().hasNext()) {
        if (RequestContext.get().getSession() != null) {
          RequestContext.get().setSessionAttribute("MOBILE_ACCESS_" + appName, Boolean.TRUE);
        }
        return true;
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return false;
  }

  /**
   * Override this method to return the ad_form_id that defines access to the mobile application
   */
  protected String getFormId(String appName) {
    return defaultsHandler.getDefaults(appName).getFormId();
  }

  /**
   * Sets the actual session parameters. It obtain defaults based on ERP's but this can be overriden
   * for a app specific logic. It can return null if login is to available, in this case session
   * should be invalidated.
   *
   */
  protected RoleDefaults getDefaults(HttpServletRequest req, HttpServletResponse res, String userId,
      String roleId, Session session) {
    try {
      return LoginUtils.getLoginDefaults(userId, roleId, myPool);
    } catch (Exception e) {
      log4j.error("Error getting login defaults", e);
      return null;
    }
  }

  /**
   * Determines language for the new session
   */
  protected Language getDefaultLanguage(User user, RoleDefaults defaults) {
    if (user.getDefaultLanguage() != null) {
      return user.getDefaultLanguage();
    } else {
      return (Language) OBDal.getInstance()
          .createCriteria(Language.class)
          .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, "en_US"))
          .list()
          .get(0);
    }
  }

  /** Returns how the successful session will be marked in ad_session. It can be app specific. */
  @Override
  protected String getSessionType() {
    return MobileCoreConstants.SESSION_TYPE;
  }

  /** Returns how if the app should ignore login controller. */
  protected boolean ignoreLoginController() {
    return false;
  }

  /**
   * @deprecated by default mobile applications count for concurrent users, unless centrally decided
   *             not to do so
   */
  @Deprecated
  protected boolean checkConcurrentUsers() {
    return false;
  }

  @Override
  protected boolean isBackOfficeLogin() {
    return false;
  }

}
