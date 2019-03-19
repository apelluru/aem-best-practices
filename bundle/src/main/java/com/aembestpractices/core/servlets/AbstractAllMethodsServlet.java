package com.aembestpractices.core.servlets;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import com.aembestpractices.core.utils.ResourceResolverUtils;

/**
 * @author apelluru
 */
@Component
public class AbstractAllMethodsServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = 1L;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  protected ResourceResolver getResourceResolver(final String service) throws LoginException {
    return ResourceResolverUtils.getServiceResourceResolver(this.resourceResolverFactory, service);
  }

  public <T> T getService(final SlingHttpServletRequest request, final Class<T> clazz) {
    return request.getResource().adaptTo(clazz);
  }
}
