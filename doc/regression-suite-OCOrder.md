# Regression suite: OCOrder import (OCRE-POS → SaveOrder queue)

Manual / semi-automated checks to run when changing `OcreOrderLoadOrchestrator`, retail bridge, or persistence port.

## 1. Golden payloads

1. Export **real** OCRE-POS envelopes from staging (anonymize BPs/products if needed).
2. Store under version control as JSON files (suggested path: internal wiki or secure repo; avoid committing production secrets).
3. For each payload, record **expected** `messageId` and business outcome (new order, return, invoice flags, etc.).

## 2. Before/after ERP comparison

On a throwaway database (or snapshot):

1. Note `C_IMPORT_ENTRY` row for `messageId` before run (if reprocessing, archive or use new id).
2. Submit `POST /ws/com.doceleguas.pos.webservices.SaveOrder` with the golden body; wait until import worker sets `IMPORTSTATUS` to `Processed` or `Error`.
3. Compare **after** state vs a baseline taken on the **previous** release for the same payload class:

Suggested SQL checks (adjust table/column names to your customization):

- `C_ORDER` / `C_ORDERLINE`: document no, amounts, BP, warehouse, `DocStatus`, `GrandTotal`.
- `FIN_PAYMENT` / schedule lines if the ticket has payments.
- `C_INVOICE` / `C_INVOICELINE` when `ocreIssueInvoice` / shipment paths apply.
- `M_INOUT` if shipments are generated.

### SQL checklist (parity retail vs core)

Use the same `documentNo` (or `messageId` correlation) in both executions.

```sql
-- 1) Import queue outcome
select c_import_entry_id, importstatus, errormsg, responseinfo
from c_import_entry
where c_import_entry_id = :messageId;
```

```sql
-- 2) Order header parity
select o.c_order_id, o.documentno, o.ad_org_id, o.c_bpartner_id, o.m_warehouse_id,
       o.m_pricelist_id, o.c_currency_id, o.totallines, o.grandtotal, o.docstatus
from c_order o
where o.documentno = :documentNo
order by o.updated desc;
```

```sql
-- 3) Order lines parity
select l.c_orderline_id, l.line, l.m_product_id, l.qtyordered, l.priceactual,
       l.pricelist, l.linenetamt, l.c_tax_id
from c_orderline l
join c_order o on o.c_order_id = l.c_order_id
where o.documentno = :documentNo
order by l.line;
```

```sql
-- 4) Basic payment parity
select p.fin_payment_id, p.documentno, p.c_bpartner_id, p.fin_paymentmethod_id,
       p.fin_financial_account_id, p.amount, p.c_currency_id, p.status
from fin_payment p
where p.referenceno like :documentNo || ':%'
order by p.updated desc;
```

```sql
-- 5) Optional generated invoice / shipment checks
select i.c_invoice_id, i.documentno, i.grandtotal, i.docstatus
from c_invoice i
where i.c_order_id in (select c_order_id from c_order where documentno = :documentNo);

select s.m_inout_id, s.documentno, s.docstatus
from m_inout s
where s.c_order_id in (select c_order_id from c_order where documentno = :documentNo);
```

### Execution notes for the new modes

- `-Ddoceleguas.ocorder.mode=retail`: baseline.
- `-Ddoceleguas.ocorder.mode=shadow_native`: validates native payload shape, persists via retail.
- `-Ddoceleguas.ocorder.mode=native`: tries `CoreOrderPersistenceAdapter`; if unsupported/fails, falls back to retail.

## 3. Automated client checks (OCRE-POS)

In the `pos` workspace, keep unit tests for `transformForApi` / envelope shape aligned with `SaveOrder` contract (202, `messageId`, `data` array). Extend when new envelope fields are introduced.

## 4. Sign-off criterion

No change to OCOrder import code is “done” for production until at least one **sale**, one **return** (if used), and one **invoice** scenario pass steps 2–3 with no unexpected diffs.
