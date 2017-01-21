/*
 * Copyright 2015, Supreme Court Republic of Slovenia
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
package si.jrc.msh.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPBinding;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.jaxws.DispatchImpl;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.transports.http.configuration.ProxyServerType;
import si.laurentius.msh.outbox.mail.MSHOutMail;
import si.laurentius.msh.pmode.PartyIdentitySet;
import si.laurentius.msh.pmode.Protocol;

import si.laurentius.cert.SEDCertStore;
import si.laurentius.cert.SEDCertificate;
import si.jrc.msh.exception.EBMSError;
import si.jrc.msh.exception.EBMSErrorCode;
import si.jrc.msh.interceptor.EBMSInFaultInterceptor;
import si.jrc.msh.interceptor.EBMSInInterceptor;
import si.jrc.msh.interceptor.EBMSLogInInterceptor;
import si.jrc.msh.interceptor.EBMSLogOutInterceptor;
import si.jrc.msh.interceptor.EBMSOutFaultInterceptor;
import si.jrc.msh.interceptor.EBMSOutInterceptor;
import si.jrc.msh.interceptor.MSHPluginInFaultInterceptor;
import si.jrc.msh.interceptor.MSHPluginInInterceptor;
import si.jrc.msh.interceptor.MSHPluginOutFaultInterceptor;
import si.jrc.msh.interceptor.MSHPluginOutInterceptor;
import si.jrc.msh.transport.SMTPConduit;
import si.jrc.msh.transport.SMTPTransportFactory;
import si.laurentius.commons.MimeValues;
import si.laurentius.commons.cxf.SoapUtils;
import si.laurentius.commons.exception.SEDSecurityException;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.pmode.EBMSMessageContext;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.Utils;
import si.laurentius.lce.KeystoreUtils;
import si.laurentius.lce.tls.X509TrustManagerForAlias;

/**
 * Sets up MSH client and submits message.
 *
 * @author Jože Rihtaršič
 */
public class MshClient {

  /**
   * Logger for MshClient class
   */
  protected final static SEDLogger LOG = new SEDLogger(MshClient.class);

  /**
   * Common Lookups from database
   */
//  @EJB(mappedName = SEDJNDI.JNDI_SEDLOOKUPS)
  //private SEDLookupsInterface mSedLookups;
  /**
   * Keystore tools
   */
  private final KeystoreUtils mKSUtis = new KeystoreUtils();
  StorageUtils msStorageUtils = new StorageUtils();

  /**
   * Method sets set up a client for PartyIdentitySet.TransportProtocol.
   *
   * @param messageId
   * @param protocol: transport definition object frompmode
   * @param scs
   * @return Dispatch client for submitting message
   * @throws si.jrc.msh.exception.EBMSError (Error creating client)
   */
  public Dispatch<SOAPMessage> createClient(final String messageId,
      final PartyIdentitySet.TransportProtocol protocol, SEDCertStore scs, SEDCertStore rootCerts)
      throws EBMSError {

    if (protocol == null) {
      throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
          "Missing protocol!", SoapFault.FAULT_CODE_CLIENT);
    }

