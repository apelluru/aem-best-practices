package com.aembestpractices.core.utils;

import java.util.HashMap;
import java.util.Map;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

/**
 * @author apelluru
 */
public class ResourceResolverUtils {
  
  public static final String READ_SERVICE = "readservice";
  public static final String WRITE_SERVICE = "writeservice";
  
  public static void close(final ResourceResolver resourceResolver) {
    if (resourceResolver != null && resourceResolver.isLive()) {
      resourceResolver.close();
    }
  }

  public static ResourceResolver getServiceResourceResolver(final ResourceResolverFactory resourceResolverFactory, final String subservice)
      throws LoginException {
    final Map<String, Object> param = new HashMap<>();
    param.put(ResourceResolverFactory.SUBSERVICE, subservice);
    return resourceResolverFactory.getServiceResourceResolver(param);
  }

  private ResourceResolverUtils() {
    // do nothing
  }
}
