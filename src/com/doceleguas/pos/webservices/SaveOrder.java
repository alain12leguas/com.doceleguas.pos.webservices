/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.importprocess.ImportEntry;
import org.openbravo.service.importprocess.ImportEntryAlreadyExistsException;
import org.openbravo.service.importprocess.ImportEntryBuilder;
import org.openbravo.service.web.WebService;

import com.doceleguas.pos.webservices.orders.loader.OCOrderImportConstants;

/**
 * Accepts SaveOrder native OrderLoader payload (v2) and enqueues it for asynchronous
 * processing via {@link com.doceleguas.pos.webservices.orders.loader.OCOrderImportEntryProcessor}.
 */
public class SaveOrder implements WebService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    JSONObject err = new JSONObject();
    err.put("error", true);
    err.put("message", "Use POST with SaveOrder native payload body.");
    PrintWriter out = response.getWriter();
    out.print(err.toString());
    out.flush();
  }

  @Override
  public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    StringBuilder sb = new StringBuilder();
    String line;
    try (BufferedReader reader = request.getReader()) {
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }

    String requestId = UUID.randomUUID().toString().replace("-", "");
    long receivedAt = System.currentTimeMillis();

    try {
      JSONObject requestBody = new JSONObject(sb.toString());
      validateRequest(requestBody);
      String messageId = requestBody.getString("messageId");
      JSONObject queuePayload = normalizeForQueue(requestBody);
      String jsonString = queuePayload.toString();

      try {
        ImportEntryBuilder.newInstance(OCOrderImportConstants.TYPE_OF_DATA, jsonString)
            .setId(messageId)
            .setNotifyManager(true)
            .create();
      } catch (ImportEntryAlreadyExistsException dup) {
        log.debug("Duplicate order import request for messageId {}", messageId);
        writeAccepted(response, requestId, messageId, receivedAt, true);
        return;
      }

      writeAccepted(response, requestId, messageId, receivedAt, false);

    } catch (JSONException e) {
      log.warn("SaveOrder invalid JSON: {}", e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      JSONObject err = new JSONObject();
      err.put("error", true);
      err.put("message", "Invalid JSON: " + e.getMessage());
      err.put("requestId", requestId);
      PrintWriter out = response.getWriter();
      out.print(err.toString());
      out.flush();
    } catch (IllegalArgumentException e) {
      log.warn("SaveOrder validation: {}", e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      JSONObject err = new JSONObject();
      err.put("error", true);
      err.put("message", e.getMessage());
      err.put("requestId", requestId);
      PrintWriter out = response.getWriter();
      out.print(err.toString());
      out.flush();
    }
  }

  private void validateRequest(JSONObject body) throws JSONException {
    if (!body.has("messageId") || body.isNull("messageId")
        || body.getString("messageId").trim().isEmpty()) {
      throw new IllegalArgumentException("Missing or empty required field: messageId");
    }
    if (!body.has("order")) {
      throw new IllegalArgumentException("Missing required field: order");
    }
    Object order = body.get("order");
    if (!(order instanceof JSONObject)) {
      throw new IllegalArgumentException("Field order must be a JSON object");
    }
  }

  /**
   * Queue payload normalized to internal native shape expected by OCWS_Order pipeline.
   */
  private JSONObject normalizeForQueue(JSONObject requestBody) throws JSONException {
    JSONObject normalized = new JSONObject();
    normalized.put("messageId", requestBody.getString("messageId"));
    String posTerminal = requestBody.optString("posTerminal", null);
    normalized.put("posTerminal", posTerminal);
    normalized.put("appName", "OCRE");
    normalized.put("channel", "Native");

    JSONArray data = new JSONArray();
    JSONObject order = new JSONObject(requestBody.getJSONObject("order").toString());
    if (!order.has("posTerminal") || order.isNull("posTerminal")
        || order.optString("posTerminal", "").trim().isEmpty()) {
      order.put("posTerminal", posTerminal);
    }
    data.put(order);
    normalized.put("data", data);
    if (!requestBody.isNull("metadata")) {
      normalized.put("metadata", requestBody.get("metadata"));
    }
    return normalized;
  }

  private void writeAccepted(HttpServletResponse response, String requestId, String importEntryId,
      long receivedAt, boolean duplicate) throws Exception {
    response.setStatus(HttpServletResponse.SC_ACCEPTED);
    JSONObject ok = new JSONObject();
    ok.put("status", "accepted");
    ok.put("requestId", requestId);
    ok.put("importEntryId", importEntryId);
    ok.put("receivedAt", receivedAt);
    ok.put("duplicate", duplicate);
    if (duplicate) {
      ImportEntry existing = OBDal.getInstance().get(ImportEntry.class, importEntryId);
      if (existing != null) {
        ok.put("importStatus", existing.getImportStatus());
      }
    }
    PrintWriter out = response.getWriter();
    out.print(ok.toString());
    out.flush();
  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    doGet(path, request, response);
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    doGet(path, request, response);
  }
}
