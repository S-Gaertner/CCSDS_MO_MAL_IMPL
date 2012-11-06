/* ----------------------------------------------------------------------------
 * (C) 2010      European Space Agency
 *               European Space Operations Centre
 *               Darmstadt Germany
 * ----------------------------------------------------------------------------
 * System       : CCSDS MO MAL Implementation
 * Author       : Sam Cooper
 *
 * ----------------------------------------------------------------------------
 */
package org.ccsds.moims.mo.mal.impl;

import java.util.Map;
import java.util.TreeMap;
import org.ccsds.moims.mo.mal.*;
import org.ccsds.moims.mo.mal.consumer.MALInteractionListener;
import org.ccsds.moims.mo.mal.impl.util.Logging;
import org.ccsds.moims.mo.mal.provider.MALPublishInteractionListener;
import org.ccsds.moims.mo.mal.structures.InteractionType;
import org.ccsds.moims.mo.mal.structures.URI;
import org.ccsds.moims.mo.mal.structures.Union;
import org.ccsds.moims.mo.mal.transport.*;

/**
 * The interaction map is the central class responsible for maintaining the state of interactions for a MAL instance.
 * New transactions are added using the creatTransaction methods. Synchronous consumer interactions are handled by 
 * calling waitForResponse, and any message received is 'handled' using the handleStage method.
 * 
 * When a new interaction is created, an internal (to this class) interaction handler class is created which is responsible for ensuring
 * the correct stages are received in the correct order.
 */
class InteractionMap
{
  private static volatile long transId = 0;
  private final Map<Long, InternalOperationHandler> transMap =
          new TreeMap<Long, InternalOperationHandler>();
  private final Map<Long, Map.Entry> resolveMap = new TreeMap<Long, Map.Entry>();

  Long createTransaction(final int interactionType,
          final boolean syncOperation,
          final MALInteractionListener listener) throws MALInteractionException
  {
    final Long oTransId = getTransactionId();

    InternalOperationHandler handler = null;

    if (InteractionType._SUBMIT_INDEX == interactionType)
    {
      handler = new SubmitOperationHandler(syncOperation, listener);
    }
    else if (InteractionType._REQUEST_INDEX == interactionType)
    {
      handler = new RequestOperationHandler(syncOperation, listener);
    }
    else if (InteractionType._INVOKE_INDEX == interactionType)
    {
      handler = new InvokeOperationHandler(syncOperation, listener);
    }
    else if (InteractionType._PROGRESS_INDEX == interactionType)
    {
      handler = new ProgressOperationHandler(syncOperation, listener);
    }
    else if (InteractionType._PUBSUB_INDEX == interactionType)
    {
      handler = new PubSubOperationHandler(syncOperation, listener);
    }
    else
    {
      throw new MALInteractionException(new MALStandardError(MALHelper.INTERNAL_ERROR_NUMBER,
              new Union("Pattern not supported")));
    }

    synchronized (transMap)
    {
      transMap.put(oTransId, handler);
    }

    return oTransId;
  }

  Long createTransaction(final boolean syncOperation, final MALPublishInteractionListener listener)
  {
    final Long oTransId = getTransactionId();

    synchronized (transMap)
    {
      transMap.put(oTransId,
              new PubSubOperationHandler(syncOperation, new InteractionListenerPublishAdapter(listener)));
    }

    return oTransId;
  }

  MALMessage waitForResponse(final Long id)
  {
    InternalOperationHandler handler = null;

    synchronized (transMap)
    {
      if (transMap.containsKey(id))
      {
        handler = transMap.get(id);
      }
      else
      {
        Logging.logMessage("ERROR: **** No key found in service maps to wait for response! " + id);
      }
    }

    MALMessage retVal = null;

    // do the wait
    if (null != handler)
    {
      synchronized (handler.lock)
      {
        while (!handler.lock.getLock())
        {
          try
          {
            handler.lock.wait();
          }
          catch (InterruptedException ex)
          {
            ex.printStackTrace();
          }
        }
      }

      // must have value now
      retVal = handler.getResult();

      // delete entry from trans map
      synchronized (transMap)
      {
        if (handler.finished())
        {
          Logging.logMessage("INFO: A Removing handler from service maps: " + id);
          transMap.remove(id);
        }
      }
    }

    return retVal;
  }

  void handleStage(final MALMessage msg) throws MALInteractionException
  {
    final Long id = msg.getHeader().getTransactionId();
    InternalOperationHandler handler = null;

    synchronized (transMap)
    {
      if (transMap.containsKey(id))
      {
        handler = transMap.get(id);
      }
      else
      {
        Logging.logMessage("ERROR: **** No key found in service maps to get listener! " + id);
      }
    }

    if (null != handler)
    {
      handler.handleStage(msg);

      // delete entry from trans map
      if (handler.finished())
      {
        synchronized (transMap)
        {
          Logging.logMessage("INFO: B Removing handler from service maps: " + id);
          transMap.remove(id);
        }
      }
    }
  }

