package snapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
    
    public Date getSamplingTime() {
        return samplingTime;
    }

    public void setSamplingTime(Date samplingTime) {
        this.samplingTime = samplingTime;
    }

    public void addNode(SnapshotNode node) {
        nodes.add(node);
    }
    
    public List<SnapshotNode> getNodes() {
        return nodes;
    }

    public void addQueuedJob(SnapshotJob job) {
        queuedJobs.add(job);
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
}
