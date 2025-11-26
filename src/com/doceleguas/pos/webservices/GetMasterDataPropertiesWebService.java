package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery.MasterDataModel;
import org.openbravo.mobile.core.process.WebServiceServletUtils;
import org.openbravo.service.web.WebService;

public class GetMasterDataPropertiesWebService implements WebService {

  @Inject
  private Instance<MasterDataProcessHQLQuery> masterDataQueries;

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    try {
      // \"_offset\":1,
      String jsonString = "{\"csrfToken\":\"126D2537BF02493EAB64127F306DDE1D\",\"appName\":\"POS2\","
          + "\"_limit\":1,"
          + "\"client\":\"39363B0921BB4293B48383844325E84C\",\"organization\":\"D270A5AC50874F8BA67A88EE977F8E3B\","
          + "\"pos\":\"1C9CB2318D17467BA0A76DB6CF309213\",\"terminalName\":\"VBS-2\",\"timeout\":100000,"
          + "\"parameters\":{\"terminalTime\":\"2025-11-21T04:37:33.413Z\","
          + "\"terminalTimeOffset\":{\"value\":240}},"
          + "\"incremental\":false,\"_isMasterdata\":true,\"lastId\":null,\"clientQueryIndex\":-1}";
      JSONObject jsonsent = new JSONObject(jsonString);

      // final String modelName = request.getParameter("model");
      requestParamsToJson(jsonsent, request);
      OBContext.setOBContext(OBContext.getOBContext().getUser().getId(),
          OBContext.getOBContext().getRole().getId(),
          jsonsent.optString("client", OBContext.getOBContext().getCurrentClient().getId()),
          jsonsent.optString("organization",
              OBContext.getOBContext().getCurrentOrganization().getId()));
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{");
      int i = 0;
      for (String modelName : getAllMasterDataModels()) {
        jsonsent.put("model", modelName);
        StringWriter w = new StringWriter();
        MasterDataProcessHQLQuery modelInstance = getModelInstance(modelName);
        // Workaround using Reflection to configure process timeout
        Method setTimeout = MasterDataProcessHQLQuery.class.getDeclaredMethod("setTimeout",
            Long.class);
        setTimeout.setAccessible(true);
        setTimeout.invoke(modelInstance, jsonsent.optLong("timeout", 10000));
        modelInstance.exec(w, jsonsent);
        String data = w.toString();
        response.getWriter().write(jsonString);
        if (i++ > 2) {
          break;
        }
      }
      response.getWriter().write("}");
    } catch (Exception e) {
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("error", e.getMessage());
      PrintWriter out = response.getWriter();
      out.print(errorResponse.toString());
      out.flush();
    }
  }

  public List<String> getAllMasterDataModels() {

    List<String> modelNames = masterDataQueries.stream()
        .map(MasterDataProcessHQLQuery::getName)
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .collect(Collectors.toList());

    return modelNames;
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

  protected String getRequestContent(HttpServletRequest request) throws IOException {
    return WebServiceServletUtils.getRequestContent(request);
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
