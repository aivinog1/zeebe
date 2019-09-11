/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;

@Controller
@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class SSOController implements ErrorController {
  
  private static final String ERROR_PATH = "/error";

  @Autowired
  protected AuthenticationController authenticator;
  
  @Autowired
  protected SSOWebSecurityConfig config;

  protected  final Logger logger = LoggerFactory.getLogger(this.getClass());
  
  // Is called by access to protected url with as not logged in user
  @RequestMapping(ERROR_PATH)
  protected String error(final RedirectAttributes redirectAttributes) throws IOException {
      return redirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
  }

  // user authentication will be delegated to auth0
  @RequestMapping(value = SSOWebSecurityConfig.LOGIN_RESOURCE, method = {RequestMethod.GET,RequestMethod.POST})
  protected String login(final HttpServletRequest req) {
      logger.debug("Performing login");
      String authorizeUrl = authenticator.buildAuthorizeUrl(req, getRedirectURI(req,SSOWebSecurityConfig.CALLBACK_URI ))
              .withAudience(String.format("https://%s/userinfo", config.getBackendDomain())) // get user profile
              .withScope("openid profile email") // which info we request
              .build();
      return redirect(authorizeUrl);
  }
  
  // Is called by auth0 with results of user authentication (GET)
  @RequestMapping(value = SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.GET)
  protected void getCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
      handle(req, res);
  }

  //Is called by auth0 with results of user authentication (POST)
  @RequestMapping(value =  SSOWebSecurityConfig.CALLBACK_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  protected void postCallback(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
      handle(req, res);
  }
  
  // Is called when authentication was successful, but there is no permission to use this application
  @RequestMapping(value = SSOWebSecurityConfig.NO_PERMISSION)
  @ResponseBody
  protected String noPermissions() {
    return "No Permission for Operate - Please check your organization id in operate configuration or cloud configuration.";
  }

  //
  protected void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      Tokens tokens = authenticator.handle(req);
      TokenAuthentication tokenAuth = new TokenAuthentication(tokens, config);
      tokenAuth.authenticate();
      res.sendRedirect(SSOWebSecurityConfig.ROOT);
    } catch (InsufficientAuthenticationException iae) {
      logoutAndRedirectToNoPermissionPage(req, res);
    } catch (AuthenticationException | IdentityVerificationException e) {
      clearContextAndRedirectToLogin(res, e);
    }
  }

  protected void clearContextAndRedirectToLogin(HttpServletResponse res, Exception e) throws IOException {
    logger.error("Error in authentication callback", e);
    SecurityContextHolder.clearContext();
    res.sendRedirect(SSOWebSecurityConfig.LOGIN_RESOURCE);
  }

  protected void logoutAndRedirectToNoPermissionPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.error("User is authenticated but there are no permissions. Show noPermission message");
    if (req.getSession() != null) {
      req.getSession().invalidate();
    }
    SecurityContextHolder.clearContext();
    logoutFromAuth0(res, getRedirectURI(req, "/noPermission"));
  }

  protected void logoutFromAuth0(HttpServletResponse res, String returnTo) throws IOException {
    String logoutUrl = String.format(
            "https://%s/v2/logout?client_id=%s&returnTo=%s",
            config.getDomain(),
            config.getClientId(),
            returnTo);
    res.sendRedirect(logoutUrl);
  }
  
  protected String getRedirectURI(final HttpServletRequest req,final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    return redirectUri + redirectTo;
  }
  
  protected String redirect(String toURL) {
    return "redirect:" + toURL;
  }
 
  @Override
  public String getErrorPath() {
    return ERROR_PATH;
  }

}