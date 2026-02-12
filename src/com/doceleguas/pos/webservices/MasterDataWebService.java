package com.doceleguas.pos.webservices;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery.MasterDataModel;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

public class MasterDataWebService implements WebService {
  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    final String modelName = request.getParameter("model");
    try {
      // \"_offset\":1,
      String jsonString = "{\"csrfToken\":\"126D2537BF02493EAB64127F306DDE1D\",\"appName\":\"POS\","
          + "\"timeout\":100000," + "\"parameters\":{\"terminalTime\":\"2025-11-21T04:37:33.413Z\","
          + "\"terminalTimeOffset\":{\"value\":240}},"
          + "\"incremental\":false,\"_isMasterdata\":true,\"lastId\":null,\"clientQueryIndex\":-1}";
      JSONObject jsonsent = new JSONObject(jsonString);
      jsonsent.put("client", request.getParameter("client"));
      jsonsent.put("organization", request.getParameter("organization"));
      jsonsent.put("pos", request.getParameter("pos"));
      jsonsent.put("termnalName", request.getParameter("terminalName"));
      requestParamsToJson(jsonsent, request);

      //User currentUser = OBDal.getInstance().get(User.class, jsonsent.getString("user"));
      //Role defaultPosRole = currentUser.getOBPOSDefaultPOSRole();
      
      OBContext.setOBContext(OBContext.getOBContext().getUser().getId(),
    		  OBContext.getOBContext().getUser().getOBPOSDefaultPOSRole().getId(),
          jsonsent.optString("client", OBContext.getOBContext().getCurrentClient().getId()),
          jsonsent.optString("organization",
              OBContext.getOBContext().getCurrentOrganization().getId()));
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JSONObject responseJSON = new JSONObject();
      responseJSON.put("model", modelName);
      // response.getWriter().write("{\"model\":\"" + modelName + "\",");

      if (jsonsent.has("v2")) {
        Model model = getModelInstanceNew(modelName);
        JSONObject parameters = new JSONObject();
        requestParamsToJson(parameters, request);
        String regex = "(?i)\\b(select|update|delete|drop)\\b";
        String selectList = parameters.getString("selectList")
            .replaceAll(regex, "")
            .trim()
            .replaceAll(" +", " ");
        parameters.put("selectList", selectList);
        NativeQuery<?> query = model.createQuery(parameters);
        String lastUpdated = jsonsent.optString("lastUpdated", null);
        if (lastUpdated != null) {
          query.setParameter("lastUpdated", Instant.ofEpochMilli(Long.parseLong(lastUpdated)));
        }
        query.scroll(ScrollMode.FORWARD_ONLY);
        ScrollableResults scroll = query.scroll(ScrollMode.FORWARD_ONLY);
        int i = 0;
        JSONArray dataArray = new JSONArray();
        try {
          while (scroll.next()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rowMap = (Map<String, Object>) scroll.get()[0];
            JSONObject res = model.rowToJson(rowMap);
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
        responseJSON.put("data", dataArray);
        response.getWriter().write(responseJSON.toString());
      } else {
        MasterDataProcessHQLQuery modelInstance = getModelInstance(modelName);
        response.getWriter().write("{\"model\":\"" + modelName + "\",");
        modelInstance.exec(response.getWriter(), jsonsent);
        response.getWriter().write("}");
      }
    } catch (Exception e) { 
        Throwable cause = DbUtility.getUnderlyingSQLException(e);
        log.error("Error Loading Masterdata", e); 

        JSONObject errorResponse = new JSONObject();
        try {
            String message = (cause != null && cause.getMessage() != null) 
                             ? cause.getMessage() 
                             : "Internal Server Error";
            
            errorResponse.put("exception", message);
                        
        } catch (JSONException je) {
            log.error("Error creating JSON error response", je);
        }

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

  private Model getModelInstanceNew(String modelName) throws Exception {
    try {
      // return (Model) CDI.current().select(new ModelAnnotation.Literal(modelName)).get();
      List<Model> allModels = WeldUtils.getInstances(Model.class);
      return allModels.stream()
          .filter(model -> model.getName().equals(modelName))
          .findFirst()
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
