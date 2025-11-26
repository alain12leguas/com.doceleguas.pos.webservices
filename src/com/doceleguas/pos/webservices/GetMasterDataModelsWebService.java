package com.doceleguas.pos.webservices;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery;
import org.openbravo.mobile.core.process.WebServiceServletUtils;
import org.openbravo.service.web.WebService;

public class GetMasterDataModelsWebService implements WebService {

  // @Inject
  // @Any
  // private Instance<MasterDataProcessHQLQuery> masterDataQueries;

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    try {
      // List<String> modelNames = masterDataQueries.stream()
      // .map(MasterDataProcessHQLQuery::getName)
      // .filter(java.util.Optional::isPresent)
      // .map(java.util.Optional::get)
      // .collect(Collectors.toList());
      //
      // Instance<MasterDataProcessHQLQuery> allMasterDataQueries = CDI.current()
      // .select(MasterDataProcessHQLQuery.class);
      List<String> modelNames = WeldUtils.getInstances(MasterDataProcessHQLQuery.class)
          .stream()
          .map(MasterDataProcessHQLQuery::getName)
          .filter(java.util.Optional::isPresent)
          .map(java.util.Optional::get)
          .collect(Collectors.toList());
      String jsonArray = modelNames.stream()
          .map(name -> "\"" + name + "\"")
          .collect(Collectors.joining(","));

      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      PrintWriter writer = response.getWriter();

      writer.write("{\"models\":[");

      writer.write(jsonArray);

      writer.write("]}");

      writer.flush();
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

  protected String getRequestContent(HttpServletRequest request) throws IOException {
    return WebServiceServletUtils.getRequestContent(request);
  }

}
