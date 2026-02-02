package com.doceleguas.pos.webservices;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.web.WebService;

/**
 * Custom WebService to retrieve Orders from the Openbravo API ExportService.
 * 
 * This endpoint acts as a proxy to the org.openbravo.api.ExportService/Order endpoint, providing a
 * simplified interface for querying orders with different filter types.
 * 
 * The authentication is handled by forwarding the Authorization header from the incoming request
 * to the ExportService. This ensures that the same credentials used to call this endpoint are
 * used to authenticate with the underlying ExportService.
 */
public class GetOrders implements WebService {

  private static final Logger log = LogManager.getLogger();

  private static final String EXPORT_SERVICE_PATH = "/ws/org.openbravo.api.ExportService/Order";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final int CONNECTION_TIMEOUT = 30000;
  private static final int READ_TIMEOUT = 60000;

  // Filter type constants
  private static final String FILTER_BY_ID = "byId";
  private static final String FILTER_BY_DOCUMENT_NO = "byDocumentNo";
  private static final String FILTER_BY_ORG_ORDER_DATE = "byOrgOrderDate";
  private static final String FILTER_BY_ORG_ORDER_DATE_RANGE = "byOrgOrderDateRange";

  @Override
  public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      // Get and validate the Authorization header
      String authHeader = request.getHeader(AUTHORIZATION_HEADER);
      if (authHeader == null || authHeader.isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
            "Missing Authorization header. Use Basic Auth.");
        return;
      }

      String filterType = request.getParameter("filter");

      if (filterType == null || filterType.isEmpty()) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required parameter: 'filter'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange");
        return;
      }

      String exportServiceRelativePath = buildExportServicePath(request, filterType);

      if (exportServiceRelativePath == null) {
        return;
      }

      // Build full URL for the ExportService
      String fullUrl = buildFullUrl(request, exportServiceRelativePath);
      
      log.debug("Calling ExportService: {}", fullUrl);

      // Call the ExportService and stream the response
      callExportService(fullUrl, authHeader, response);

    } catch (MissingParameterException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      log.error("Error in GetOrders WebService", e);
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error: " + e.getMessage());
    }
  }

  /**
   * Builds the full URL for the ExportService based on the current request.
   */
  private String buildFullUrl(HttpServletRequest request, String relativePath) {
    StringBuilder urlBuilder = new StringBuilder();
    urlBuilder.append(request.getScheme())
        .append("://")
        .append(request.getServerName());
    
    int port = request.getServerPort();
    if ((request.getScheme().equals("http") && port != 80) ||
        (request.getScheme().equals("https") && port != 443)) {
      urlBuilder.append(":").append(port);
    }
    
    urlBuilder.append(request.getContextPath())
        .append(relativePath);
    
    return urlBuilder.toString();
  }

  /**
   * Calls the ExportService using HttpURLConnection and streams the response back.
   * The Authorization header from the original request is forwarded to maintain authentication.
   */
  private void callExportService(String url, String authHeader, HttpServletResponse response)
      throws IOException {
    
    HttpURLConnection connection = null;
    
    try {
      URL exportUrl = new URL(url);
      connection = (HttpURLConnection) exportUrl.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty(AUTHORIZATION_HEADER, authHeader);
      connection.setRequestProperty("Accept", "application/json");
      connection.setConnectTimeout(CONNECTION_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);
      connection.setDoInput(true);

      int responseCode = connection.getResponseCode();
      response.setStatus(responseCode);

      // Read the response (either from input stream or error stream)
      String responseBody;
      if (responseCode >= 200 && responseCode < 300) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          responseBody = reader.lines().collect(Collectors.joining("\n"));
        }
      } else {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
          responseBody = reader.lines().collect(Collectors.joining("\n"));
        }
      }

      // Copy relevant headers from ExportService response
      String contentType = connection.getContentType();
      if (contentType != null) {
        response.setContentType(contentType);
      }

      // Write the response body
      response.getWriter().write(responseBody);

    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Builds the Export Service path based on the filter type and request parameters.
   * 
   * @param request
   *          The HTTP request
   * @param filterType
   *          The type of filter to apply
   * @return The relative path to call the Export Service
   * @throws MissingParameterException
   *           if required parameters are missing
   */
  private String buildExportServicePath(HttpServletRequest request, String filterType)
      throws MissingParameterException, UnsupportedEncodingException {

    StringBuilder pathBuilder = new StringBuilder(EXPORT_SERVICE_PATH);

    switch (filterType) {
      case FILTER_BY_ID:
        return buildByIdPath(request, pathBuilder);

      case FILTER_BY_DOCUMENT_NO:
        return buildByDocumentNoPath(request, pathBuilder);

      case FILTER_BY_ORG_ORDER_DATE:
        return buildByOrgOrderDatePath(request, pathBuilder);

      case FILTER_BY_ORG_ORDER_DATE_RANGE:
        return buildByOrgOrderDateRangePath(request, pathBuilder);

      default:
        throw new MissingParameterException("Invalid filter type: '" + filterType
            + "'. Valid values: byId, byDocumentNo, byOrgOrderDate, byOrgOrderDateRange");
    }
  }

  /**
   * Builds path for filtering by Order ID. Example:
   * /ws/org.openbravo.api.ExportService/Order/068DCCBCB90F80C459DD7BA46E32C16B
   */
  private String buildByIdPath(HttpServletRequest request, StringBuilder pathBuilder)
      throws MissingParameterException {
    String id = getRequiredParameter(request, "id");
    pathBuilder.append("/").append(id);
    return pathBuilder.toString();
  }

  /**
   * Builds path for filtering by Document Number. Example:
   * /ws/org.openbravo.api.ExportService/Order/byDocumentNo?documentNo=VBS1/0000080
   */
  private String buildByDocumentNoPath(HttpServletRequest request, StringBuilder pathBuilder)
      throws MissingParameterException, UnsupportedEncodingException {
    String documentNo = getRequiredParameter(request, "documentNo");
    pathBuilder.append("/byDocumentNo?documentNo=")
        .append(URLEncoder.encode(documentNo, StandardCharsets.UTF_8.toString()));
    return pathBuilder.toString();
  }

  /**
   * Builds path for filtering by Organization and Order Date. Example:
   * /ws/org.openbravo.api.ExportService/Order/byOrgOrderDate?organization=Store&orderDate=2025-01-15
   */
  private String buildByOrgOrderDatePath(HttpServletRequest request, StringBuilder pathBuilder)
      throws MissingParameterException, UnsupportedEncodingException {
    String organization = getRequiredParameter(request, "organization");
    String orderDate = getRequiredParameter(request, "orderDate");

    pathBuilder.append("/byOrgOrderDate?organization=")
        .append(URLEncoder.encode(organization, StandardCharsets.UTF_8.toString()))
        .append("&orderDate=")
        .append(URLEncoder.encode(orderDate, StandardCharsets.UTF_8.toString()));
    return pathBuilder.toString();
  }

  /**
   * Builds path for filtering by Organization and Date Range. Example:
   * /ws/org.openbravo.api.ExportService/Order/byOrgOrderDateRange?organization=Store&dateFrom=2025-01-01&dateTo=2025-01-31
   */
  private String buildByOrgOrderDateRangePath(HttpServletRequest request, StringBuilder pathBuilder)
      throws MissingParameterException, UnsupportedEncodingException {
    String organization = getRequiredParameter(request, "organization");
    String dateFrom = getRequiredParameter(request, "dateFrom");
    String dateTo = getRequiredParameter(request, "dateTo");

    pathBuilder.append("/byOrgOrderDateRange?organization=")
        .append(URLEncoder.encode(organization, StandardCharsets.UTF_8.toString()))
        .append("&dateFrom=")
        .append(URLEncoder.encode(dateFrom, StandardCharsets.UTF_8.toString()))
        .append("&dateTo=")
        .append(URLEncoder.encode(dateTo, StandardCharsets.UTF_8.toString()));
    return pathBuilder.toString();
  }

  /**
   * Gets a required parameter from the request, throwing an exception if missing.
   */
  private String getRequiredParameter(HttpServletRequest request, String paramName)
      throws MissingParameterException {
    String value = request.getParameter(paramName);
    if (value == null || value.trim().isEmpty()) {
      throw new MissingParameterException("Missing required parameter: '" + paramName + "'");
    }
    return value.trim();
  }

  /**
   * Sends an error response as JSON.
   */
  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setStatus(statusCode);
    try {
      JSONObject errorJson = new JSONObject();
      errorJson.put("error", true);
      errorJson.put("message", message);
      errorJson.put("statusCode", statusCode);
      response.getWriter().write(errorJson.toString());
    } catch (JSONException e) {
      response.getWriter().write("{\"error\":true,\"message\":\"" + message + "\"}");
    }
  }

  /**
   * Custom exception for missing required parameters.
   */
  private static class MissingParameterException extends Exception {
    private static final long serialVersionUID = 1L;

    public MissingParameterException(String message) {
      super(message);
    }
  }

  /**
   * Utility method to convert request parameters to JSON (for potential future use).
   */
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
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "POST method not supported. Use GET instead.");
  }

  @Override
  public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "DELETE method not supported. Use GET instead.");
  }

  @Override
  public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "PUT method not supported. Use GET instead.");
  }
}
