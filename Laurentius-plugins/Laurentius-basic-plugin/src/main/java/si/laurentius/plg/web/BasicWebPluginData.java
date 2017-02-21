/*
 * Copyright 2016, Supreme Court Republic of Slovenia
 * 
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work except in
 * compliance with the Licence. You may obtain a copy of the Licence at:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence
 * is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the Licence for the specific language governing permissions and limitations under
 * the Licence.
 */
package si.laurentius.plg.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import si.laurentius.commons.SEDGUIConstants;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.SEDSystemProperties;
import si.laurentius.commons.interfaces.SEDLookupsInterface;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.user.SEDUser;

/**
 *
 * @author Jože Rihtaršič
 */
@SessionScoped
@ManagedBean(name = "basicWebPluginData")
public class BasicWebPluginData {

  private static final SEDLogger LOG = new SEDLogger(BasicWebPluginData.class);

  @Resource
  WebServiceContext context;
  String currentPanel  =AppConstant.S_PANEL_IMP_EXPORT;

  @ManagedProperty(value = "#{loginManager}")
  private LoginManager loginManager;

  @EJB(mappedName = SEDJNDI.JNDI_SEDLOOKUPS)
  SEDLookupsInterface mDBLookUp;
  
 
  
  boolean showNavigator  =true;
  /**
   *
   * @return
   */
  protected ExternalContext externalContext() {
    return facesContext().getExternalContext();
  }


  /**
   *
   * @return
   */
  protected FacesContext facesContext() {
    return FacesContext.getCurrentInstance();
  }

  /**
   *
   * @return
   */
  public String getBuildVersion() {
    String strBuildVer = "";
    Manifest p;
    File manifestFile = null;
    String home = FacesContext.getCurrentInstance().getExternalContext().getRealPath("/");
    manifestFile = new File(home, "META-INF/MANIFEST.MF");
    try (FileInputStream fis = new FileInputStream(manifestFile)) {
      p = new Manifest();
      p.read(fis);
      Attributes a = p.getMainAttributes();
      strBuildVer = a.getValue("Implementation-Build");
    } catch (IOException ex) {

    }
    return strBuildVer;
  }

  /**
   *
   * @return
   */
  public String getClientIP() {
    return ((HttpServletRequest) externalContext().getRequest()).getRemoteAddr();
  }
  public String getCurrentPanel() {
    return currentPanel;
  }
  /**
   *
   * @return
   */
  public String getLocalDomain() {
    return System.getProperty(SEDSystemProperties.SYS_PROP_LAU_DOMAIN);
  }

  public LoginManager getLoginManager() {
    return loginManager;
  }
  /**
   *
   * @return
   */
  public SEDUser getUser() {
    long l = LOG.logStart();
    
    ExternalContext externalContext = facesContext().getExternalContext();
    SEDUser su =
            (SEDUser) externalContext.getSessionMap().get(SEDGUIConstants.SESSION_USER_VARIABLE_NAME);
    if (su == null) {
      Principal principal = facesContext().getExternalContext().getUserPrincipal();
      if (principal != null) {
        LOG.formatedlog("User principal is %s", principal.getName());
        su = mDBLookUp.getSEDUserByUserId(principal.getName());
        externalContext.getSessionMap().put(SEDGUIConstants.SESSION_USER_VARIABLE_NAME, su);
        
      } else {
        LOG.log("Principal is NULL");
      }
    }
    LOG.logEnd(l, su);
    return su;
  }
  public List<String> getUserEBoxes() {
    List<String> lst = new ArrayList<>();
    SEDUser usr = getUser();
    if (usr != null) {
      getUser().getSEDBoxes().stream().forEach((sb) -> {
        lst.add(sb.getLocalBoxName());
      });
    }
    return lst;
  }
  public boolean isShowNavigator() {
    return showNavigator;
  }

  public boolean isUserLocalBox(String locbox) {
    String[] bx = locbox.split("@");
    if (bx.length != 2 || !bx[1].equals(SEDSystemProperties.getLocalDomain())) {
      return true;
    }
    return getUserEBoxes().contains(bx[0]);
  }

 
  
  /**
   *
   * @param event
   */
  public void onToolbarButtonAction(ActionEvent event) {
    if (event != null) {
      String res = (String) event.getComponent().getAttributes().get("panel");
      currentPanel = res;
    }
  }

  

  public void setCurrentPanel(String currentPanel) {
    this.currentPanel = currentPanel;
  }
  public void setLoginManager(LoginManager loginManager) {
    this.loginManager = loginManager;
  }
  public void setShowNavigator(boolean showNavigator) {
    this.showNavigator = showNavigator;
  }


  
}