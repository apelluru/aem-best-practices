package com.aembestpractices.core.changelistener;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aembestpractices.core.services.UserService;
import com.aembestpractices.core.utils.ResourceResolverUtils;

/**
 * @author apelluru
 */
@Component(immediate = true)
@Service(value = JobConsumer.class)
@Property(name = JobConsumer.PROPERTY_TOPICS, value = UserAuthDataModifier.JOB_TOPIC)
public class UserAuthDataModifier implements JobConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(UserAuthDataModifier.class);

  public static final String JOB_TOPIC = "com/aembestpractices/core/changelistener/userAuthDataModifier";

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  private String getPropertyPathParent(final String resourcePath) {
    String result = StringUtils.EMPTY;

    if (StringUtils.isNotBlank(resourcePath)) {
      result = StringUtils.substringBeforeLast(resourcePath, "/");
    }

    return result;
  }

  private void procesLoginUser(final Session session, final Resource tokensRes) throws RepositoryException {
    if (tokensRes != null) {
      Date repTokenExpDate = null;
      if (tokensRes.hasChildren()) {
        final Iterator<Resource> childTokenResources = tokensRes.listChildren();
        while (childTokenResources.hasNext()) {
          final Resource childTokenResource = childTokenResources.next();
          final ValueMap childTokenResourceMap = childTokenResource.adaptTo(ValueMap.class);
          if (childTokenResourceMap != null) {
            final Date tempTokenExpDate = childTokenResourceMap.get("rep:token.exp", Date.class);
            if (tempTokenExpDate != null && (repTokenExpDate == null || tempTokenExpDate.compareTo(repTokenExpDate) > 0)) {
              repTokenExpDate = tempTokenExpDate;
            }
          }
        }
      }

      final Resource userRes = tokensRes.getParent();
      if (userRes != null) {
        final Node userNode = userRes.adaptTo(Node.class);
        if (userNode != null && repTokenExpDate != null) {
          final Calendar calTokenExp = Calendar.getInstance();
          calTokenExp.setTime(repTokenExpDate);

          if (userNode.hasProperty(UserService.LAST_AUTHORIZED_PROP)) {
            userNode.getProperty(UserService.LAST_AUTHORIZED_PROP).remove();
            userNode.getSession().save();
          }
          userNode.setProperty(UserService.LAST_AUTHORIZED_PROP, calTokenExp);
          session.save();
        }
      }
    }
  }

  @Override
  public JobResult process(final Job job) {
    JobResult result = null;
    final String resourcePath = (String) job.getProperty(UserAuthorizedListener.RESOURCE_PATH_PARAM);

    ResourceResolver serviceResourceResolver = null;

    try {
      serviceResourceResolver = ResourceResolverUtils.getServiceResourceResolver(this.resourceResolverFactory, ResourceResolverUtils.WRITE_SERVICE);
      final Session session = serviceResourceResolver.adaptTo(Session.class);
      final Resource tokensRes = serviceResourceResolver.getResource(getPropertyPathParent(resourcePath));
      procesLoginUser(session, tokensRes);
    } catch (RepositoryException | LoginException ex) {
      LOG.error("LoginException | RepositoryException", ex);
      result = JobResult.FAILED;
    } finally {
      ResourceResolverUtils.close(serviceResourceResolver);
    }

    result = JobResult.OK;
    return result;
  }
}
