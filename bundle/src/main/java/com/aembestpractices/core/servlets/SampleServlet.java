package com.aembestpractices.core.servlets;

import java.io.IOException;
import javax.servlet.ServletException;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.http.HttpStatus;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author apelluru
 */
@Slf4j
@SlingServlet(resourceTypes = "sling/servlet/default", extensions = "json", methods = {"GET", "POST"})
public class SampleServlet extends AbstractAllMethodsServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    log.info("SampleServlet Request");
    response.getWriter().print("{}");
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    response.setStatus(HttpStatus.SC_METHOD_NOT_ALLOWED);
  }
}
