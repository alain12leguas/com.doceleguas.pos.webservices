# OCWS_Order import type — database prerequisite

`SaveOrder` enqueues `C_IMPORT_ENTRY` rows with `typeofdata = OCWS_Order`. Openbravo validates this field against the **Type of Import Data** list (`AD_REFERENCE_ID` = `11F86B630ECB4A57B28927193F8AB99D`).

If `OCWS_Order` is missing from `AD_REF_LIST`, you get:

`ValidationException: ... value (OCWS_Order) is not allowed`

## Fix (module sourcedata)

This tree ships [`../src-db/database/sourcedata/AD_REF_LIST.xml`](../src-db/database/sourcedata/AD_REF_LIST.xml) adding list value `OCWS_Order` for that reference.

If your Openbravo clone already has a full `com.doceleguas.pos.webservices` module with its own `AD_REF_LIST.xml`, **merge** the `OCWS_Order` `<AD_REF_LIST>` block into that file (do not duplicate the same `AD_REF_LIST_ID` or `VALUE`).

## Apply to your database

From the Openbravo root (with `Openbravo.properties` pointing at the target DB):

```bash
ant update.database
```

Then restart the application server if your deployment process requires it.

## Verify

```sql
select ad_ref_list_id, value, name, isactive
from ad_ref_list
where ad_reference_id = '11F86B630ECB4A57B28927193F8AB99D'
  and value = 'OCWS_Order';
```

You should see one active row.

Or in Application Dictionary: **Reference** → **Type of Import Data** → **List Reference** → confirm **OCWS_Order** exists.
