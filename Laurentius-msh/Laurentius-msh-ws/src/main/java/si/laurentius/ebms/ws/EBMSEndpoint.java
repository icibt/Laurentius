package si.laurentius.ebms.ws;

import java.util.Collection;
import java.util.Date;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.jms.JMSException;
import javax.naming.NamingException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.BindingType;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.soap.SOAPBinding;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import si.jrc.msh.exception.EBMSError;
import si.jrc.msh.exception.EBMSErrorCode;
import si.laurentius.msh.inbox.mail.MSHInMail;
import si.laurentius.ebox.SEDBox;
import si.laurentius.commons.SEDInboxMailStatus;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.SEDSystemProperties;
import si.laurentius.commons.cxf.SoapUtils;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.JMSManagerInterface;
import si.laurentius.commons.interfaces.SEDDaoInterface;
import si.laurentius.commons.utils.HashUtils;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.StringFormater;
import si.laurentius.commons.utils.Utils;

/**
 *
 * @author Jože Rihtaršič
 */
@WebServiceProvider(serviceName = "msh")
@ServiceMode(value = Service.Mode.MESSAGE)
@BindingType(SOAPBinding.SOAP12HTTP_BINDING)
@org.apache.cxf.interceptor.InInterceptors(interceptors = {
  "si.jrc.msh.interceptor.EBMSLogInInterceptor",
  "si.jrc.msh.interceptor.EBMSInInterceptor",
  "si.jrc.msh.interceptor.MSHPluginInInterceptor"})
@org.apache.cxf.interceptor.OutInterceptors(interceptors = {
  "si.jrc.msh.interceptor.EBMSLogOutInterceptor",
  "si.jrc.msh.interceptor.EBMSOutInterceptor",
  "si.jrc.msh.interceptor.MSHPluginOutInterceptor"})
@org.apache.cxf.interceptor.OutFaultInterceptors(interceptors = {
  "si.jrc.msh.interceptor.EBMSLogOutInterceptor",
  "si.jrc.msh.interceptor.EBMSOutFaultInterceptor",
  "si.jrc.msh.interceptor.MSHPluginOutFaultInterceptor"})
@org.apache.cxf.interceptor.InFaultInterceptors(interceptors = {
  "si.jrc.msh.interceptor.EBMSLogInInterceptor",
  "si.jrc.msh.interceptor.EBMSInFaultInterceptor",
  "si.jrc.msh.interceptor.MSHPluginInFaultInterceptor"})
public class EBMSEndpoint implements Provider<SOAPMessage> {

  private static final SEDLogger LOG = new SEDLogger(EBMSEndpoint.class);

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mDB;
  HashUtils mpHU = new HashUtils();
  StringFormater msfFormat = new StringFormater();
  StorageUtils msuStorageUtils = new StorageUtils();
  @Resource
  WebServiceContext wsContext;

  @EJB(mappedName = SEDJNDI.JNDI_JMSMANAGER)
  JMSManagerInterface mJMS;

  /**
   *
   */
  public EBMSEndpoint() {

  }

  private String getJNDIPrefix() {

    return System.getProperty(SEDSystemProperties.SYS_PROP_JNDI_PREFIX, "java:/jboss/");
  }

  @Override
  public SOAPMessage invoke(SOAPMessage request) {
    long l = LOG.logStart();
    SOAPMessage response = null;
    try {
      // create empty response
      MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
      response = mf.createMessage();

      // Using this cxf specific code you can access the CXF Message and Exchange objects
      WrappedMessageContext wmc = (WrappedMessageContext) wsContext.getMessageContext();
      Message msg = wmc.getWrappedMessage();
      MSHInMail inmail = SoapUtils.getMSHInMail(msg);
      if (inmail == null) {

        LOG.logWarn("No inbox message", null);
        // todo application error
        return null;
      }

      SEDBox sb = SoapUtils.getMSHInMailReceiverBox(msg);

      if (sb == null) {
        LOG.formatedWarning("Inbox message %s but no inbox found  for message: %s", inmail.getId(),
            inmail.getReceiverEBox());
        // return error
      } else if (Utils.isEmptyString(inmail.getStatus())) {
        serializeMail(inmail, msg.getAttachments(), sb);
      }

    } catch (SOAPException ex) {
      LOG.logError(l, ex);
    }
    LOG.logEnd(l);
    return response;
  }

  private void serializeMail(MSHInMail mail, Collection<Attachment> lstAttch, SEDBox sb) {
    long l = LOG.logStart();
    // prepare mail to persist
    Date dt = new Date();
    // set current status
    mail.setStatus(SEDInboxMailStatus.RECEIVED.getValue());
    mail.setStatusDate(dt);
    mail.setReceivedDate(dt);

    try {
      mDB.serializeInMail(mail, "Laurentius-msh-ws");
    } catch (StorageException ex) {
      String errmsg = "Internal error occured while serializing incomming mail.";
      LOG.logError(l, errmsg, ex);
      throw new EBMSError(EBMSErrorCode.ExternalPayloadError, mail.getMessageId(), errmsg,
          SoapFault.FAULT_CODE_CLIENT);
    }

    try {
      // --------------------
      // serialize data to db

      mDB.setStatusToInMail(mail, SEDInboxMailStatus.RECEIVED, null);
    } catch (StorageException ex) {
      LOG.logError(l, "Error setting status ERROR to MSHInMail :'" + mail.getId() + "'!", ex);
    }
/*
    if (sb.getExport() != null && sb.getExport().getActive() != null 
        && sb.getExport().getActive()
        || sb.getXSLT()!= null        ) {
      try {
        mJMS.exportInMail(mail.getId().longValue());
      } catch (NamingException | JMSException ex) {
        LOG.logError(l, "Error occured while submitting mail to export queue:'" + mail.getId() + "'!",
            ex);
      }
    }*/
    LOG.logEnd(l);
  }

}
