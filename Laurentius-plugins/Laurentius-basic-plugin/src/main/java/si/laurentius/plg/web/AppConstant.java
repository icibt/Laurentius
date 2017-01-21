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

import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

/**
 *
 * @author Jože Rihtaršič
 */
@ApplicationScoped
@ManagedBean(name = "AppConstant")
public class AppConstant {

  /**
     *
     */
  public static final String S_PANEL_IMP_XSLT = "PANEL_IMP_XSLT";

  /**
     *
     */
  public static final String S_PANEL_IMP_EXPORT = "PANEL_IMP_EXPORT";

  /**
     *
     */
  public static final String S_PANEL_IMP_EXECUTE = "PANEL_IMP_EXECUTE";

  public String getS_PANEL_IMP_XSLT() {
    return S_PANEL_IMP_XSLT;
  }

  public String getS_PANEL_IMP_EXPORT() {
    return S_PANEL_IMP_EXPORT;
  }

  public String getS_PANEL_IMP_EXECUTE() {
    return S_PANEL_IMP_EXECUTE;
  }

  

}
