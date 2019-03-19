package com.aembestpractices.core.services;

import java.io.IOException;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import org.apache.sling.api.resource.ResourceResolver;
import com.aembestpractices.core.vo.InactiveUser;

/**
 * @author apelluru
 */
public interface UserService {

  public static final String LAST_AUTHORIZED_PROP = "lastAuthorized";

  List<InactiveUser> deleteInactiveUsers(List<Node> usersList, final ResourceResolver serviceResourceResolver) throws RepositoryException;

  List<Node> findInactiveUsers(final ResourceResolver serviceResourceResolver) throws RepositoryException;

  List<String> getExcludeusers();

  void sendInactiveUsersEmail(List<InactiveUser> usersList, ResourceResolver serviceResourceResolver) throws RepositoryException, IOException;

}
