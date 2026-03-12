/*
 ************************************************************************************
 * Copyright (C) 2015 Openbravo S.L.U.
 * Licensed under the Openbravo Commercial License version 1.0
 * You may obtain a copy of the License at http://www.openbravo.com/legal/obcl.html
 * or in the legal folder of this module distribution.
 ************************************************************************************
 */

package com.doceleguas.pos.webservices.event;

import javax.enterprise.event.Observes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.retail.posterminal.OBPOSApplications;

import com.doceleguas.pos.webservices.POSWebSocketService;

public class POSTerminalEventHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(OBPOSApplications.ENTITY_NAME) };
  protected Logger logger = LogManager.getLogger();

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    final Entity terminalEntity = ModelProvider.getInstance()
        .getEntity(OBPOSApplications.ENTITY_NAME);
    final Property isLinkedProperty = terminalEntity
        .getProperty(OBPOSApplications.PROPERTY_ISLINKED);

    Boolean oldLinked = (Boolean) event.getPreviousState(isLinkedProperty);
    Boolean newLinked = (Boolean) event.getCurrentState(isLinkedProperty);

    if (Boolean.TRUE.equals(oldLinked) && Boolean.FALSE.equals(newLinked)) {
      POSWebSocketService.sendTerminalUnlinkedNotification(
          ((OBPOSApplications) event.getTargetInstance()).getSearchKey(), null);

    }
  }
}
