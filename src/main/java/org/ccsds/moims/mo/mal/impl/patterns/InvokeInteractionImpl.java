/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ccsds.moims.mo.mal.impl.patterns;

import org.ccsds.moims.mo.mal.provider.MALInvoke;
import org.ccsds.moims.mo.mal.structures.Element;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.MALInvokeOperation;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.transport.MALMessage;
import org.ccsds.moims.mo.mal.impl.MALImpl;
import org.ccsds.moims.mo.mal.impl.MALServiceComponentImpl;
import org.ccsds.moims.mo.mal.structures.StandardError;

/**
 *
 * @author cooper_sf
 */
public class InvokeInteractionImpl extends BaseInteractionImpl implements MALInvoke
{
  private boolean ackSent = false;
  
  public InvokeInteractionImpl(MALImpl impl, MALServiceComponentImpl handler, Identifier internalTransId, MALMessage msg)
  {
    super(impl, handler, internalTransId, msg);
  }

  @Override
  public void sendAcknowledgement(Element result) throws MALException
  {
    ackSent = true;
    impl.getSendingInterface().returnResponse(handler, internalTransId, msg.getHeader(), MALInvokeOperation.INVOKE_ACK_STAGE, result);
  }

  @Override
  public void sendResponse(Element result) throws MALException
  {
    impl.getSendingInterface().returnResponse(handler, internalTransId, msg.getHeader(), MALInvokeOperation.INVOKE_RESPONSE_STAGE, result);
  }

  @Override
  public void sendError(StandardError error) throws MALException
  {
    Byte stage = MALInvokeOperation.INVOKE_ACK_STAGE;

    if (ackSent)
    {
      stage = MALInvokeOperation.INVOKE_RESPONSE_STAGE;
    }

    impl.getSendingInterface().returnError(handler, internalTransId, msg.getHeader(), stage, error);
  }
}