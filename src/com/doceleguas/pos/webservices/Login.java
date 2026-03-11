package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.mobile.core.MobileDefaults;
import org.openbravo.mobile.core.login.ProfileUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Language;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.POSDefaults;
import org.openbravo.retail.posterminal.POSLoginHandler;

import com.doceleguas.pos.webservices.utils.WebServiceUtils;
import com.doceleguas.pos.webservices.utils.WebServiceUtils.TerminalAuthenticationException;

public class Login extends POSLoginHandler {
  private static final long serialVersionUID = 1L;

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    OBContext.setAdminMode(false);
    try {
      JSONObject terminalAuthResponse = checkTerminalAuth(req);
      if (!terminalAuthResponse.getBoolean("success")) {
        res.getWriter().write(terminalAuthResponse.toString());
        return;
      }
      ResponseBufferWrapper wrappedRes = new ResponseBufferWrapper(res);
      super.doPost(req, wrappedRes);
      JSONObject jsonResponse = new JSONObject(wrappedRes.getCapturedContent());
      if (jsonResponse.optBoolean("showMessage", false) && jsonResponse.optString("messageTitle")
          .equals("Web POS terminal (%0) does not exist")) {
        jsonResponse.put("messageTitle", "");
      }
      if (!jsonResponse.optBoolean("showMessage", false)) {
        Role defaultRole = OBContext.getOBContext().getUser().getOBPOSDefaultPOSRole();
        if (defaultRole == null) {
          throw new RoleDoNotExistException();
        }
        JSONObject user = new JSONObject();
        user.put("name", OBContext.getOBContext().getUser().getName());
        user.put("id", OBContext.getOBContext().getUser().getId());
        user.put("defaultRole", getDefaultRoleJson());
        user.put("roles", getRoles());
        user.put("defaultLanguage", getDefaultLanguageJson());
        user.put("languages", getLanguages());
        jsonResponse.put("user", user);
      }
      // JSONObject profilesJson = profile.exec(new JSONObject("{\"appName\":\"WebPOS\"}"));
      PrintWriter writer = res.getWriter();
      writer.print(jsonResponse.toString());
      writer.flush();
    } catch (RoleDoNotExistException e) {
      OBError error = new OBError();
      error.setTitle(null);
      error.setMessage("The user doesn't have a defined role.");
      responseWithError(res, error);
    } catch (TerminalAuthenticationException e) {
      OBError error = new OBError();
      error.setTitle(null);
      error.setMessage(e.getMessage());
      responseWithError(res, error);
    } catch (Exception e) {
      OBError error = new OBError();
      e.printStackTrace();
      error.setTitle(OBMessageUtils.getI18NMessage("IDENTIFICATION_FAILURE_TITLE", null));
      error.setMessage(OBMessageUtils.getI18NMessage("IDENTIFICATION_FAILURE_MSG", null));
      responseWithError(res, error);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private JSONObject checkTerminalAuth(HttpServletRequest request)
      throws TerminalAuthenticationException, JSONException {
    String cacheSessionId = request.getParameter("cacheSessionId");
    String terminalKeyIdentifier = request.getParameter("terminalKeyIdentifier");
    JSONObject response = new JSONObject();
    try {
      String terminalAuthenticationValue = WebServiceUtils.getTerminalAuthentication();
      if (Preferences.NO.equals(terminalAuthenticationValue)) {
        response.put("success", true);
        return response;
      }
      if (terminalKeyIdentifier == null) {
        response.put("success", false);
        response.put("error", "MISSING_TERMINAL_IDENTIFIER");
        return response;
      }

      OBPOSApplications terminal = WebServiceUtils
          .getTerminalByKeyIdentifier(terminalKeyIdentifier);

      if (!terminal.isLinked()) {
        terminal.setCurrentCacheSession(cacheSessionId);
        terminal.setLinked(true);
        response.put("success", true);
        return response;
      }
      if (terminal.isLinked() && !terminal.getCurrentCacheSession().equals(cacheSessionId)) {
        throw new TerminalAuthenticationException(
            OBMessageUtils.getI18NMessage("OBPOS_TerminalAuthChangeMsg", null));
      }
    } catch (final PropertyException ignore) {

    }
    response.put("success", true);
    return response;
  }

  private JSONObject getDefaultRoleJson() throws JSONException {
    Role role = OBContext.getOBContext().getUser().getOBPOSDefaultPOSRole();
    JSONObject response = new JSONObject();
    response.put("id", role.getId());
    response.put("identifier", role.getIdentifier());
    return response;
  }

  private JSONObject getDefaultLanguageJson() throws JSONException {
    User user = OBContext.getOBContext().getUser();
    Language language;
    if (user.getDefaultLanguage() != null) {
      language = user.getDefaultLanguage();
    } else if (user.getOBPOSDefaultPOSRole().getOBPOSDefaultPosLanguage() != null) {
      language = user.getOBPOSDefaultPOSRole().getOBPOSDefaultPosLanguage();
    } else {
      language = OBDal.getInstance()
          .createCriteria(Language.class)
          .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, "en_US"))
          .list()
          .get(0);
    }
    JSONObject response = new JSONObject();
    response.put("id", language.getId());
    response.put("identifier", language.getIdentifier());
    return response;
  }

  private JSONArray getRoles() throws JSONException {
    OCProfile profile = new OCProfile();
    final List<Role> roles = profile.getRoles(new POSDefaults());
    JSONArray rolesArray = new JSONArray();
    for (Role role : roles) {
      final JSONObject jsonRole = new JSONObject();
      jsonRole.put("id", role.getId());
      jsonRole.put("identifier", role.getIdentifier());
      rolesArray.put(jsonRole);
    }
    return rolesArray;
  }

  private JSONArray getLanguages() throws JSONException {

    final OBQuery<Language> languages = OBDal.getInstance()
        .createQuery(Language.class, "(" + Language.PROPERTY_SYSTEMLANGUAGE + "=true or "
            + Language.PROPERTY_BASELANGUAGE + "=true)");
    languages.setFilterOnReadableClients(false);
    languages.setFilterOnReadableOrganization(false);
    JSONArray jsonArray = new JSONArray();
    for (Language language : languages.list()) {
      final JSONObject json = new JSONObject();
      json.put("id", language.getId());
      json.put("identifier", language.getIdentifier());
      jsonArray.put(json);
    }
    return jsonArray;
  }

  private void responseWithError(HttpServletResponse res, OBError err) {
    JSONObject errorResponse = new JSONObject();
    try {
      errorResponse.put("messageType", "error");
      errorResponse.put("messageTitle", err.getTitle());
      errorResponse.put("messageText", err.getMessage());
      PrintWriter out = res.getWriter();
      out.print(errorResponse.toString());
      out.flush();
    } catch (Exception e1) {
      e1.printStackTrace();
    }
  }

  class RoleDoNotExistException extends OBException {
    private static final long serialVersionUID = 1L;

    public RoleDoNotExistException() {
      super("The user does not have a defined role.");
    }
  }

  class OCProfile extends ProfileUtils {
    @Override
    protected List<Role> getRoles(MobileDefaults defaults) {
      // String appName = "WebPOS";
      return super.getRoles(defaults);
    }
  }

  class TerminalLinkedException extends OBException {
    private static final long serialVersionUID = 1L;

    public TerminalLinkedException(String msg) {
      super(msg);
    }
  }

}
