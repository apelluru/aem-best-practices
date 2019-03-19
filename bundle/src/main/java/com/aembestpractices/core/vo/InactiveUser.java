package com.aembestpractices.core.vo;

import java.util.Calendar;
import lombok.Data;

/**
 * @author apelluru
 */
@Data
public class InactiveUser {

  private String username;
  private String familyName;
  private String givenName;
  private Calendar lastAuthorized;
}
