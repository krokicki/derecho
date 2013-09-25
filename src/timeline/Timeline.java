package timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import snapshot.Snapshot;
import snapshot.SnapshotJob;
import snapshot.SnapshotNode;
import timeline.GridEvent.EventType;
import util.LRUCache;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

/**
 * A moving window timeline of events on a grid. Holds some maximum number of snapshots and discards old events as it 
 * gets new ones, so as to not run out of memory. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class Timeline {
    
    private static final Logger log = LoggerFactory.getLogger(Timeline.class);
    
    public static final int MAX_NUM_SNAPSHOTS = 100;
    public static final int MIN_SNAPSHOT_RESOLUTION_SECS = 300; // 5 mins
    public static final long LIVE_LAG_MS = 5000; // 5 seconds       
    
    // Loaded timeline 
    private Snapshot penultimateSnapshot;
    private Snapshot ultimateSnapshot;
    private LinkedBlockingDeque<Snapshot> snapshots = new LinkedBlockingDeque<Snapshot>();
    private ConcurrentSkipListMap<Long,List<Event>> eventMap = new ConcurrentSkipListMap<Long,List<Event>>();
    private Date firstSnapshotDate;
    private Date lastSnapshotDate;
    
    // Offsets mapped to various time series
    private Map<Long,Integer> numRunningJobsMap = new ConcurrentSkipListMap<Long,Integer>();
    private Map<Long,Integer> numQueuedJobsMap = new ConcurrentSkipListMap<Long,Integer>();
    
    // State machine for loading
    private GridState loadState;
    private ConcurrentSkipListMap<Long,List<Event>> snapshotEventMap = new ConcurrentSkipListMap<Long,List<Event>>();
    private LRUCache<String,Long> eventCache = new LRUCache<String,Long>(100000);
    private Integer numRunningJobs = null;
    private Integer numQueuedJobs = null;
    private long prevSnapshotOffset = 0;
            
    public synchronized void addSnapshot(Snapshot snapshot) {
        
        if (snapshots.size()>=MAX_NUM_SNAPSHOTS) {
            log.info("Removing first snapshot to keep total at "+MAX_NUM_SNAPSHOTS);
            snapshots.pop();
            Snapshot newFirst = snapshots.peek();
            long snapshotOffset = getOffset(newFirst.getSamplingTime());
            trimIterator(eventMap.keySet().iterator(), snapshotOffset);
            trimIterator(numRunningJobsMap.keySet().iterator(), snapshotOffset);
            trimIterator(numQueuedJobsMap.keySet().iterator(), snapshotOffset);
        }
        
        this.snapshotEventMap.clear();
        this.penultimateSnapshot = ultimateSnapshot;
        this.ultimateSnapshot = snapshot;
        
        if (firstSnapshotDate==null) {
            synchronized (this) {
                this.firstSnapshotDate = snapshot.getSamplingTime();
                long snapshotOffset = getOffset(snapshot.getSamplingTime());
                this.loadState = new GridState(snapshot,"loadState");
                snapshots.add(snapshot);
                setNumRunningJobs(0,loadState.getNumRunningJobs());
                setNumQueuedJobs(0,loadState.getNumQueuedJobs());
                log.info("using snapshot {} as the basis",snapshotOffset);
            }
            return;
        }
        
        this.lastSnapshotDate = snapshot.getSamplingTime();
        
        long snapshotOffset = getOffset(snapshot.getSamplingTime());
        
        long prevKeptSnapshotOffset = getOffset(snapshots.peekLast().getSamplingTime());
        if (snapshotOffset-prevKeptSnapshotOffset > MIN_SNAPSHOT_RESOLUTION_SECS*1000) {
            snapshots.add(snapshot);
        }
        
        log.debug("---------------------------------------------------------------------");
        log.info("Adding snapshot with offset={}",snapshotOffset);

        // Compute a set of relevant job ids in this snapshot
        Set<String> ssJobIds = new HashSet<String>();
        for(SnapshotNode node : snapshot.getNodes()) {          
            for(SnapshotJob ssJob : node.getJobs()) {
                ssJobIds.add(ssJob.getFullJobId());
            }
        }
        for(SnapshotJob ssJob : snapshot.getQueuedJobs()) {
            ssJobIds.add(ssJob.getFullJobId());
        }
        
        // Check all known jobs, and generate delete events for the ones that are
        // no longer relevant. 
        List<String> jobsToErase = new ArrayList<String>();
        for(GridJob stateJob : loadState.getJobMap().values()) {
            if (!ssJobIds.contains(stateJob.getFullJobId())) { 
                long endOffset = prevSnapshotOffset+1;
                if (prevSnapshotOffset==0) {
                    // Job ends before the timeline starts, so let's delete it entirely
                    log.trace("    "+stateJob+" (known) ended at "+endOffset+" (event occurs before timeline)");
                    jobsToErase.add(stateJob.getFullJobId());
                }
                else {
                    boolean e1 = addEvent(new GridEvent(EventType.END, endOffset, stateJob.getFullJobId()));
                    if (e1) {
                        log.trace("    "+stateJob+" (known) ended at "+endOffset);
                    }
                }
            }
        }       
        
        // Note: have to remove it from the run state as well, if we're running already. Oy!
        for(String fullJobId : jobsToErase) {
            for(Snapshot s : snapshots) {
                s.eraseJob(fullJobId);
            }
            loadState.eraseJob(fullJobId);
        }
        
        for(SnapshotNode node : snapshot.getNodes()) {          
            for(SnapshotJob ssJob : node.getJobs()) {
                
                GridJob stateJob = loadState.getJobByFullId(ssJob.getFullJobId());
                if (stateJob==null) {
                    // We're seeing this job for the first time
                    if (ssJob.getStartTime()!=null) {
                        long startOffset = getOffset(ssJob.getStartTime());
                        // It's already been started, so that means we missed the queuing
                        long queueOffset = startOffset;
                        if (startOffset<prevSnapshotOffset+1) startOffset=prevSnapshotOffset+1;
                        if (queueOffset<prevSnapshotOffset+1) queueOffset=prevSnapshotOffset+1;
                        if (startOffset>snapshotOffset) startOffset=snapshotOffset;
                        if (queueOffset>snapshotOffset) queueOffset=snapshotOffset;
                        
                        boolean e1 = addEvent(new GridEvent(EventType.SUB, queueOffset, ssJob));
                        boolean e2 = addEvent(new GridEvent(EventType.START, startOffset, ssJob));  
                        if (e1) {
                            log.trace("    "+ssJob+" (new) subbed at "+queueOffset);    
                        }
                        if (e2) {
                            log.trace("    "+ssJob+" (new) started at "+startOffset);
                        }
                    }
                    else {
                        log.error("Job running on a node does not have a start time: "+ssJob);
                    }
                }
                else {
                    // We already know about this job
                    if (stateJob.getStartTime()==null && ssJob.getStartTime()!=null) {
                        // Job just started
                        long startOffset = getOffset(ssJob.getStartTime());
                        if (startOffset<prevSnapshotOffset+1) startOffset=prevSnapshotOffset+1;
                        if (startOffset>snapshotOffset) startOffset=snapshotOffset;
                        boolean e1 = addEvent(new GridEvent(EventType.START, startOffset, ssJob));
                        if (e1) {
                            log.trace("    "+ssJob+" (known) started at "+startOffset); 
                        }
                    }
                }
            }
        }
        
        for(SnapshotJob ssJob : snapshot.getQueuedJobs()) {

            GridJob stateJob = loadState.getJobByFullId(ssJob.getFullJobId());
            if (stateJob!=null) {
                // TODO: this should happen as an event
                if (stateJob.update(ssJob)) {
                    log.debug("Job's internal state was updated: {}",ssJob.getFullJobId());
                }
            }
            else {
                // New job we've never seen before. 
                stateJob = new GridJob(ssJob);
                long subOffset = getOffset(stateJob.getSubTime()==null?stateJob.getStartTime():stateJob.getSubTime());
                // Submission was before this snapshot's range, so move it into range.
                if (subOffset<prevSnapshotOffset+1) subOffset=prevSnapshotOffset+1;
                if (subOffset>snapshotOffset) subOffset=snapshotOffset;
                boolean e1 = addEvent(new GridEvent(EventType.SUB, subOffset, ssJob));
                if (e1) {
                    log.trace("    "+ssJob+" (new) subbed at "+subOffset);  
                }
            }
        }
        
        addEvent(new SnapshotEvent(snapshotOffset));
        
        // Apply the events to the state
        synchronized(this) {
            
            int errorsDetected = 0;
            log.debug("Applying {} events",snapshotEventMap.size());
            for(Long offset : snapshotEventMap.keySet()) {
                for(Event event : snapshotEventMap.get(offset)) {
                	if (event instanceof GridEvent) {
                		GridEvent gridEvent = (GridEvent)event;
                        log.debug("Apply event: {}",gridEvent);
                        if (!loadState.applyEvent(gridEvent)) {
                            errorsDetected++;
                            log.error("Error applying event: {}",gridEvent);
                        }
                        setNumRunningJobs(event.getOffset(),loadState.getNumRunningJobs());
                        setNumQueuedJobs(event.getOffset(),loadState.getNumQueuedJobs());
                        prevSnapshotOffset = event.getOffset();
                	}
                	 
                }
            }

            if (errorsDetected>0) {
                log.error("{} errors occured during event processing",errorsDetected);
                
//              printEventMap();
                
                GridState snapshotState = new GridState(snapshot,"Snapshot");
                
//              loadState.printGridState();
//              snapshotState.printGridState();
                
                loadState.printDifferences(snapshotState);
            }
        }
        
        
        printEventMap();
//      
//      GridState snapshotState = new GridState(snapshot,"Snapshot");
//      
//      loadState.printGridState();
//      snapshotState.printGridState();
//      
//      loadState.printDifferences(snapshotState);
        
        prevSnapshotOffset = snapshotOffset;
    }
    
    private synchronized boolean addEvent(Event event) {
    	if (event instanceof GridEvent) {
    		GridEvent gridEvent = (GridEvent)event;
	        if (eventCache.containsKey(gridEvent.getCacheKey())) {
	            return false;
	        }
	        eventCache.put(gridEvent.getCacheKey(), event.getOffset());
    	}
        
        List<Event> events = eventMap.get(event.getOffset());
        if (events==null) {
            events = new ArrayList<Event>();
            eventMap.put(event.getOffset(), events);
        }
        events.add(event);
        
        List<Event> snapshotEvents = snapshotEventMap.get(event.getOffset());
        if (snapshotEvents==null) {
            snapshotEvents = Collections.synchronizedList(new ArrayList<Event>());
            snapshotEventMap.put(event.getOffset(), snapshotEvents);
        }
        snapshotEvents.add(event);
        return true;
    }
    
    private void setNumRunningJobs(long offset, int numRunningJobs) {
        if (this.numRunningJobs == null || this.numRunningJobs != numRunningJobs) {
            numRunningJobsMap.put(offset, numRunningJobs);
        }
        this.numRunningJobs = numRunningJobs;
    }
    
    private void setNumQueuedJobs(long offset, int numQueuedJobs) {
        if (this.numQueuedJobs == null || this.numQueuedJobs != numQueuedJobs) {
            numQueuedJobsMap.put(offset, numQueuedJobs);
        }
        this.numQueuedJobs = numQueuedJobs;
    }
    
    public Map<Long, Integer> getNumRunningJobsMap() {
        return numRunningJobsMap;
    }

    public Map<Long, Integer> getNumQueuedJobsMap() {
        return numQueuedJobsMap;
    }

    public synchronized boolean isReady() {
        return firstSnapshotDate != null;
    }
    
    public synchronized long getOffset(Date date) {
        return (date.getTime() - firstSnapshotDate.getTime());
    }

    public Date getBaselineDate() {
        return firstSnapshotDate;
    }
    
    public synchronized long getLength() {
        if (lastSnapshotDate==null) return 0;
        Snapshot firstSnapshot = snapshots.peek();
        if (firstSnapshot==null) return 0;
        long lastOffset = eventMap.isEmpty()?0:eventMap.lastKey();
        long length1 = lastOffset - getOffset(firstSnapshot.getSamplingTime());
        long length2 = (lastSnapshotDate.getTime() - firstSnapshot.getSamplingTime().getTime());
        return Math.max(length1, length2);
    }

    public synchronized long getFirstOffset() {
        Snapshot firstSnapshot = snapshots.peek();
        if (firstSnapshot==null) return 0;
        return getOffset(firstSnapshot.getSamplingTime());
    }
    
    public synchronized long getLiveOffset() {
        if (penultimateSnapshot==null) return 0;
        if (ultimateSnapshot==null) return 0;
        long lastOffset = getOffset(penultimateSnapshot.getSamplingTime());
        long snapshotInterval = getOffset(ultimateSnapshot.getSamplingTime()) - lastOffset;
        return lastOffset - snapshotInterval - LIVE_LAG_MS;
    }
    
    public long getLastOffset() {
        return getFirstOffset()+getLength();
    }
    
    public synchronized SortedMap<Long,List<Event>> getEvents() {
        return ImmutableSortedMap.copyOf(eventMap);
    }
    
    public synchronized List<Snapshot> getSnapshots() {
        return ImmutableList.copyOf(snapshots);
    }

    public synchronized Snapshot getLastLoadedSnapshot() {
        return ultimateSnapshot;
    }
    
    private void trimIterator(Iterator<Long> iterator, long firstOffset) {
        while(iterator.hasNext()) {
            if (iterator.next()<firstOffset) {
                iterator.remove();
            }
        }
    }
    
    public void printEventMap() {
        log.info("Current Timeline:");
        for(Long offset : eventMap.keySet()) {
            List<Event> events = eventMap.get(offset);
            for(Event event : events) {
            	if (event instanceof GridEvent) {
            		GridEvent gridEvent = (GridEvent)event;
                    log.info(padRight(""+event.getOffset(), 10)+" "+gridEvent.getCacheKey());
            	}
            	else if (event instanceof SnapshotEvent) {
                    log.info(padRight(""+event.getOffset(), 10)+" ---SNAPSHOT---");	
            	}
            	else {
            		log.error("Unknown event class: {}",event.getClass().getName());
            	}
            }
        }
        
	}

	public static String padRight(String s, int n) {
		return String.format("%1$-" + n + "s", s);
	}

	public static String padLeft(String s, int n) {
		return String.format("%1$" + n + "s", s);
	}
}
