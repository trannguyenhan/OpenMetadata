/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.events.subscription.email;

import static org.openmetadata.schema.api.events.CreateEventSubscription.SubscriptionType.EMAIL;
import static org.openmetadata.service.events.subscription.AlertsRuleEvaluator.getEntity;
import static org.openmetadata.service.util.SubscriptionUtil.buildReceiversListFromActions;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.EntityInterface;
import org.openmetadata.schema.alert.type.EmailAlertConfig;
import org.openmetadata.schema.entity.events.EventSubscription;
import org.openmetadata.schema.type.ChangeEvent;
import org.openmetadata.schema.type.EventType;
import org.openmetadata.service.events.errors.EventPublisherException;
import org.openmetadata.service.events.subscription.SubscriptionPublisher;
import org.openmetadata.service.exception.CatalogExceptionMessage;
import org.openmetadata.service.formatter.decorators.EmailMessageDecorator;
import org.openmetadata.service.formatter.decorators.MessageDecorator;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.resources.events.EventResource;
import org.openmetadata.service.util.EmailUtil;
import org.openmetadata.service.util.JsonUtils;

@Slf4j
public class EmailPublisher extends SubscriptionPublisher {
  private final MessageDecorator<EmailMessage> emailDecorator = new EmailMessageDecorator();
  private final EmailAlertConfig emailAlertConfig;
  private final CollectionDAO daoCollection;

  public EmailPublisher(EventSubscription eventSub, CollectionDAO dao) {
    super(eventSub);
    if (eventSub.getSubscriptionType() == EMAIL) {
      this.emailAlertConfig = JsonUtils.convertValue(eventSub.getSubscriptionConfig(), EmailAlertConfig.class);
      this.daoCollection = dao;
    } else {
      throw new IllegalArgumentException("Email Alert Invoked with Illegal Type and Settings.");
    }
  }

  @Override
  public void onStartDelegate() {
    LOG.info("Email Publisher Started");
  }

  @Override
  public void onShutdownDelegate() {
    LOG.info("Email Publisher Stopped");
  }

  @Override
  public void sendAlert(EventResource.EventList list) {
    for (ChangeEvent event : list.getData()) {
      try {
        Set<String> receivers = buildReceiversList(event);
        EmailMessage emailMessage = emailDecorator.buildMessage(event);
        String titleTemplate = titleBuilder(event);
        for (String email : receivers) {
          EmailUtil.sendChangeEventMail(email, titleTemplate, emailMessage);
        }
        setSuccessStatus(System.currentTimeMillis());
      } catch (Exception e) {
        setErrorStatus(System.currentTimeMillis(), 500, e.getMessage());
        String message = CatalogExceptionMessage.eventPublisherFailedToPublish(EMAIL, event, e.getMessage());
        LOG.error(message);
        throw new EventPublisherException(message);
      }
    }
  }

  private String titleBuilder(ChangeEvent event) {
    String name = event.getEntityFullyQualifiedName();
    Object entity = event.getEntity();
    if (entity != null) {
      Class<?> aClass = entity.getClass();
      try {
        Field nameField = aClass.getDeclaredField("name");
        nameField.setAccessible(true);
        name = (String) nameField.get(entity);
      } catch (Exception ignored) {

      }
    }

    return "%s " + event.getEntityType() + " " + name + " " + eventBuilder(event);
  }

  private String eventBuilder(ChangeEvent event) {
    if (event.getEventType() == EventType.ENTITY_CREATED) {
      return "Created";
    }
    if (event.getEventType() == EventType.ENTITY_UPDATED) {
      return "Updated";
    }
    if (event.getEventType() == EventType.ENTITY_DELETED) {
      return "Deleted";
    }
    if (event.getEventType() == EventType.ENTITY_RESTORED) {
      return "Restored";
    }
    if (event.getEventType() == EventType.ENTITY_NO_CHANGE) {
      return "No Change";
    }
    if (event.getEventType() == EventType.ENTITY_SOFT_DELETED) {
      return "Deleted";
    }
    return "Change";
  }

  private Set<String> buildReceiversList(ChangeEvent changeEvent) {
    Set<String> receiverList =
        emailAlertConfig.getReceivers() == null ? new HashSet<>() : emailAlertConfig.getReceivers();
    EntityInterface entityInterface = getEntity(changeEvent);
    receiverList.addAll(
        buildReceiversListFromActions(
            emailAlertConfig, EMAIL, daoCollection, entityInterface.getId(), changeEvent.getEntityType()));
    return receiverList;
  }
}
