/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ccsds.moims.mo.mal.impl;

import org.ccsds.moims.mo.mal.impl.util.MALClose;
import java.util.Hashtable;
import org.ccsds.moims.mo.mal.MALInvokeOperation;
import org.ccsds.moims.mo.mal.MALProgressOperation;
import org.ccsds.moims.mo.mal.MALPubSubOperation;
import org.ccsds.moims.mo.mal.MALRequestOperation;
import org.ccsds.moims.mo.mal.MALSendOperation;
import org.ccsds.moims.mo.mal.MALService;
import org.ccsds.moims.mo.mal.MALSubmitOperation;
import org.ccsds.moims.mo.mal.consumer.MALConsumer;
import org.ccsds.moims.mo.mal.consumer.MALInteractionListener;
import org.ccsds.moims.mo.mal.structures.Blob;
import org.ccsds.moims.mo.mal.structures.DomainIdentifier;
import org.ccsds.moims.mo.mal.structures.Element;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.QoSLevel;
import org.ccsds.moims.mo.mal.structures.SessionType;
import org.ccsds.moims.mo.mal.structures.Subscription;
import org.ccsds.moims.mo.mal.structures.URI;
import org.ccsds.moims.mo.mal.impl.transport.MALTransportSingleton;

/**
 *
 * @author cooper_sf
 */
public class MALConsumerImpl extends MALClose implements MALConsumer
{
  private final MALImpl impl;
  private final MALMessageDetails details;


  public MALConsumerImpl(MALImpl impl, MALConsumerManagerImpl parent, URI uriTo, URI brokerUri, MALService service, Blob authenticationId, DomainIdentifier domain, Identifier networkZone, SessionType sessionType, Identifier sessionName, QoSLevel qosLevel, Hashtable qosProps, Integer priority) throws MALException
  {
    super(parent);
    this.impl = impl;
    this.details = new MALMessageDetails(MALTransportSingleton.instance(uriTo, null).createEndPoint(null, null, qosProps), uriTo, brokerUri, service, authenticationId, domain, networkZone, sessionType, sessionName, qosLevel, qosProps, priority);
  }

  @Override
  public void send(MALSendOperation op, Element requestBody) throws MALException
  {
    impl.getSendingInterface().send(details, op, requestBody);
  }

  @Override
  public void submit(MALSubmitOperation op, Element requestBody) throws MALException
  {
    impl.getSendingInterface().submit(details, op, requestBody);
  }

  @Override
  public Element request(MALRequestOperation op, Element requestBody) throws MALException
  {
    return impl.getSendingInterface().request(details, op, requestBody);
  }

  @Override
  public Element invoke(MALInvokeOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    return impl.getSendingInterface().invoke(details, op, requestBody, listener);
  }

  @Override
  public Element progress(MALProgressOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    return impl.getSendingInterface().progress(details, op, requestBody, listener);
  }

  @Override
  public void register(MALPubSubOperation op, Subscription subscription, MALInteractionListener listener) throws MALException
  {
    impl.getSendingInterface().register(details, op, subscription, listener);
  }

  @Override
  public void deregister(MALPubSubOperation op, IdentifierList unsubscription) throws MALException
  {
    impl.getSendingInterface().deregister(details, op, unsubscription);
  }

  @Override
  public void asyncSubmit(MALSubmitOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    impl.getSendingInterface().submitAsync(details, op, requestBody, listener);
  }

  @Override
  public void asyncRequest(MALRequestOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void asyncInvoke(MALInvokeOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void asyncProgress(MALProgressOperation op, Element requestBody, MALInteractionListener listener) throws MALException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void asyncRegister(MALPubSubOperation op, Subscription subscription, MALInteractionListener listener) throws MALException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void asyncDeregister(MALPubSubOperation op, IdentifierList unsubscription, MALInteractionListener listener) throws MALException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }
}