  Long addTransactionSource(final URI urlFrom, final Long transactionId)
  {
    final Long internalTransactionId = getTransactionId();

    synchronized (resolveMap)
    {
      resolveMap.put(internalTransactionId, new TreeMap.SimpleEntry(urlFrom, transactionId));
    }

    return internalTransactionId;
  }

  Map.Entry resolveTransactionSource(final Long internalTransactionId)
  {
    synchronized (resolveMap)
    {
      return resolveMap.get(internalTransactionId);
    }
  }

  void removeTransactionSource(final Long internalTransactionId)
  {
    synchronized (resolveMap)
    {
      if (null == resolveMap.remove(internalTransactionId))
      {
        Logging.logMessage("WARNING: **** No key found in service maps for received interaction of " + internalTransactionId);
      }
    }
  }

  static synchronized Long getTransactionId()
  {
    return transId++;
  }

  private abstract static class InternalOperationHandler
  {
    private static final class BooleanLock
    {
      private boolean lock = false;

      public synchronized boolean getLock()
      {
        return lock;
      }

      public synchronized void setLock()
      {
        lock = true;
      }
    }
    protected final boolean syncOperation;
    protected final MALInteractionListener listener;
    protected final BooleanLock lock = new BooleanLock();
    private MALMessage result = null;

    public InternalOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      this.syncOperation = syncOperation;
      this.listener = listener;
    }

    protected void signalResponse(final MALMessage msg)
    {
      result = msg;

      // do the wait
      synchronized (lock)
      {
        lock.setLock();
        lock.notifyAll();
      }
    }

    public abstract void handleStage(final MALMessage msg) throws MALInteractionException;

    public MALMessage getResult()
    {
      return result;
    }

    public abstract boolean finished();

