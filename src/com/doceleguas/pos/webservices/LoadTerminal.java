package com.doceleguas.pos.webservices;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.retail.posterminal.term.Terminal;
import org.openbravo.service.web.WebService;

public class LoadTerminal implements WebService {

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    try {
      Terminal terminal = WeldUtils.getInstances(Terminal.class).get(0);
      // request.getParameter("terminal")
      JSONObject jsonsent = new JSONObject();
      JSONObject parameters = new JSONObject();
      parameters.put("terminalTime", Instant.now().toString());
      parameters.put("terminalTimeOffset", new JSONObject("{\"value\":0}"));
      jsonsent.put("parameters", parameters);
      // jsonsent.put("client", request.getParameter("client"));
      // jsonsent.put("organization", request.getParameter("organization"));
      // jsonsent.put("termnalName", request.getParameter("terminalName"));

      requestParamsToJson(jsonsent, request);
      // jsonsent.put("parameters", new JSONObject());
      OBContext.setOBContext(OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonsent.optString("client", OBContext.getOBContext().getCurrentClient().getId()),
          jsonsent.optString("organization",
              OBContext.getOBContext().getCurrentOrganization().getId()));
      JSONObject terminalJson = terminal.exec(jsonsent);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().print(terminalJson.toString());
    } catch (Exception e) {
      JSONObject errorResponse = new JSONObject();
      try {
        errorResponse.put("messageType", "error");
        errorResponse.put("messageText", e.getMessage());
        PrintWriter out = response.getWriter();
        out.print(errorResponse.toString());
        out.flush();
      } catch (Exception e1) {
        e1.printStackTrace();
      }
    }
  }

  public JSONObject requestParamsToJson(JSONObject jsonParams, HttpServletRequest request)
      throws JSONException {
    Map<String, String[]> params = request.getParameterMap();

    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      String key = entry.getKey();
      String[] values = entry.getValue();

      if (values.length == 1) {
        jsonParams.put(key, values[0]);
      } else if (values.length > 1) {
        JSONArray jsonArray = new JSONArray();
        for (String value : values) {
          jsonArray.put(value);
        }
        jsonParams.put(key, jsonArray);
      }
    }

    return jsonParams;
  }

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    // TODO Auto-generated method stub

  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    // TODO Auto-generated method stub

  }

}
