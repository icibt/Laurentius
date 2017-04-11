/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package si.jrc.msh.plugin.meps.ejb;

import java.io.File;
import java.util.Collections;
import java.util.List;
import javax.ejb.Local;
import si.laurentius.plugin.meps.PhysicalAddressType;
import si.laurentius.plugin.meps.ServiceType;


/**
 *
 * @author sluzba
 */
@Local
public interface MEPSDataInterface {


  List<ServiceType> getServices();
  List<PhysicalAddressType> getAddresses();
  PhysicalAddressType getSenderAddress();
 
 /*
  IMPXslt getAddresses(String instance);
  boolean addXSLT(IMPXslt sb);
  List<IMPXslt> getAddresses();
  boolean removeXSLT(IMPXslt sb);
  boolean updateXSLT(IMPXslt sb);
*/


}
