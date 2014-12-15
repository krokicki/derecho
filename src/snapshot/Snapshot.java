package snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A snapshot of a cluster given by qstat (for SGE clusters) or a similar tool. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class Snapshot {

    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);
    
    private Date samplingTime;
    private List<SnapshotNode> nodes = new ArrayList<SnapshotNode>();
    private List<SnapshotJob> queuedJobs = new ArrayList<SnapshotJob>();
    private List<SnapshotNode> ordered;
    private Map<Integer,Date> parallelJobStarts = new HashMap<Integer,Date>();
    
    public Snapshot(Date samplingTime) {
        this.samplingTime = samplingTime;
    }

    public void addNode(SnapshotNode node) {
        nodes.add(node);
    }

    public void addQueuedJob(SnapshotJob job) {
        queuedJobs.add(job);
    }
    
    /**
     * This must be called after adding all the data to this snapshot using addNode() and addQueuedJob().
     */
    public void init() {

        // For parallel jobs, they might start with a 20 slot job on one node and then spread to 20 exclusive one slot 
        // jobs when they start. We need to detect that case and add 'tasks' indexes to the running jobs so that they 
        // are treated as separate jobs.
        Map<String,AtomicInteger> ssJobCount = new HashMap<String,AtomicInteger>();
        Map<String,SnapshotJob> ssJobMap = new HashMap<String,SnapshotJob>();
        for(SnapshotNode node : getNodes()) {          
            for(SnapshotJob ssJob : node.getJobs()) {
                ssJobMap.put(ssJob.getFullJobId(), ssJob);
                // Only worry about jobs without task numbers
                if (ssJob.getTasks()!=null && !"".equals(ssJob.getTasks())) continue;
                AtomicInteger count = ssJobCount.get(ssJob.getFullJobId());
                if (count == null) {
                    count = new AtomicInteger(1);
                    ssJobCount.put(ssJob.getFullJobId(), count);    
                }
                else {
                    count.incrementAndGet();
                }
            }
        }
        for(String fullJobId : ssJobCount.keySet()) {
            AtomicInteger count = ssJobCount.get(fullJobId);
            if (count.get()>1) {
                log.trace("Parallel job "+fullJobId+" detected. Adding task numbers.");
                // More than one instance of this job, give it task numbers
                int index = 1;
                for(SnapshotNode node : getOrderedNodes()) {          
                    for(SnapshotJob ssJob : node.getJobs()) {
                        if (ssJob.getFullJobId().equals(fullJobId)) {
                            parallelJobStarts.put(ssJob.getJobId(), ssJob.getStartTime());
                            ssJob.setTasks(""+index);
                            index++;
                        }
                    }
                }
            }
        }
    }
    
    public Date getSamplingTime() {
        return samplingTime;
    }
    
    public List<SnapshotNode> getNodes() {
        return nodes;
    }

    public List<SnapshotNode> getOrderedNodes() {
        if (ordered==null) {
            ordered = new ArrayList<SnapshotNode>(nodes);
            Collections.sort(ordered, new Comparator<SnapshotNode>() {
                @Override
                public int compare(SnapshotNode o1, SnapshotNode o2) {
                    return o1.getShortName().compareTo(o2.getShortName());
                }
            });
        }
        return ordered;
    }

    public List<SnapshotJob> getQueuedJobs() {
        return queuedJobs;
    }

    public void eraseJob(String fullJobId) {
        log.debug("Erasing job {} from snapshot {}",fullJobId,samplingTime);
        for(SnapshotNode node : nodes) {
            for(Iterator<SnapshotJob> iterator = node.getJobs().iterator(); iterator.hasNext(); ) { 
                SnapshotJob job = iterator.next();
                if (job.getFullJobId().equals(fullJobId)) {
                    iterator.remove();
                }
            }
        }
        for(Iterator<SnapshotJob> iterator = queuedJobs.iterator(); iterator.hasNext(); ) {
            SnapshotJob job = iterator.next();
            if (job.getFullJobId().equals(fullJobId)) {
                iterator.remove();
            }
        }
    }   

    public SnapshotJob getJob(String fullJobId) {
        for(SnapshotNode node : nodes) {
            for(Iterator<SnapshotJob> iterator = node.getJobs().iterator(); iterator.hasNext(); ) { 
                SnapshotJob job = iterator.next();
                if (job.getFullJobId().equals(fullJobId)) {
                    return job;
                }
            }
        }
        for(Iterator<SnapshotJob> iterator = queuedJobs.iterator(); iterator.hasNext(); ) {
            SnapshotJob job = iterator.next();
            if (job.getFullJobId().equals(fullJobId)) {
                return job;
            }
        }
        return null;
    }

    public Map<Integer, Date> getParallelJobStarts() {
        return parallelJobStarts;
    }   
}
