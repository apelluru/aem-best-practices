package com.aembestpractices.core.services.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.activation.DataSource;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.jcr.query.qom.Constraint;
import javax.jcr.query.qom.Literal;
import javax.jcr.query.qom.PropertyValue;
import javax.jcr.query.qom.QueryObjectModel;
import javax.jcr.query.qom.QueryObjectModelConstants;
import javax.jcr.query.qom.QueryObjectModelFactory;
import javax.jcr.query.qom.Selector;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.mail.ByteArrayDataSource;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aembestpractices.core.services.UserService;
import com.aembestpractices.core.utils.ResourceResolverUtils;
import com.aembestpractices.core.vo.InactiveUser;
import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import lombok.Getter;

/**
 * @author apelluru
 */
@Service(UserService.class)
@Component(immediate = true, metatype = true)
public class UserServiceImpl implements UserService {

  private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

  @Property(label = "Users that have been inactive for longer as n-months", intValue = 24)
  private static final String INACTIVE_MONTHS = "userservice.inactive.months";

  @Property(label = "Exclude users", unbounded = PropertyUnbounded.ARRAY, cardinality = 50,
      description = "Please enter usernames which should be exclude for deleting")
  private static final String EXCLUDE_USERS = "userservice.inactive.excludeusers";

  @Property(label = "Email addresses", unbounded = PropertyUnbounded.ARRAY, cardinality = 50,
      description = "Please enter email ids who should email for inactive users")
  private static final String TO_ADDRESSES = "userservice.inactive.toaddresses";

  private static String USERS_HOME_PATH = "/home/users";
  private static String FAMILY_NAME = "profile/familyName";
  private static String GIVEN_NAME = "profile/givenName";
  private static String AUTHORIZABLE_ID = "rep:authorizableId";
  private static final String EXCEL_CONTENTYPE = "application/vnd.ms-excel";
  private static String[] headerColumns = {"Username", "FamilyName", "GivenName", "LastAuthorized"};
  private static String TEMPLATE_PATH = "/etc/social/notification/emailtemplates/enrollment/testing.html";

  public static NodeIterator searchByCalendarPropertyBefore(final String queryRootPath, final String primaryType, final String propertyName,
      final Calendar maxDate, final ResourceResolver resourceResolver) throws RepositoryException {

    if (resourceResolver == null) {
      return null;
    }
    final ValueFactory valueFactory = resourceResolver.adaptTo(Session.class).getValueFactory();
    final QueryObjectModelFactory modelFactory = resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager().getQOMFactory();
    final Selector selector = modelFactory.selector(primaryType, "s");
    final Constraint constraintDescendantNode = modelFactory.descendantNode("s", queryRootPath);

    final PropertyValue queryPropertyValue = modelFactory.propertyValue("s", propertyName);
    final Literal queryLiteral = modelFactory.literal(valueFactory.createValue(maxDate));
    final Constraint constraintResourceType =
        modelFactory.comparison(queryPropertyValue, QueryObjectModelConstants.JCR_OPERATOR_LESS_THAN, queryLiteral);

    final Constraint constraint = modelFactory.and(constraintDescendantNode, constraintResourceType);

    final QueryObjectModel queryObjectModel = modelFactory.createQuery(selector, constraint, null, null);

    return queryObjectModel.execute().getNodes();
  }

  @Getter
  private int defaultInactiveMonths;
  @Getter
  private List<String> excludeusers;
  @Getter
  private List<String> toaddresses;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Reference
  private MessageGatewayService messageGatewayService;

  @Activate
  @Modified
  protected void activate(final ComponentContext componentContext) {
    final Dictionary<?, ?> properties = componentContext.getProperties();
    this.defaultInactiveMonths = PropertiesUtil.toInteger(properties.get(INACTIVE_MONTHS), 24);
    final String[] excludeUsers = PropertiesUtil.toStringArray(properties.get(EXCLUDE_USERS));
    if (excludeUsers != null && excludeUsers.length > 0) {
      excludeusers = Arrays.asList(excludeUsers);
    }
    final String[] toAddresses = PropertiesUtil.toStringArray(properties.get(TO_ADDRESSES));
    if (toAddresses != null && toAddresses.length > 0) {
      toaddresses = Arrays.asList(toAddresses);
    }
  }

  @Override
  public List<InactiveUser> deleteInactiveUsers(List<Node> usersList, final ResourceResolver serviceResourceResolver) throws RepositoryException {
    if (usersList == null) {
      usersList = findInactiveUsers(serviceResourceResolver);
    }
    final List<InactiveUser> inactiveUsers = new ArrayList<>();
    if (usersList != null && !usersList.isEmpty()) {
      try {
        for (final Node userNode : usersList) {
          final InactiveUser inactiveUser = new InactiveUser();
          inactiveUser.setUsername(userNode.getProperty(AUTHORIZABLE_ID).getString());
          if (userNode.hasProperty(UserService.LAST_AUTHORIZED_PROP)) {
            inactiveUser.setLastAuthorized(userNode.getProperty(UserService.LAST_AUTHORIZED_PROP).getDate());
          }
          if (userNode.hasProperty(FAMILY_NAME)) {
            inactiveUser.setFamilyName(userNode.getProperty(FAMILY_NAME).getString());
          }
          if (userNode.hasProperty(GIVEN_NAME)) {
            inactiveUser.setGivenName(userNode.getProperty(GIVEN_NAME).getString());
          }
          inactiveUsers.add(inactiveUser);
          userNode.remove();
        }
      } finally {
        if (serviceResourceResolver != null) {
          final Session session = serviceResourceResolver.adaptTo(Session.class);
          if (session != null) {
            session.save();
          }
        }
      }
    }
    return inactiveUsers;
  }

