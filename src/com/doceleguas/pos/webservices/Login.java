package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.mobile.core.login.MobileCoreLoginHandler;
import org.openbravo.model.ad.access.Role;

public class Login extends MobileCoreLoginHandler {
  private static final long serialVersionUID = 1L;

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    try {
      ResponseBufferWrapper wrappedRes = new ResponseBufferWrapper(res);
      super.doPost(req, wrappedRes);
      JSONObject jsonResponse = new JSONObject(wrappedRes.getCapturedContent());
      Role defaultRole = OBContext.getOBContext().getUser().getOBPOSDefaultPOSRole();
      if (defaultRole == null) {
        throw new RoleDoNotExistException();
      }
      JSONObject role = new JSONObject();
      role.put("id", defaultRole.getId());
      role.put("name", defaultRole.getName());
      jsonResponse.put("role", role);
      PrintWriter writer = res.getWriter();
      writer.print(jsonResponse.toString());
      writer.flush();
    } catch (RoleDoNotExistException e) {
      OBError error = new OBError();
      error.setTitle(null);
      error.setMessage("The user doesn't have a defined role.");
      responseWithError(res, error);
    } catch (Exception e) {
      OBError error = new OBError();
      error.setTitle(OBMessageUtils.getI18NMessage("IDENTIFICATION_FAILURE_TITLE", null));
      error.setMessage(OBMessageUtils.getI18NMessage("IDENTIFICATION_FAILURE_MSG", null));
      responseWithError(res, error);
    }

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
}
