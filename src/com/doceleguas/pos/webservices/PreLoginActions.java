/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.FormAccess;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.retail.posterminal.OBPOSApplications;
import org.openbravo.retail.posterminal.TerminalAccess;

import com.doceleguas.pos.webservices.internal.terminal.OcrePosConstants;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

import com.doceleguas.pos.webservices.utils.WebServiceUtils;
import com.doceleguas.pos.webservices.utils.WebServiceUtils.TerminalAuthenticationException;

public class PreLoginActions implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "GET method not supported. Use POST instead.");
  }

  /**
   * Sends an error response as JSON.
   * 
   * @param response
   *          The HTTP response
   * @param statusCode
   *          The HTTP status code
   * @param message
   *          The error message
   */
  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
    response.setStatus(statusCode);
    try {
      JSONObject errorJson = new JSONObject();
      errorJson.put("success", false);
      errorJson.put("error", true);
      errorJson.put("message", message);
      errorJson.put("statusCode", statusCode);
      response.getWriter().write(errorJson.toString());
    } catch (Exception e) {
      log.error("Error sending error response", e);
      try {
        response.getWriter().write("{\"error\":true,\"message\":\"" + message + "\"}");
      } catch (Exception ex) {
        log.error("Failed to write error response", ex);
      }
    }
  }

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    OBContext.setAdminMode(false);
    try {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      String action = request.getParameter("action");
      String cacheSessionId = request.getParameter("cacheSessionId");
      String terminalKeyIdentifier = request.getParameter("terminalKeyIdentifier");
      String terminalSearchKey = request.getParameter("terminalSearchKey");

      switch (action) {
        case "CHECK_TERMINAL_AUTH":
          try {
            JSONObject result = WebServiceUtils.checkTerminalAuthentication(terminalSearchKey);
            result.put("success", true);
            response.getWriter().write(result.toString());

          } catch (Throwable t) {
            Throwable cause = DbUtility.getUnderlyingSQLException(t);
            log.error("Error in PreLoginActions WebService", cause);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error executing query: " + cause.getMessage());
          }
          break;
        case "LINK_TERMINAL":
          JSONObject responseJson = new JSONObject();

          String username = request.getParameter("paramUsername");
          String password = request.getParameter("paramPassword");

          OBPOSApplications terminal = WebServiceUtils
              .getTerminalByKeyIdentifier(terminalKeyIdentifier);
          if (terminal == null) {
            throw new TerminalAuthenticationException(
                OBMessageUtils.getI18NMessage("OBPOS_WrongTerminalKeyIdentifier", null));
          }
          if (terminal.isLinked() && !(terminal.getCurrentCacheSession().equals(cacheSessionId))) {
            throw new TerminalAuthenticationException(
                OBMessageUtils.getI18NMessage("OBPOS_TerminalAlreadyLinked", null));
          }

          Optional<User> user = PasswordHash.getUserWithPassword(username, password);
          if (!user.isPresent()) {
            throw new TerminalAuthenticationException(
                OBMessageUtils.getI18NMessage("OBPOS_InvalidUserPassword", null));
          }
          if (!checkTerminalAccess(user.get().getId(), terminal)) {
            throw new TerminalAuthenticationException(
                OBMessageUtils.getI18NMessage("OBPOS_USER_NO_ACCESS_TO_TERMINAL_TITLE", null));
          }
          terminal.setLinked(true);
          terminal.setCurrentCacheSession(cacheSessionId);
          responseJson.put("success", true);
          responseJson.put("terminalSearchKey", terminal.getSearchKey());
          response.getWriter().write(responseJson.toString());

          break;
        default: {
          throw new TerminalAuthenticationException("Unknown action");
        }
      }
    } catch (TerminalAuthenticationException e) {
      log.error("Error in PreLoginActions WebService", e);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error in PreLoginActions WebService", cause);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error executing query: " + cause.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * @param terminal
   * @throws TerminalAuthenticationException
   */
  private boolean checkTerminalAccess(String userId, OBPOSApplications terminal)
      throws TerminalAuthenticationException {
    // Terminal access will be checked to ensure that the user has access to the terminal
    OBQuery<TerminalAccess> accessCrit = OBDal.getInstance()
        .createQuery(TerminalAccess.class, "where userContact.id = :userId");
    accessCrit.setFilterOnReadableClients(false);
    accessCrit.setFilterOnReadableOrganization(false);
    accessCrit.setNamedParameter("userId", userId);
    List<TerminalAccess> accessList = accessCrit.list();
    boolean hasAccess = false;
    if (accessList.size() != 0) {
      for (TerminalAccess access : accessList) {
        if (access.getPOSTerminal().getSearchKey().equals(terminal.getSearchKey())) {
          hasAccess = true;
          break;
        }
      }
    }
    if (!hasAccess) {
      return false;
    }
    OBQuery<OBPOSApplications> appQry = OBDal.getInstance()
        .createQuery(OBPOSApplications.class,
            " as e where e.id = :terminalId and ((ad_isorgincluded("
                + "(select organization from ADUser where id= :userId)"
                + ", e.organization, e.client.id) <> -1) or " + "(ad_isorgincluded(e.organization, "
                + "(select organization from ADUser where id= :userId)"
                + ", e.client.id) <> -1)) ");
    appQry.setFilterOnReadableClients(false);
    appQry.setFilterOnReadableOrganization(false);
    appQry.setNamedParameter("terminalId", terminal.getId());
    appQry.setNamedParameter("userId", userId);
    List<OBPOSApplications> appList = appQry.list();
    if (appList.isEmpty()) {
      return false;
    }

    OBCriteria<User> userQ = OBDal.getInstance().createCriteria(User.class);
    userQ.add(Restrictions.eq(OBPOSApplications.PROPERTY_ID, userId));
    userQ.setFilterOnReadableOrganization(false);
    userQ.setFilterOnReadableClients(false);
    List<User> userList = userQ.list();
    if (userList.size() == 1) {
      User user = ((User) userList.get(0));

      boolean haveOrgAccess = false;
      for (UserRoles userRole : user.getADUserRolesList()) {
        if (!userRole.isActive() || !userRole.getRole().isActive()) {
          continue;
        }
        for (RoleOrganization roleOrg : userRole.getRole().getADRoleOrganizationList()) {
          if (roleOrg.isActive() && roleOrg.getOrganization().equals(terminal.getOrganization())) {
            haveOrgAccess = true;
            break;
          }
        }
      }
      if (!haveOrgAccess) {
        return false;
      }
      boolean success = false;
      for (UserRoles userRole : user.getADUserRolesList()) {
        if (this.hasADFormAccess(userRole)) {
          success = true;
          break;
        }
      }
      if (!success) {
        throw new TerminalAuthenticationException(
            OBMessageUtils.getI18NMessage("OBPOS_USERS_ROLE_NO_ACCESS_WEB_POS", null));
      }
      return true;
    } else {
      throw new TerminalAuthenticationException(
          OBMessageUtils.getI18NMessage("OBPOS_WrongTerminalKeyIdentifier", null));
    }

  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "DELETE method not supported. Use POST instead.");
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "PUT method not supported. Use POST instead.");
  }

  private boolean hasADFormAccess(UserRoles userRole) {
    for (FormAccess form : userRole.getRole().getADFormAccessList()) {
      if (form.getSpecialForm().getId().equals(OcrePosConstants.WEB_POS_FORM_ID)) {
        return true;
      }
    }
    return false;
  }
}
