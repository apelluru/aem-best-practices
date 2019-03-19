package com.aembestpractices.core.schedular;

import java.util.Dictionary;
import java.util.List;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aembestpractices.core.services.UserService;
import com.aembestpractices.core.utils.ResourceResolverUtils;
import com.aembestpractices.core.vo.InactiveUser;
import com.day.cq.commons.Externalizer;

/**
 * @author apelluru
 */
@Component(immediate = true, metatype = true)
@Service(value = Runnable.class)
@Properties({@Property(name = "scheduler.expression", value = "0 0 4 1/1 * ? *", description = "runs everyday 4am"),
    @Property(name = "scheduler.concurrent", boolValue = false)})
public class DeleteUserScheduler implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(DeleteUserScheduler.class);

  private static final boolean DEF_SCHEDULER_ENABLED = true;
  @Property(label = "Scheduler enabled", description = "Enable or disable store import scheduler", boolValue = DEF_SCHEDULER_ENABLED)
  private static final String CONF_SCHEDULER_ENABLED = "scheduler.enabled";

  private static boolean running = false;

  public static boolean isRunning() {
    return running;
  }

  @Reference
  protected ResourceResolverFactory resourceResolverFactory;

  @Reference
  protected SlingSettingsService slingSettings;
  @Reference
  private UserService userService;

  private boolean enabled;

  @Activate
  @Modified
  public void activate(final ComponentContext componentContext) {
    try {
      final Dictionary<?, ?> osgiProperties = componentContext.getProperties();
      this.enabled = PropertiesUtil.toBoolean(osgiProperties.get(CONF_SCHEDULER_ENABLED), DEF_SCHEDULER_ENABLED);
    } catch (final Exception ex) {
      LOG.error("Exception while activating DeleteUserScheduler {}", ex);
    }
  }

  @Deactivate
  public void deactivate(final ComponentContext componentContext) {
    // do nothing
  }

  private void deleteUsers() {
    ResourceResolver resourceResolver = null;

    try {
      final long startTimestamp = System.currentTimeMillis();
      if (this.slingSettings.getRunModes().contains(Externalizer.AUTHOR)) {
        resourceResolver = ResourceResolverUtils.getServiceResourceResolver(this.resourceResolverFactory, ResourceResolverUtils.WRITE_SERVICE);

        final List<InactiveUser> inactiveUsers = userService.deleteInactiveUsers(null, resourceResolver);
        userService.sendInactiveUsersEmail(inactiveUsers, resourceResolver);

        LOG.info("DeleteUserScheduler :: finished in {} minutes", (((System.currentTimeMillis() - startTimestamp) / 1000) / 60));
      }
    } catch (final Exception ex) {
      LOG.error("Exception {}", ex);
    } finally {
      ResourceResolverUtils.close(resourceResolver);
    }
  }

  @Override
  public void run() {
    try {
      if (running) {
        LOG.info("DeleteUserScheduler is already running");
      } else if (!this.enabled) {
        LOG.info("DeleteUserScheduler is disabled");
      } else {
        running = true;
        this.deleteUsers();
        running = false;
      }
    } catch (final Exception ex) {
      running = false;
      LOG.error("Exception {}", ex);
    }
  }
}
