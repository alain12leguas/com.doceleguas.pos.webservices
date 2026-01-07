package com.doceleguas.pos.webservices;

import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;

public abstract class Model {
  public abstract NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException;

  // public abstract JSONArray exec(JSONObject jsonsent) throws JSONException;

  public abstract String getName();

  public JSONObject rowToJson(Map<String, Object> rowMap) throws JSONException {
    return new JSONObject(rowMap);
  }
}
