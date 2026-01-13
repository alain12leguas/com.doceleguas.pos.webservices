package com.doceleguas.pos.webservices;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.db.DbUtility;
import org.openbravo.service.web.WebService;

public class SaveBusinessPartner implements WebService {
  private static final Logger log = LogManager.getLogger();

  @SuppressWarnings("unchecked")
  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    DalConnectionProvider cp = new DalConnectionProvider(false);
    Connection conn = OBDal.getInstance().getConnection();
    conn.setAutoCommit(false);
    StringBuilder sb = new StringBuilder();
    String line;
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      BufferedReader reader = request.getReader();
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      final JSONObject bparnterJson = new JSONObject(sb.toString());
      // updateCustomerHeader(conn, bparnterJson);
      updateContactInfo(conn, bparnterJson);
      updateLocations(conn, bparnterJson);
      conn.commit();
      JSONObject successResponse = new JSONObject();
      successResponse.put("status", "success");
      PrintWriter out = response.getWriter();
      out.print(successResponse.toString());
      out.flush();
    } catch (Throwable t) {
      if (conn != null) {
        conn.rollback();
      }
      Throwable cause = DbUtility.getUnderlyingSQLException(t);
      log.error("Error Saving BusinessPartner", cause);
      JSONObject errorResponse = new JSONObject();
      errorResponse.put("error", cause.getMessage());
      PrintWriter out = response.getWriter();
      out.print(errorResponse.toString());
      out.flush();
    } finally {
      conn.setAutoCommit(true);
    }
  }

  private int updateCustomerHeader(Connection conn, JSONObject customer)
      throws SQLException, JSONException {
    String sql = "UPDATE C_BPARTNER SET NAME = ? WHERE C_BPARTNER_ID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, customer.getString("name"));
      ps.setString(2, customer.getString("id"));
      return ps.executeUpdate();
    }
  }

  private int updateContactInfo(Connection conn, JSONObject customer)
      throws JSONException, SQLException {
    JSONObject contactJson = customer.getJSONObject("contact");
    String sql = "UPDATE AD_USER SET firstname = ?, lastname = ?, phone = ?, email = ? WHERE AD_USER_ID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, contactJson.getString("firstName"));
      ps.setString(2, contactJson.getString("lastName"));
      ps.setString(3, contactJson.getString("phone"));
      ps.setString(4, contactJson.getString("email"));
      ps.setString(5, contactJson.getString("id"));
      return ps.executeUpdate();
    }
  }

  private void updateLocations(Connection conn, JSONObject customer)
      throws JSONException, SQLException {
    JSONArray locations = customer.getJSONArray("locations");
    String sqlLocation = "UPDATE c_location SET address1 = ?, address2 = ?, c_country_id = ?, c_region_id = ?, postal = ?, city = ? WHERE c_location_id = ?";
    String sqlBpLocation = "UPDATE c_bpartner_location SET isshipto = ?, isbillto = ?, name = ? WHERE c_bpartner_location_id = ?";
    for (int i = 0; i < locations.length(); i++) {
      JSONObject bpLocation = locations.getJSONObject(i);
      boolean isNew = bpLocation.optBoolean("isNew", false);
      if (isNew) {
      //@formatter:off
        sqlLocation = "INSERT INTO c_location ("
            + "    ad_client_id,"
            + "    ad_org_id,"
            + "    address1,"
            + "    address2,"
            + "    c_country_id,"
            + "    c_region_id,"
            + "    postal,"
            + "    city,"
            + "    c_location_id, createdby, updatedby, created, updated"
            + " ) SELECT ad_client_id, ad_org_id, ?, ?, ?, ?, ?, ?, ?, '100','100', NOW(), NOW() FROM c_bpartner WHERE c_bpartner_id = ?;";
                
        sqlBpLocation = "INSERT INTO c_bpartner_location ("
            + "    ad_client_id,"
            + "    ad_org_id,"
            + "    isshipto,"
            + "    isbillto,"
            + "    name,"
            + "    c_bpartner_location_id,"
            + "    c_location_id, createdby, updatedby, created, updated,"
            + "    c_bpartner_id"
            + " )"
            + " SELECT ad_client_id, ad_org_id, ?, ?, ?, ?, ?, '100','100', NOW(), NOW(), c_bpartner_id FROM c_bpartner WHERE c_bpartner_id = ?;";
      //@formatter:on
        try (PreparedStatement psLocation = conn.prepareStatement(sqlLocation)) {
          psLocation.setString(1, bpLocation.getString("adressLine1"));
          psLocation.setString(2, bpLocation.getString("adressLine2"));
          psLocation.setString(3, bpLocation.getString("countryId"));
          psLocation.setString(4, bpLocation.optString("regionId", null));
          psLocation.setString(5, bpLocation.getString("postalCode"));
          psLocation.setString(6, bpLocation.getString("cityName"));
          psLocation.setString(7, bpLocation.getString("locationId"));
          psLocation.setString(8, customer.getString("id"));
          int r = psLocation.executeUpdate();
          int a = r;
        }
        try (PreparedStatement psBpLocation = conn.prepareStatement(sqlBpLocation)) {
          psBpLocation.setString(1, bpLocation.optBoolean("isShipTo") ? "Y" : "N");
          psBpLocation.setString(2, bpLocation.optBoolean("isBillTo") ? "Y" : "N");
          psBpLocation.setString(3, bpLocation.getString("name"));
          psBpLocation.setString(4, bpLocation.getString("id"));
          psBpLocation.setString(5, bpLocation.getString("locationId"));
          psBpLocation.setString(6, customer.getString("id"));
          psBpLocation.executeUpdate();
        }
      } else {
        try (PreparedStatement psLocation = conn.prepareStatement(sqlLocation)) {
          psLocation.setString(1, bpLocation.getString("adressLine1"));
          psLocation.setString(2, bpLocation.getString("adressLine2"));
          psLocation.setString(3, bpLocation.optString("countryId"));
          psLocation.setString(4, bpLocation.optString("regionId"));
          psLocation.setString(5, bpLocation.getString("postalCode"));
          psLocation.setString(6, bpLocation.getString("cityName"));
          psLocation.setString(7, bpLocation.getString("locationId"));
        }
        try (PreparedStatement psBpLocation = conn.prepareStatement(sqlBpLocation)) {
          psBpLocation.setString(1, bpLocation.optBoolean("isShipTo") ? "Y" : "N");
          psBpLocation.setString(2, bpLocation.optBoolean("isBillTo") ? "Y" : "N");
          psBpLocation.setString(3, bpLocation.getString("name"));
          psBpLocation.setString(4, bpLocation.getString("id"));
          psBpLocation.executeUpdate();
        }
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
