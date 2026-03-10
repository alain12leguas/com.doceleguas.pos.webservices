/*
 ************************************************************************************
 * Copyright (C) 2026 Doceleguas
 * Licensed under the Openbravo Public License version 1.0
 ************************************************************************************
 */
package com.doceleguas.pos.webservices;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

/**
 * Model for querying Business Partner credit information.
 *
 * <p>Executes a native SQL query against {@code C_BPartner} to retrieve:</p>
 * <ul>
 *   <li>{@code creditLimit} — Maximum approved credit ({@code SO_CreditLimit})</li>
 *   <li>{@code creditUsed} — Currently used credit ({@code SO_CreditUsed})</li>
 *   <li>{@code availableCredit} — Calculated available credit ({@code SMFPCF_Credit})</li>
 *   <li>{@code bpName} — Business Partner name</li>
 * </ul>
 *
 * <p>Security: Results are filtered by {@code ad_client_id} using the current
 * OBContext's readable clients, preventing cross-tenant data access.</p>
 *
 * @see CheckBusinessPartnerCredit The WebService endpoint that uses this model
 */
public class CheckBusinessPartnerCreditModel extends Model {

  @SuppressWarnings("deprecation")
  @Override
  public NativeQuery<?> createQuery(JSONObject jsonParams) throws JSONException {
    String businessPartnerId = jsonParams.getString("businessPartnerId");

    String sql = "SELECT bp.c_bpartner_id AS \"id\", " //
        + " bp.name AS \"bpName\", " //
        + " COALESCE(bp.so_creditlimit, 0) AS \"creditLimit\", " //
        + " COALESCE(bp.so_creditused, 0) AS \"creditUsed\", " //
        + " COALESCE(bp.em_smfpcf_credit, 0) AS \"availableCredit\" " //
        + " FROM c_bpartner bp " //
        + " WHERE bp.c_bpartner_id = :businessPartnerId " //
        + " AND bp.ad_client_id IN :clients";

    NativeQuery<?> query = OBDal.getInstance().getSession().createNativeQuery(sql);
    query.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
    query.setParameter("businessPartnerId", businessPartnerId);
    query.setParameterList("clients", OBContext.getOBContext().getReadableClients());

    return query;
  }

  @Override
  public String getName() {
    return "CheckBusinessPartnerCredit";
  }
}