    protected static void logUnexpectedTransitionError(final int interactionType, final int interactionStage)
    {
      Logging.logMessage("ERROR: Unexpected transition IP("
              + InteractionType.fromInt(interactionType)
              + ") Stage(" + interactionStage + ")");
    }
  }

  private static class SubmitOperationHandler extends InternalOperationHandler
  {
    protected boolean receivedInitialStage = false;
    protected boolean takenFinalStage = false;
    protected final int interactionType;
    protected final int interactionStage;

    public SubmitOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      super(syncOperation, listener);

      this.interactionType = InteractionType._SUBMIT_INDEX;
      this.interactionStage = MALSubmitOperation._SUBMIT_ACK_STAGE;
    }

    public SubmitOperationHandler(final int interactionType,
            final int interactionStage,
            final boolean syncOperation,
            final MALInteractionListener listener)
    {
      super(syncOperation, listener);

      this.interactionType = interactionType;
      this.interactionStage = interactionStage;
    }

    @Override
    public synchronized void handleStage(final MALMessage msg) throws MALInteractionException
    {
      if (!receivedInitialStage)
      {
        try
        {
          if ((interactionType == msg.getHeader().getInteractionType().getOrdinal())
                  && checkStage(msg.getHeader().getInteractionStage().getValue()))
          {
            receivedInitialStage = true;
            if (syncOperation)
            {
              signalResponse(msg);
            }
            else
            {
              takenFinalStage = true;
              informListener(msg);
            }
          }
          else
          {
            logUnexpectedTransitionError(msg.getHeader().getInteractionType().getOrdinal(),
                    msg.getHeader().getInteractionStage().getValue());
            throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
          }
        }
        catch (MALException ex)
        {
          // nothing we can do with this
          ex.printStackTrace();
        }
      }
      else
      {
        logUnexpectedTransitionError(interactionType, interactionStage);
        throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
      }
    }

    @Override
    public synchronized MALMessage getResult()
    {
      takenFinalStage = true;

      return super.getResult();
    }

    @Override
    public synchronized boolean finished()
    {
      return takenFinalStage;
    }

    protected boolean checkStage(final int stage)
    {
      return (interactionStage == stage);
    }

    protected void informListener(final MALMessage msg) throws MALException
    {
      if (msg.getHeader().getIsErrorMessage())
      {
        listener.submitErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
      }
      else
      {
        listener.submitAckReceived(msg.getHeader(), msg.getQoSProperties());
      }
    }
  }

  private static final class RequestOperationHandler extends SubmitOperationHandler
  {
    public RequestOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      super(InteractionType._REQUEST_INDEX,
              MALRequestOperation._REQUEST_RESPONSE_STAGE,
              syncOperation,
              listener);
    }

    @Override
    protected void informListener(final MALMessage msg) throws MALException
    {
      if (msg.getHeader().getIsErrorMessage())
      {
        listener.requestErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
      }
      else
      {
        listener.requestResponseReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
      }
    }
  }

  private static final class InvokeOperationHandler extends InternalOperationHandler
  {
    private boolean receivedAck = false;
    private boolean receivedResponse = false;

    public InvokeOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      super(syncOperation, listener);
    }

    @Override
    public void handleStage(final MALMessage msg) throws MALInteractionException
    {
      final int interactionType = msg.getHeader().getInteractionType().getOrdinal();
      final int interactionStage = msg.getHeader().getInteractionStage().getValue();

      boolean isAckStage = false;

      synchronized (this)
      {
        if (!receivedAck)
        {
          if ((interactionType == InteractionType._INVOKE_INDEX)
                  && (interactionStage == MALInvokeOperation._INVOKE_ACK_STAGE))
          {
            isAckStage = true;
            receivedAck = true;
            if (!syncOperation && msg.getHeader().getIsErrorMessage())
            {
              receivedResponse = true;
            }
          }
          else
          {
            logUnexpectedTransitionError(interactionType, interactionStage);
            throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
          }
        }
        else if ((!receivedResponse) && (interactionType == InteractionType._INVOKE_INDEX)
                && (interactionStage == MALInvokeOperation._INVOKE_RESPONSE_STAGE))
        {
          receivedResponse = true;
        }
        else
        {
          logUnexpectedTransitionError(interactionType, interactionStage);
          throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
        }
      }

      if (isAckStage)
      {
        if (syncOperation)
        {
          signalResponse(msg);
        }
        else
        {
          try
          {
            if (msg.getHeader().getIsErrorMessage())
            {
              listener.invokeAckErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
            }
            else
            {
              listener.invokeAckReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
            }
          }
          catch (MALException ex)
          {
            // nothing we can do with this
            ex.printStackTrace();
          }
        }
      }
      else
      {
        try
        {
          if (msg.getHeader().getIsErrorMessage())
          {
            listener.invokeResponseErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
          }
          else
          {
            listener.invokeResponseReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
          }
        }
        catch (MALException ex)
        {
          // nothing we can do with this
          ex.printStackTrace();
        }
      }
    }

    @Override
    public synchronized boolean finished()
    {
      return receivedResponse;
    }
  }

  private static final class ProgressOperationHandler extends InternalOperationHandler
  {
    private boolean receivedAck = false;
    private boolean receivedResponse = false;

    public ProgressOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      super(syncOperation, listener);
    }

    @Override
    public void handleStage(final MALMessage msg) throws MALInteractionException
    {
      final int interactionType = msg.getHeader().getInteractionType().getOrdinal();
      final int interactionStage = msg.getHeader().getInteractionStage().getValue();

      boolean isAckStage = false;

      synchronized (this)
      {
        if (!receivedAck)
        {
          if ((interactionType == InteractionType._PROGRESS_INDEX)
                  && (interactionStage == MALProgressOperation._PROGRESS_ACK_STAGE))
          {
            isAckStage = true;
            receivedAck = true;
            if (msg.getHeader().getIsErrorMessage())
            {
              receivedResponse = true;
            }
          }
          else
          {
            logUnexpectedTransitionError(interactionType, interactionStage);
            throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
          }
        }
        else if ((!receivedResponse) && (interactionType == InteractionType._PROGRESS_INDEX)
                && ((interactionStage == MALProgressOperation._PROGRESS_UPDATE_STAGE)
                || (interactionStage == MALProgressOperation._PROGRESS_RESPONSE_STAGE)))
        {
          if (interactionStage == MALProgressOperation._PROGRESS_UPDATE_STAGE)
          {
            if (msg.getHeader().getIsErrorMessage())
            {
              receivedResponse = true;
            }
          }
          else
          {
            receivedResponse = true;
          }
        }
        else
        {
          logUnexpectedTransitionError(interactionType, interactionStage);
          throw new MALInteractionException(new MALStandardError(MALHelper.INCORRECT_STATE_ERROR_NUMBER, null));
        }
      }

      if (isAckStage)
      {
        try
        {
          if (syncOperation)
          {
            signalResponse(msg);
          }
          else
          {
            if (msg.getHeader().getIsErrorMessage())
            {
              listener.progressAckErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
            }
            else
            {
              listener.progressAckReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
            }
          }
        }
        catch (MALException ex)
        {
          // nothing we can do with this
          ex.printStackTrace();
        }
      }
      else
      {
        try
        {
          if (interactionStage == MALProgressOperation._PROGRESS_UPDATE_STAGE)
          {
            if (msg.getHeader().getIsErrorMessage())
            {
              listener.progressUpdateErrorReceived(msg.getHeader(),
                      (MALErrorBody) msg.getBody(), msg.getQoSProperties());
            }
            else
            {
              listener.progressUpdateReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
            }
          }
          else
          {
            if (msg.getHeader().getIsErrorMessage())
            {
              listener.progressResponseErrorReceived(msg.getHeader(),
                      (MALErrorBody) msg.getBody(), msg.getQoSProperties());
            }
            else
            {
              listener.progressResponseReceived(msg.getHeader(), msg.getBody(), msg.getQoSProperties());
            }
          }
        }
        catch (MALException ex)
        {
          // nothing we can do with this
          ex.printStackTrace();
        }
      }
    }

    @Override
    public synchronized boolean finished()
    {
      return receivedResponse;
    }
  }

  private static final class PubSubOperationHandler extends SubmitOperationHandler
  {
    public PubSubOperationHandler(final boolean syncOperation, final MALInteractionListener listener)
    {
      super(InteractionType._PUBSUB_INDEX, 0, syncOperation, listener);
    }

    @Override
    protected boolean checkStage(final int stage)
    {
      switch (stage)
      {
        case MALPubSubOperation._REGISTER_ACK_STAGE:
        case MALPubSubOperation._PUBLISH_REGISTER_ACK_STAGE:
        case MALPubSubOperation._DEREGISTER_ACK_STAGE:
        case MALPubSubOperation._PUBLISH_DEREGISTER_ACK_STAGE:
          return true;
        default:
          return true;
      }
    }

    @Override
    protected void informListener(final MALMessage msg) throws MALException
    {
      if (msg.getHeader().getIsErrorMessage())
      {
        listener.registerErrorReceived(msg.getHeader(), (MALErrorBody) msg.getBody(), msg.getQoSProperties());
      }
      else
      {
        if ((MALPubSubOperation._PUBLISH_REGISTER_ACK_STAGE == msg.getHeader().getInteractionStage().getValue())
                || (MALPubSubOperation._REGISTER_ACK_STAGE == msg.getHeader().getInteractionStage().getValue()))
        {
          listener.registerAckReceived(msg.getHeader(), msg.getQoSProperties());
        }
        else
        {
          listener.deregisterAckReceived(msg.getHeader(), msg.getQoSProperties());
        }
      }
    }
  }

  private static final class InteractionListenerPublishAdapter implements MALInteractionListener
  {
    private final MALPublishInteractionListener delegate;

    public InteractionListenerPublishAdapter(final MALPublishInteractionListener delegate)
    {
      this.delegate = delegate;
    }

    public void registerAckReceived(final MALMessageHeader header, final Map qosProperties)
            throws MALException
    {
      delegate.publishRegisterAckReceived(header, qosProperties);
    }

    public void registerErrorReceived(final MALMessageHeader header, final MALErrorBody body, final Map qosProperties)
            throws MALException
    {
      delegate.publishRegisterErrorReceived(header, body, qosProperties);
    }

    public void deregisterAckReceived(final MALMessageHeader header, final Map qosProperties)
            throws MALException
    {
      delegate.publishDeregisterAckReceived(header, qosProperties);
    }

    public void invokeAckErrorReceived(final MALMessageHeader header, final MALErrorBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void invokeAckReceived(final MALMessageHeader header, final MALMessageBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void invokeResponseErrorReceived(final MALMessageHeader header,
            final MALErrorBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void invokeResponseReceived(final MALMessageHeader header,
            final MALMessageBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void notifyErrorReceived(final MALMessageHeader header, final MALErrorBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void notifyReceived(final MALMessageHeader header, final MALNotifyBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void progressAckErrorReceived(final MALMessageHeader header,
            final MALErrorBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void progressAckReceived(final MALMessageHeader header, final MALMessageBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void progressResponseErrorReceived(final MALMessageHeader header,
            final MALErrorBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void progressResponseReceived(final MALMessageHeader header,
            final MALMessageBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void progressUpdateErrorReceived(final MALMessageHeader header,
            final MALErrorBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void progressUpdateReceived(final MALMessageHeader header,
            final MALMessageBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void requestErrorReceived(final MALMessageHeader header, final MALErrorBody body, final Map qosProperties)
            throws MALException
    {
    }

    public void requestResponseReceived(final MALMessageHeader header,
            final MALMessageBody body,
            final Map qosProperties)
            throws MALException
    {
    }

    public void submitAckReceived(final MALMessageHeader header, final Map qosProperties)
            throws MALException
    {
    }

    public void submitErrorReceived(final MALMessageHeader header, final MALErrorBody body, final Map qosProperties)
            throws MALException
    {
    }
  }
}