  private boolean excludeUsers(Node userNode) throws RepositoryException {
    if (userNode.hasProperty(AUTHORIZABLE_ID)) {
      final String username = userNode.getProperty(AUTHORIZABLE_ID).getString();
      if (getExcludeusers().contains(username)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Node> findInactiveUsers(final ResourceResolver serviceResourceResolver) throws RepositoryException {
    final Calendar calTokenExp = Calendar.getInstance();
    calTokenExp.add(Calendar.MONTH, -(getDefaultInactiveMonths()));

    final List<Node> usersList = new ArrayList<>();
    final NodeIterator inactiveUsers =
        searchByCalendarPropertyBefore(USERS_HOME_PATH, "rep:User", UserService.LAST_AUTHORIZED_PROP, calTokenExp, serviceResourceResolver);
    while (inactiveUsers != null && inactiveUsers.hasNext()) {
      final Node userNode = inactiveUsers.nextNode();
      if (userNode != null && !excludeUsers(userNode)) {
        usersList.add(userNode);
      }
    }
    return usersList;
  }

  private Email getEmail(MailTemplate mailTemplate, Class<? extends Email> mailType, Map<String, String> params)
      throws EmailException, MessagingException, IOException {
    final Email email = mailTemplate.getEmail(StrLookup.mapLookup(params), mailType);
    if ((params.containsKey("senderEmailAddress")) && (params.containsKey("senderName"))) {
      email.setFrom(params.get("senderEmailAddress"), params.get("senderName"));
    } else if (params.containsKey("senderEmailAddress")) {
      email.setFrom(params.get("senderEmailAddress"));
    }
    if (params.containsKey("subject")) {
      email.setSubject(params.get("subject"));
    }
    return email;
  }

  private MailTemplate getMailTemplate(String templatePath) {
    MailTemplate mailTemplate = null;
    ResourceResolver resourceResolver = null;
    try {
      final Map<String, Object> authInfo = Collections.singletonMap("sling.service.subservice", ResourceResolverUtils.READ_SERVICE);
      resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo);
      mailTemplate = MailTemplate.create(templatePath, resourceResolver.adaptTo(Session.class));
      if (mailTemplate == null) {
        throw new IllegalArgumentException("Mail template path [ " + templatePath + " ] could not resolve to a valid template");
      }
    } catch (final LoginException e) {
      LOG.error("Unable to obtain an administrative resource resolver to get the Mail Template at [ " + templatePath + " ]", e);
    } finally {
      if (resourceResolver != null) {
        resourceResolver.close();
      }
    }
    return mailTemplate;
  }

  private Class<? extends Email> getMailType(String templatePath) {
    return templatePath.endsWith(".html") ? HtmlEmail.class : SimpleEmail.class;
  }

  @Override
  public void sendInactiveUsersEmail(List<InactiveUser> usersList, final ResourceResolver serviceResourceResolver)
      throws RepositoryException, IOException {
    XSSFWorkbook workbook = null;
    if (usersList != null && !usersList.isEmpty()) {
      try {

        workbook = new XSSFWorkbook();
        final XSSFSheet inactiveUsersSheet = workbook.createSheet("Inactive Users");
        final Row headerRow = inactiveUsersSheet.createRow(0);
        for (int i = 0; i < headerColumns.length; i++) {
          final Cell cell = headerRow.createCell(i);
          cell.setCellValue(headerColumns[i]);
        }
        int rowNum = 1;
        for (final InactiveUser inactiveUser : usersList) {
          final Row row = inactiveUsersSheet.createRow(rowNum++);
          row.createCell(0).setCellValue(inactiveUser.getUsername());
          row.createCell(1).setCellValue(inactiveUser.getFamilyName());
          row.createCell(2).setCellValue(inactiveUser.getGivenName());
          row.createCell(3).setCellValue(inactiveUser.getLastAuthorized());
        }

        final Map<String, String> emailParams = new HashMap<>();
        final Map<String, DataSource> attachments = new HashMap<>();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        final InputStream excelStream = new ByteArrayInputStream(out.toByteArray());
        out.close();

        final DataSource dataSource = new ByteArrayDataSource(excelStream, EXCEL_CONTENTYPE);
        attachments.put("InactiveUsers.xlsx", dataSource);

        final MailTemplate mailTemplate = getMailTemplate(TEMPLATE_PATH);
        final Class<? extends Email> mailType = getMailType(TEMPLATE_PATH);
        final Email email = getEmail(mailTemplate, mailType, emailParams);

        final List<InternetAddress> recipients = new ArrayList<>();
        if (!toaddresses.isEmpty()) {
          for (final String emailId : toaddresses) {
            recipients.add(new InternetAddress(emailId));
          }
        }
        email.setTo(recipients);

        // attachments
        if (attachments.size() > 0) {
          for (final Map.Entry<String, DataSource> entry : attachments.entrySet()) {
            ((HtmlEmail) email).attach(entry.getValue(), entry.getKey(), null);
          }
        }

        final MessageGateway<Email> messageGateway = messageGatewayService.getGateway(mailType);
        messageGateway.send(email);
        LOG.info("EMAIL SENT");

      } catch (final EmailException ex) {
        LOG.error("EmailException {}", ex);
      } catch (final MessagingException ex) {
        LOG.error("MessagingException {}", ex);
      } finally {
        if (workbook != null) {
          workbook.close();
        }
      }
    }
  }
}