    if (protocol.getAddress() == null) {
      throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
          "Missing Address element!", SoapFault.FAULT_CODE_CLIENT);
    }
    if (protocol.getAddress().getValue() == null ||
        protocol.getAddress().getValue().trim().isEmpty()) {
      throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId, "Empty address!",
          SoapFault.FAULT_CODE_CLIENT);
    }

    // --------------------------------------------------------------------
    // create MTOM service
    String url = protocol.getAddress().getValue();
    QName serviceName1 = new QName("", "");
    QName portName1 = new QName("", "");
    Service s = Service.create(serviceName1);
    s.addPort(portName1, SOAPBinding.SOAP12HTTP_MTOM_BINDING, url);
    MTOMFeature mtomFt = new MTOMFeature(true);

    Dispatch<SOAPMessage> dispSOAPMsg =
        s.createDispatch(portName1, SOAPMessage.class, Service.Mode.MESSAGE, mtomFt);
    DispatchImpl dimpl = (org.apache.cxf.jaxws.DispatchImpl) dispSOAPMsg;
    SOAPBinding sb = (SOAPBinding) dispSOAPMsg.getBinding();
    sb.setMTOMEnabled(true);
    
    

    // --------------------------------------------------------------------
    // configure interceptors (log, ebms and plugin interceptors)
    Client cxfClient = dimpl.getClient();
    cxfClient.getInInterceptors().add(new EBMSLogInInterceptor());
    cxfClient.getInInterceptors().add(new EBMSInInterceptor());
    cxfClient.getInInterceptors().add(new MSHPluginInInterceptor());

    cxfClient.getOutInterceptors().add(new MSHPluginOutInterceptor());
    cxfClient.getOutInterceptors().add(new EBMSOutInterceptor());
    cxfClient.getOutInterceptors().add(new EBMSLogOutInterceptor());

    cxfClient.getInFaultInterceptors().add(new EBMSLogInInterceptor());
    cxfClient.getInFaultInterceptors().add(new EBMSInFaultInterceptor());
    cxfClient.getInFaultInterceptors().add(new MSHPluginInFaultInterceptor());
    cxfClient.getOutFaultInterceptors().add(new EBMSLogOutInterceptor());
    cxfClient.getOutFaultInterceptors().add(new EBMSOutFaultInterceptor());
    cxfClient.getOutFaultInterceptors().add(new MSHPluginOutFaultInterceptor());

    if (url.startsWith("smtp://")) {
      Bus bus = dimpl.getClient().getBus();
      ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
      extension.registerConduitInitiator("http://schemas.xmlsoap.org/soap/http", new SMTPTransportFactory());
      SMTPConduit smtp = (SMTPConduit) cxfClient.getConduit();    
      smtp.setSetJNDISession("java:jboss/mail/Default");
      
    } else {
      HTTPConduit http = (HTTPConduit) cxfClient.getConduit();
      // --------------------------------------------------------------------
      // set TLS
      if (protocol.getTLS() != null) {
        setupTLS(messageId, http, protocol.getTLS(), scs, rootCerts);
      }
      // --------------------------------------------------------------------
      // set http client policy
      HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
      if (protocol.getProxy() != null && !Utils.isEmptyString(protocol.getProxy().getHost())) {
        httpClientPolicy.setProxyServer(protocol.getProxy().getHost());
        if (protocol.getProxy().getPort() != null) {
          httpClientPolicy.setProxyServerPort(protocol.getProxy().getPort());
        }
        httpClientPolicy.setProxyServerType(Utils.isEmptyString(protocol.getProxy().getType()) ?
            ProxyServerType.HTTP :
            protocol.getProxy().getType().equalsIgnoreCase("SOCKS") ?
            ProxyServerType.SOCKS : ProxyServerType.HTTP);
      }

      httpClientPolicy.setConnectionTimeout(protocol.getAddress().getConnectionTimeout() != null ?
          protocol.getAddress().getConnectionTimeout() : 120000);
      httpClientPolicy.setReceiveTimeout(protocol.getAddress().getReceiveTimeout() != null ?
          protocol.getAddress().getReceiveTimeout() : 120000);
      httpClientPolicy.setAllowChunking(protocol.getAddress().getChunked() != null ?
          protocol.getAddress().getChunked() : false);

      // set http Policy
      http.setClient(httpClientPolicy);
    }
    return dispSOAPMsg;
  }

  /**
   * Method submits message according pmode configuration
   *
   * @param mail
   * @param ebms
   * @param scs
   * @return
   */
  public Result pushMessage(MSHOutMail mail, EBMSMessageContext ebms, SEDCertStore scs,
      SEDCertStore rootCACerts) {

    long l = LOG.logStart(mail);
    Result r = new Result();

    Dispatch<SOAPMessage> client = null;
    try {

      client = createClient(mail.getMessageId(), ebms.getTransportProtocol(), scs, rootCACerts);

      // set context parameters!
      SoapUtils.setEBMSMessageOutContext(ebms, client);
      SoapUtils.setMSHOutnMail(mail, client);

      // create empty soap mesage
      MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
      SOAPMessage soapReq = mf.createMessage();

      long st = LOG.getTime();
      LOG.formatedlog("Start submiting mail %s", mail.getMessageId());
      SOAPMessage soapRes = client.invoke(soapReq);
      
      r.setResult(soapRes);
      LOG.formatedlog("Submit mail %s in ( %d ms).", mail.getMessageId(), (LOG.getTime() - st));

      if (soapRes != null) {
        File file;
        try {
          file = StorageUtils.getNewStorageFile(MimeValues.MIME_XML.getSuffix(), "RSP_");
          try (FileOutputStream fos = new FileOutputStream(file)) {
            soapRes.writeTo(fos);
            String respFilePath = StorageUtils.getRelativePath(file);
            r.setResultFile(respFilePath);
            r.setMimeType(MimeValues.MIME_XML.getMimeType());
          } catch (IOException ex) {
            LOG.logError(l, "ERROR saving response to file!", ex);
          }
        } catch (StorageException ex) {
          LOG.logError(l, "ERROR saving response to file!", ex);
        }
      }

    } catch (javax.xml.ws.WebServiceException ex) {

      String key = "org.apache.cxf.staxutils.W3CDOMStreamWriter";
      Throwable initCause = Utils.getInitCause(ex);

      if (client != null && client.getResponseContext().containsKey(key)) {
        r.setError(new EBMSError(EBMSErrorCode.ApplicationError, mail.getMessageId(),
            "Soap fault error: " + ex.getMessage(), ex, SoapFault.FAULT_CODE_CLIENT));

        W3CDOMStreamWriter wr = (W3CDOMStreamWriter) client.getResponseContext().get(key);

        try {
          File file = StorageUtils.getNewStorageFile(MimeValues.MIME_XML.getSuffix(), "ERR_");
          try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(wr.toString());
            //  wr.getDocument().
            r.setResultFile(StorageUtils.getRelativePath(file));
            r.setMimeType(MimeValues.MIME_XML.getMimeType());
          } catch (IOException ex1) {
            LOG.logError(l, "ERROR saving saop fault to file!", ex);
          }

        } catch (StorageException ex1) {
          LOG.logError(l, "ERROR saving saop fault to file!", ex);
        }
      } else if (initCause instanceof EBMSError) {
        r.setError((EBMSError) initCause);
      } else {
        r.setError(new EBMSError(EBMSErrorCode.DeliveryFailure, mail.getMessageId(),
            "HTTP error: " + Utils.getInitCauseMessage(ex), ex, SoapFault.FAULT_CODE_CLIENT));
        try {
          String res = msStorageUtils.storeThrowableAndGetRelativePath(ex);
          r.setMimeType(MimeValues.MIME_TXT.getMimeType());
          r.setResultFile(res);
        } catch (StorageException ex1) {
          LOG.logError(l, "ERROR saving saop fault to file!", ex1);
        }
      }
    } catch (SOAPException ex) {
      try {
        String res = msStorageUtils.storeThrowableAndGetRelativePath(ex);
        r.setMimeType(MimeValues.MIME_TXT.getMimeType());
        r.setResultFile(res);
      } catch (StorageException ex1) {
        LOG.logError(l, "ERROR saving saop fault to file!", ex);
      }
      r.setError(new EBMSError(EBMSErrorCode.ApplicationError, mail.getMessageId(),
          "Error occured while creating soap message!", ex, SoapFault.FAULT_CODE_CLIENT));
    } catch (EBMSError ex) {
      try {
        String res = msStorageUtils.storeThrowableAndGetRelativePath(ex);
        r.setResultFile(res);
        r.setMimeType(MimeValues.MIME_TXT.getMimeType());
      } catch (StorageException ex1) {
        LOG.logError(l, "ERROR saving saop fault to file!", ex);
      }
      r.setError(ex);
    } catch (Throwable ex) {
      try {
        String res = msStorageUtils.storeThrowableAndGetRelativePath(ex);
        r.setResultFile(res);
        r.setMimeType(MimeValues.MIME_TXT.getMimeType());
      } catch (StorageException ex1) {
        LOG.logError(l, "Unexpected error!", ex);
      }
      r.setError(new EBMSError(EBMSErrorCode.ApplicationError, mail.getMessageId(),
          "Unexpected error!", ex, SoapFault.FAULT_CODE_CLIENT));
    }

    LOG.logEnd(l);
    return r;
  }

  /**
   * Method sets Truststore and key (if needed) to https client for TLS
   *
   * @param client - http(s) client
   * @param tls - pmode tls configuration
   * @throws FileNotFoundException
   * @throws IOException
   * @throws SEDSecurityException
   */
  private void setupTLS(String messageId, HTTPConduit httpConduit, Protocol.TLS tls,
      SEDCertStore scs, SEDCertStore rootCACerts) {
    TLSClientParameters tlsCP = null;

    String serverTrustAlias = tls.getServerTrustCertAlias();
    String keyAlias = tls.getClientKeyAlias();
    LOG.formatedWarning("SET TLS : keyalias %s , cert alias %s", keyAlias, serverTrustAlias);
    List<X509Certificate> lstRootCAS = null;

    // set client's key cert for mutual identification
    if (!Utils.isEmptyString(keyAlias)) {
      keyAlias = keyAlias.trim();
      if (scs == null) {
        String msg = String.format("(Message: %s) Keystore for alias '%s' not exists!", messageId,
            keyAlias);
        throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
            msg, SoapFault.FAULT_CODE_CLIENT);
      }
      SEDCertificate aliasKey = null;
      for (SEDCertificate c : scs.getSEDCertificates()) {
        if (Objects.equals(c.getAlias(), keyAlias) && c.isKeyEntry()) {
          aliasKey = c;
          break;

        }
      }

      if (aliasKey == null) {
        String msg = String.format(
            "(Message: %s) Key for alias: '%s' do not exist in keystore!", messageId, keyAlias);
        throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
            msg, SoapFault.FAULT_CODE_CLIENT);
      }

      //get key managers
      KeyManager[] myKeyManagers;
      try {
        myKeyManagers = mKSUtis.getKeyManagersForAlias(scs, keyAlias);
      } catch (SEDSecurityException ex) {
        String msg = String.format("(Message: %s) Error occured while creating KeyManagers for " +
            "alias: '%s'! Error: ", messageId, keyAlias, ex.getMessage());
        throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
            msg, ex, SoapFault.FAULT_CODE_CLIENT);
      }
      tlsCP = new TLSClientParameters();
      tlsCP.setKeyManagers(myKeyManagers);
    }

    // set trustore cert
    if (!Utils.isEmptyString(serverTrustAlias)) {
      serverTrustAlias = serverTrustAlias.trim();
      try {
        if (lstRootCAS == null) {
          lstRootCAS = mKSUtis.getTrustedCertsFromKeystore(rootCACerts);
        }
        LOG.formatedWarning("Get certiticate: %s from keystore", serverTrustAlias, scs.getName());
        X509Certificate expectedCert = mKSUtis.getTrustedCertForAlias(scs, serverTrustAlias);
        TrustManager[] trustStoreManagers = new TrustManager[]{new X509TrustManagerForAlias(
          expectedCert, lstRootCAS)};
        tlsCP = tlsCP == null ? new TLSClientParameters() : tlsCP;
        tlsCP.setTrustManagers(trustStoreManagers);
        tlsCP.setDisableCNCheck(tls.getDisableCNAndHostnameCheck() != null &&
            tls.getDisableCNAndHostnameCheck());
      } catch (SEDSecurityException ex) {
        String msg = String.format("(Message: %s) Error occured while creating TrustManagers for " +
            "truststore '%s'! Error: ", messageId, serverTrustAlias, ex.getMessage());
        throw new EBMSError(EBMSErrorCode.PModeConfigurationError, messageId,
            msg, ex, SoapFault.FAULT_CODE_CLIENT);
      }

    }

    if (tlsCP != null) {
      LOG.log("\t TLS is setted:");
      httpConduit.setTlsClientParameters(tlsCP);
    }
  }

}
