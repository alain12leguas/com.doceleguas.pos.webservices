package com.doceleguas.pos.webservices.hooks;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.retail.posterminal.OrderLoaderPreProcessHook;

@ApplicationScoped
public class PreOrderLoaderHook implements OrderLoaderPreProcessHook {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void exec(JSONObject jsonOrder) {
    try {
      if (jsonOrder.optBoolean("ocreIssueInvoice", false)) {
        JSONObject calculatedInvoice = cloneJSON(jsonOrder);
        calculatedInvoice.put("documentNo", jsonOrder.getString("invoiceDocumentNo"));
        jsonOrder.put("calculatedInvoice", calculatedInvoice);
      }
    } catch (Exception e) {
      log.debug("Error cloning order: " + e.getMessage());
    }
  }

  public JSONObject cloneJSON(JSONObject original) {
    if (original == null) {
      return null;
    }
    try {
      // Generates a deep copy by parsing the string representation
      return new JSONObject(original.toString());
    } catch (JSONException e) {
      // This should not happen with a valid original JSONObject
      return new JSONObject();
    }
  }
}
