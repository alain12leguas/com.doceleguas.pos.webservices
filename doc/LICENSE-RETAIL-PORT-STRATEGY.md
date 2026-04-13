# License and porting strategy: Web POS (OBCL) vs custom module (OBPL)

## Facts

- `org.openbravo.retail.posterminal` (Web POS) in the standard Openbravo distribution is licensed under the **Openbravo Commercial License (OBCL)**. Source headers reference `http://www.openbravo.com/legal/obcl.html`.
- `com.doceleguas.pos.webservices` is maintained as **Openbravo Public License (OBPL)** custom code.

## Allowed directions (process, not legal advice)

1. **Dependency (current bridge)**  
   Keep a **module dependency** on Web POS and call retail APIs (`ExternalOrderLoader`, `OrderLoader`) from OBPL code. This does **not** copy OBCL sources into the OBPL module.

2. **Reimplementation**  
   Rebuild external-envelope handling and Core/DAL persistence **without** copying OBCL text. Use public ERP APIs, DAL entities, and your own mapping. This is the long-term goal of the strangler plan.

3. **Copying or large verbatim extraction**  
   Requires **explicit permission** from the rights holder and compatible license terms for your distribution. Treat “copy `OrderLoader` into OBPL” as **not** automatic.

## Project decision

Until native `ExternalEnvelopeTransform` + `CoreOrderPersistenceAdapter` cover all OCRE-POS flows, **retain** the Web POS module dependency and use `LegacyRetailOrderPipelineAdapter` as the technical seam. Document removal criteria in `doc/WHEN-TO-DROP-RETAIL-MODULE.md`.
