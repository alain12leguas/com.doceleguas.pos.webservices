package com.doceleguas.pos.webservices;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

public class SaveBusinessPartner implements WebService {
  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings("unchecked")
  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    StringBuilder sb = new StringBuilder();
    String line;
    try {
      BufferedReader reader = request.getReader();
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      final JSONObject bparnterJson = new JSONObject(sb.toString());
      String pk = "c_bpartner_id";
      StringBuilder setClause = new StringBuilder();
      List<String> columns = new ArrayList<>();
      Iterator<String> keys = bparnterJson.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (!key.equals(pk)) {
          columns.add(key);
          setClause.append(key).append(" = :").append(key);
          if (keys.hasNext()) {
            setClause.append(", ");
          }
        }
      }
      String finalSet = setClause.toString().trim();
      if (finalSet.endsWith(",")) {
        finalSet = finalSet.substring(0, finalSet.length() - 1);
      }
      String sql = "UPDATE c_bpartner SET " + finalSet + " WHERE c_bpartner_id = :idParam";

      NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);

      for (String col : columns) {
        Object value = bparnterJson.get(col);
        query.setParameter(col, value == JSONObject.NULL ? null : value);
      }
      query.setParameter("idParam", bparnterJson.get(pk));

      int updatedRows = query.executeUpdate();
      JSONObject successResponse = new JSONObject();
      successResponse.put("status", "success");
      PrintWriter out = response.getWriter();
      out.print(successResponse.toString());
      out.flush();
    } catch (Throwable t) {
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error Saving BusinessPartner", cause);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("error", cause.getMessage());
      PrintWriter out = response.getWriter();
      out.print(errorResponse.toString());
      out.flush();
    }
  }

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
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
