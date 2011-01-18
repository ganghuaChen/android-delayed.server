package se.sandos.android.delayed.server.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sandos.android.delayed.rpc.Interest;
import se.sandos.android.delayed.rpc.RegisterListenerMessage;
import se.sandos.android.delayed.rpc.TrainPushEvent;

import com.google.gson.Gson;

public class DelayedServerHandler extends IoHandlerAdapter
{
    private static final String LISTENED_IDS = "listenedIds";

    Logger log = LoggerFactory.getLogger(DelayedServerHandler.class);

    final static Gson gs = new Gson();
    
    private final Set<IoSession> sessions = Collections
             .synchronizedSet(new HashSet<IoSession>());
    
    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception
    {
        log.warn("Problem with MINA", cause);
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception
    {
        Gson gs = new Gson();
            
        if(message instanceof String)
        {
            RegisterListenerMessage fromJson = gs.fromJson((String) message, RegisterListenerMessage.class);
            log.warn("Msg: {}", fromJson);
            
            session.setAttribute(LISTENED_IDS, fromJson.getInterests());
            
            sessions.add(session);
        }
        else
        {
            log.warn("Non-string message received!");
        }
    }
    
    @Override
    public void sessionClosed(IoSession session) throws Exception
    {
        sessions.remove(session);
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception
    {
        log.warn("Sent message");
    }
    
    /**
     * Push data to clients. Sends to clients with identical Interests.
     * @param ev 
     * @param trainId
     * @param stationName
     */
    @SuppressWarnings("unchecked")
    public void broadcast(TrainPushEvent ev, String trainId, String stationName)
    {
        //Only support this right now
        if(trainId == null || stationName != null)
        {
            return;
        }
        
        Interest compare = new Interest();
        compare.trainId = trainId;
        
        synchronized (sessions)
        {
            for (IoSession session : sessions)
            {
                Set<Interest> s = (Set<Interest>) session.getAttribute(LISTENED_IDS);
                if(true || s.contains(compare) && session.isConnected())
                {
                     session.write(gs.toJson(ev));
                }
            }
        }
    }
}
