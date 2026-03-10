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
 *   <li>{@code availableCredit} — Remaining credit room, computed as
 *       {@code SO_CreditLimit} minus the real-time outstanding debt.
 *       The debt is calculated inline from standard Openbravo tables
 *       ({@code c_invoice}/{@code fin_payment_schedule} for invoice outstanding
 *       amounts, and {@code fin_payment} for net generated credit), with
 *       multi-currency conversion via {@code c_currency_convert}.</li>
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
        // availableCredit = creditLimit - outstanding debt (invoices + net payment credit)
        + " COALESCE(bp.so_creditlimit, 0) - COALESCE(( " //
        + "   SELECT SUM(sub.amount) FROM ( " //
        // Part 1: outstanding invoice amounts with currency conversion
        + "     SELECT COALESCE(SUM(c_currency_convert( " //
        + "       ps.outstandingamt * (CASE WHEN inv.issotrx = 'Y' THEN 1 ELSE -1 END), " //
        + "       inv.c_currency_id, bp.bp_currency_id, " //
        + "       inv.created, null, inv.ad_client_id, inv.ad_org_id " //
        + "     )), 0) AS amount " //
        + "     FROM c_invoice inv " //
        + "       JOIN fin_payment_schedule ps ON inv.c_invoice_id = ps.c_invoice_id " //
        + "     WHERE ps.outstandingamt <> 0 " //
        + "       AND inv.c_bpartner_id = bp.c_bpartner_id " //
        + "     UNION ALL " //
        // Part 2: net payment credit (leave-as-credit) with currency conversion
        + "     SELECT COALESCE(SUM(c_currency_convert( " //
        + "       (p.generated_credit - p.used_credit) " //
        + "         * (CASE WHEN p.isreceipt = 'Y' THEN -1 ELSE 1 END), " //
        + "       p.c_currency_id, bp.bp_currency_id, " //
        + "       p.created, null, p.ad_client_id, p.ad_org_id " //
        + "     )), 0) AS amount " //
        + "     FROM fin_payment p " //
        + "     WHERE (p.generated_credit - p.used_credit) <> 0 " //
        + "       AND p.generated_credit <> 0 " //
        + "       AND p.processed = 'Y' " //
        + "       AND p.c_bpartner_id = bp.c_bpartner_id " //
        + "   ) sub " //
        + " ), 0) AS \"availableCredit\" " //
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
