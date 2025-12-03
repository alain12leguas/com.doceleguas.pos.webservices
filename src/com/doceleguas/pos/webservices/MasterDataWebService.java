package com.doceleguas.pos.webservices;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery.MasterDataModel;
import org.openbravo.service.web.WebService;

public class MasterDataWebService implements WebService {

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    try {
      // \"_offset\":1,
      String jsonString = "{\"csrfToken\":\"126D2537BF02493EAB64127F306DDE1D\",\"appName\":\"POS\","
          + "\"timeout\":100000,"
          + "\"parameters\":{\"terminalTime\":\"2025-11-21T04:37:33.413Z\","
          + "\"terminalTimeOffset\":{\"value\":240}},"
          + "\"incremental\":false,\"_isMasterdata\":true,\"lastId\":null,\"clientQueryIndex\":-1}";
      JSONObject jsonsent = new JSONObject(jsonString);
      final String modelName = request.getParameter("model");
      jsonsent.put("client", request.getParameter("client"));
      jsonsent.put("organization", request.getParameter("organization"));
      jsonsent.put("pos", request.getParameter("pos"));
      jsonsent.put("termnalName", request.getParameter("terminalName"));
      
      requestParamsToJson(jsonsent, request);
      OBContext.setOBContext(OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonsent.optString("client", OBContext.getOBContext().getCurrentClient().getId()),
          jsonsent.optString("organization",
              OBContext.getOBContext().getCurrentOrganization().getId()));

      MasterDataProcessHQLQuery modelInstance = getModelInstance(modelName);
      // Workaround using Reflection to configure process timeout
      Method setTimeout = MasterDataProcessHQLQuery.class.getDeclaredMethod("setTimeout",
          Long.class);
      setTimeout.setAccessible(true);
      setTimeout.invoke(modelInstance, jsonsent.optLong("timeout", 10000));
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"model\":\"" + modelName + "\",");
      modelInstance.exec(response.getWriter(), jsonsent);
      response.getWriter().write("}");
    } catch (Exception e) {
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("error", e.getMessage());
      PrintWriter out = response.getWriter();
      out.print(errorResponse.toString());
      out.flush();
    }
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

  private MasterDataProcessHQLQuery getModelInstance(String modelName) throws Exception {
    try {
      return (MasterDataProcessHQLQuery) CDI.current()
          .select(new MasterDataModel.Literal(modelName))
          .get();
    } catch (Exception e) {
      throw new MasterDataLoaderError("Error loading model " + modelName, e);
    }
  }

  class MasterDataLoaderError extends Exception {
    private static final long serialVersionUID = 1L;

    public MasterDataLoaderError(String errorMessage, Throwable err) {
      super(errorMessage, err);
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
}
