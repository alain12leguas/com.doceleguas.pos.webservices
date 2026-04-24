# Regression suite: OCWS_Order import (OCRE-POS → SaveOrder queue)

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

- `C_ORDER` / `C_ORDERLINE`: document no, amounts, BP, warehouse, terminal (`EM_Obpos_Applications_ID`), `DocStatus`, `Processed`, `GrandTotal`.
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
       o.m_pricelist_id, o.c_currency_id, o.em_obpos_applications_id, o.totallines, o.grandtotal, o.docstatus,
       o.processed, o.processing
from c_order o
where o.documentno = :documentNo
order by o.updated desc;
```

```sql
-- 3) Order lines parity
select l.c_orderline_id, l.line, l.m_product_id, l.qtyordered, l.priceactual,
       l.pricelist, l.pricestd, l.linenetamt, l.gross_unit_price, l.grosspricelist,
       l.grosspricestd, l.line_gross_amount, l.c_tax_id
from c_orderline l
join c_order o on o.c_order_id = l.c_order_id
where o.documentno = :documentNo
order by l.line;
```

```sql
-- 4) Basic payment parity (referenceNo is not used in native parity mode)
select p.fin_payment_id, p.documentno, p.c_bpartner_id, p.fin_paymentmethod_id,
       p.fin_financial_account_id, p.amount, p.c_currency_id, p.status, p.referenceno
from fin_payment p
where exists (
  select 1
  from fin_payment_scheduledetail psd
  join fin_payment_scheduleorder pso on pso.fin_payment_schedule_id = psd.fin_payment_schedule_order
  join c_order o on o.c_order_id = pso.c_order_id
  where psd.fin_payment_id = p.fin_payment_id
    and o.documentno = :documentNo
)
order by p.updated desc;
```

```sql
-- 5) Hard parity assertions for booked + non-zero lines
select
  o.documentno,
  o.docstatus,
  o.processed,
  count(*) filter (where coalesce(l.priceactual, 0) = 0) as zero_priceactual_lines,
  count(*) filter (where coalesce(l.linenetamt, 0) = 0) as zero_linenetamt_lines,
  count(*) filter (where coalesce(l.gross_unit_price, 0) = 0) as zero_gross_unit_price_lines,
  count(*) filter (where coalesce(l.line_gross_amount, 0) = 0) as zero_line_gross_amount_lines
from c_order o
join c_orderline l on l.c_order_id = o.c_order_id
where o.documentno = :documentNo
group by o.documentno, o.docstatus, o.processed;
```

```sql
-- 6) Optional generated invoice / shipment checks
select i.c_invoice_id, i.documentno, i.grandtotal, i.docstatus
from c_invoice i
where i.c_order_id in (select c_order_id from c_order where documentno = :documentNo);

select s.m_inout_id, s.documentno, s.docstatus
from m_inout s
where s.c_order_id in (select c_order_id from c_order where documentno = :documentNo);
```

### Execution notes for native routing

- `-Ddoceleguas.ocorder.mode=shadow_native`: validates native payload shape before persistence.
- `-Ddoceleguas.ocorder.mode=native`: executes native persistence for all classified flows.
- There is no runtime fallback to retail loaders in the OCWS_Order chain.

### Flow-specific assertions

Run the checklist with one payload per flow:

- `STANDARD_SALE`: `docstatus='CO'`, `processed='Y'`, payment plan fully settled.
- `RETURN`/blind return: return document type, negative line quantities/amounts, payment out
  consistency (`APP`/refund path), no residual outstanding for fully paid return.
- `QUOTATION`: quotation doc type, no forced payment rows, expected draft/quotation lifecycle.
- `LAYAWAY`: verify `step` handling is persisted best-effort and payment/outstanding consistency
  for partial/full payments.

### ExternalOrderLoader parity (delivery modes + quotation linkage)

After changes to `OcreExternalOrderParityService`, `IdentityExternalEnvelopeTransform`,
`OcreQuotationLinkageHelper`, or OBRDM mapping in `CoreOrderPersistenceAdapter`:

1. **PickAndCarry / `step` all**: `lines[].obrdmDeliveryMode` omitted or `PickAndCarry`, `step: all`
   (or default from parity). Expect `deliver` set as in legacy `ExternalOrderLoader.handleOrderSteps`
   and shipment/invoice flags consistent with `shouldCreateStandardPosDocuments`.
2. **Non–PickAndCarry** (e.g. `HomeDelivery`): at least one line with `obrdmDeliveryMode` not
   `PickAndCarry`. Expect `obposQtytodeliver` 0 on those lines in the normalized payload and no
   immediate customer shipment where retail would defer delivery.
3. **Quotation-only**: `isQuotation: true`, no `oldId`, expect quotation doc type, `UE` doc status,
   no payment creation.
4. **Quotation revision**: new QT with `oldId` of prior quotation: prior `C_Order` moves to
   `DocStatus` `CJ`, new line links `EM_Obpos_Rejected_Quotat` when applicable.
5. **Order from quotation**: `isQuotation: false` with `oldId` of source QT: new order
   `Quotation_ID` and lines `QuotationLine` links; source QT `DocStatus` `CA` (retail contract).

## 3. Automated client checks (OCRE-POS)

In the `pos` workspace, keep unit tests for `transformForApi` / envelope shape aligned with `SaveOrder` contract (202, `messageId`, `data` array). Extend when new envelope fields are introduced.

## 4. Sign-off criterion

No change to OCWS_Order import code is “done” for production until at least one **sale**, one **return** (if used), and one **invoice** scenario pass steps 2–3 with no unexpected diffs.
