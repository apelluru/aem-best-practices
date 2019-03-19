package com.aembestpractices.core.changelistener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.event.jobs.JobManager;

/**
 * @author apelluru
 */
@Component(immediate = true, label = "User auth token Listener", description = "Listener for session token validation", metatype = true)
@Properties({@Property(name = ResourceChangeListener.CHANGES, value = {"CHANGED", "ADDED"}),
    @Property(name = ResourceChangeListener.PATHS, value = {"glob:/home/users/**/.tokens/*"})})
@Service
public class UserAuthorizedListener implements ResourceChangeListener, ExternalResourceChangeListener {

  public static final String RESOURCE_PATH_PARAM = "resourcePath";

  @Reference
  private JobManager jobManager;

  @Override
  public void onChange(final List<ResourceChange> changes) {
    Map<String, Object> payload = null;
    for (final ResourceChange change : changes) {
      payload = new HashMap<>();
      payload.put(RESOURCE_PATH_PARAM, change.getPath());

      this.jobManager.addJob(UserAuthDataModifier.JOB_TOPIC, payload);
    }
  }
}
