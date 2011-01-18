package se.sandos.android.delayed.server.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class Main
{
    final static Logger log = LoggerFactory.getLogger(Main.class);

    private TrainFetcher fetcher = null;
    private IoAcceptor acceptor = null;
    private DelayedServerHandler handler = null;
    
    public void setDelayedServerHandler(DelayedServerHandler handler)
    {
        this.handler = handler;
    }
    
    public void setFetcher(TrainFetcher tf)
    {
        fetcher = tf;
    }

    public static void main(String[] args) throws IOException
    {
        //log.info("Logging");
        String[] l = new String[]{"config.xml"};
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext(l);
        
        Main m = appContext.getBean(Main.class);
        m.run();
    }
        
    public void run() throws IOException
    {
        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        acceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("UTF-8"))));
        
        acceptor.getFilterChain().addLast(
                "threadPool",
                new ExecutorFilter(Executors.newCachedThreadPool()));
        acceptor.setHandler(  handler );
        
        acceptor.getSessionConfig().setIdleTime( IdleStatus.BOTH_IDLE, 10 );
        acceptor.bind( new InetSocketAddress(8081) );
        
        fetcher.setBroadcaster(handler);
        new Thread(fetcher).start();

    }
}
