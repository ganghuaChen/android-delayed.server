package se.sandos.android.delayed.server.impl;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sandos.android.delayed.rpc.TrainEvent;
import se.sandos.android.delayed.rpc.TrainPushEvent;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlBold;
import com.gargoylesoftware.htmlunit.html.HtmlBreak;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlFont;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.base.Joiner;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

/**
 * Polls the mobile HTML pages from Trafikverket
 * 
 * Has adaptive delay for page-fetches to ease the load on the servers.
 * @author John Bäckstrand
 *
 */
public class TrainFetcher implements Runnable
{
    Logger log = LoggerFactory.getLogger(TrainFetcher.class);

    private Set<String> uris;
    private DelayedServerHandler dsh = null;
    
    public void setUris(Set<String> uris)
    {
        this.uris = uris;
    }
    
    public void setBroadcaster(DelayedServerHandler handler)
    {
        dsh = handler;
    }
    
    //Minimal delay that is allowed between two full page fetches. 
    private int minimalDelay = 4000;

    public int getMinimalDelay()
    {
        return minimalDelay;
    }

    public void setMinimalDelay(int minimalDelay)
    {
        this.minimalDelay = minimalDelay;
    }

    @Override
    public void run()
    {
        log.warn("Entering run");
        
        //Last time that we even tried to reach a page
        long lastTryTime = -1;
        long lastFetchTime = -1;

        Pattern matchTime = Pattern.compile("^(\\d\\d:\\d\\d) till (.*).Tåg nr (\\d+).?Spår (\\d+).*$", Pattern.DOTALL);
        Pattern matchDelayed = Pattern.compile("^(\\d\\d:\\d\\d) till (.*).Tåg nr (\\d+).(.*)$", Pattern.DOTALL);
        Pattern stationNameDeparture = Pattern.compile("^(.*).Avgående tåg$", Pattern.DOTALL);
        
        List<String> workOrder = new ArrayList<String>();
        workOrder.addAll(uris);
        
        Map<String, Map<String, TrainEvent>> knownData = new HashMap<String, Map<String, TrainEvent>>();
        while(true)
        {
            Map<String, TrainEvent> currentlyFetched = new HashMap<String, TrainEvent>();
            
            try
            {
                long now = System.currentTimeMillis();
                long sleeptime = 0;
                if(lastTryTime != -1) {
                    long diff = now - lastTryTime;
                    
                    if(diff < minimalDelay)
                    {
                        sleeptime = minimalDelay - Math.max(0, diff);
                    }
                }   
                
                if(sleeptime < minimalDelay)
                {
                    sleeptime = minimalDelay;
                }
                
                if(sleeptime > 0)
                {
                    log.warn("Sleeping {}, last fetch took {}", sleeptime, lastFetchTime);
                    Thread.sleep(sleeptime);
                }
                
                lastTryTime = System.currentTimeMillis();
                final WebClient webClient = new WebClient();
                String work = getWork(workOrder);
                work = work.replace("ä", "%C3%A4");
                String stationName = null;
                log.warn("before got past " + work);
                final HtmlPage page = webClient.getPage(work);
    
                log.warn("got past");
                
                long after = System.currentTimeMillis();
                lastFetchTime = after - lastTryTime;

                Iterable<HtmlElement> allHtmlChildElements = page.getAllHtmlChildElements();
                for (HtmlElement htmlElement : allHtmlChildElements)
                {
                    
                    if(htmlElement instanceof HtmlFont)
                    {
                        String text = htmlElement.getTextContent();

                        Matcher m = matchTime.matcher(text);
                        if(!m.matches()) {
                            Matcher o = matchDelayed.matcher(text);
                            if(!o.matches())
                            {
                                Matcher p = stationNameDeparture.matcher(text);
                                if(!p.matches())
                                {
//                                    log.warn("XXXXXXYYY >{}<", text);
                                }
                                else
                                {
                                    stationName = p.group(1);
                                }
                            }
                            else
                            {
                                String tid = o.group(1);
                                String to = o.group(2);
                                String nr = o.group(3);
                                String extra = o.group(4);
                                
                                TrainEvent te = new TrainEvent(null, tid, to, nr, stationName, null, extra);
                                currentlyFetched.put(nr, te);
                                
//                                log.warn("\nTID: {}\nTILL: {}\nNR: {}\nINFO: {}", new Object[]{tid, to, nr, extra});
                                
                            }
                        }
                        else
                        {
                            String tid = m.group(1);
                            String to = m.group(2);
                            String nr = m.group(3);
                            String track = m.group(4);

                            TrainEvent te = new TrainEvent(null, tid, to, nr, stationName, track, null);
                            currentlyFetched.put(nr, te);
                            
//                            log.warn("\nTID: {}\nTILL: {}\nNR: {}\nSPÅR: {}", new Object[]{tid, to, nr, track});
                        }
                        
//                        log.warn("{}\n{}", new Object[]{
//                                htmlElement, 
//                                htmlElement.getTextContent()});
                     
                        
                        Iterable<HtmlElement> childElements = htmlElement.getChildElements();
                        for (HtmlElement child : childElements)
                        {
                            if(child instanceof HtmlBreak || child instanceof HtmlBold)
                            {
                                continue;
                            }
                            
//                            if(child instanceof HtmlAnchor)
//                            {
//                                log.warn("c {} {}", child, child.getTextContent());
//                            }
                        
                        }
                    }
                    
                }
                
                webClient.closeAllWindows();
                
                if(knownData.containsKey(work))
                {
                    //compare
                    Map<String, TrainEvent> known = knownData.get(work);
                    if(!known.equals(currentlyFetched))
                    {
//                        log.warn("Aaaa");
                    }
                    
                    MapDifference<String, TrainEvent> diff = Maps.difference(known, currentlyFetched);
                    if(!diff.areEqual())
                    {
                        Set<TrainEvent> added = new HashSet<TrainEvent>();
                        Set<TrainEvent> removed = new HashSet<TrainEvent>();
                        Set<TrainEvent> changed = new HashSet<TrainEvent>();
                        
                        for (Iterator<TrainEvent> iterator = diff.entriesOnlyOnLeft().values().iterator(); iterator.hasNext();)
                        {
                            TrainEvent t = (TrainEvent) iterator.next();
                            removed.add(t);
                            
                            TrainPushEvent tpe = new TrainPushEvent(t, "removed");
                            dsh.broadcast(tpe, tpe.id, null);
                        }
                        
                        for (Iterator<TrainEvent> iterator = diff.entriesOnlyOnRight().values().iterator(); iterator.hasNext();)
                        {
                            TrainEvent t = (TrainEvent) iterator.next();
                            added.add(t);

                            TrainPushEvent tpe = new TrainPushEvent(t, "added");
                            dsh.broadcast(tpe, tpe.id, null);
                        }
                        
                        for (Iterator<ValueDifference<TrainEvent>> iterator = diff.entriesDiffering().values().iterator(); iterator.hasNext();)
                            
                        {
                            ValueDifference<TrainEvent> d = (ValueDifference<TrainEvent>) iterator.next();
                            TrainEvent newVal = d.rightValue();
                            changed.add(newVal);
                            
                            TrainPushEvent tpe = new TrainPushEvent(newVal, "changed");
                            dsh.broadcast(tpe, tpe.id, null);
                        }
                        
                        Joiner j = Joiner.on("\n").skipNulls();
                        
                        if(changed.size() != 0)
                        {
                            log.warn("Changed: {}", j.join(changed));
                        }

                        if(added.size() != 0)
                        {
                            log.warn("Added: {}", j.join(added));
                        }
                        
                        if(removed.size() != 0)
                        {
                            log.warn("Removed: {}", j.join(removed));
                        }
                    }
                }
                
                knownData.put(work, new HashMap<String, TrainEvent>(currentlyFetched));
            }
            catch (Exception e)
            {
                log.warn("Problem", e);
            }
            catch(Error ee)
            {
                log.warn("Runtime Problem", ee);
                throw ee;
            }
           
            
        }
    }

    private String getWork(List<String> workOrder)
    {
        if(0 == workOrder.size())
        {
            workOrder.addAll(uris);
        }
        
        return workOrder.remove(0);
    }
}
