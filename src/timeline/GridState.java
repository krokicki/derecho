package timeline;

import gui.GridConfig;
import gui.GridConfig.NodeConfiguration;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import snapshot.Snapshot;
import snapshot.SnapshotJob;
import snapshot.SnapshotNode;

/**
 * The entire state of the grid at any given time point. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class GridState {
    
    private static final Logger log = LoggerFactory.getLogger(GridState.class);
    
    private String name;
    private List<GridNode> nodes = new ArrayList<GridNode>();
    private List<GridJob> queuedJobs = new ArrayList<GridJob>();
    
    private Map<String,GridNode> nodeMap = new HashMap<String,GridNode>();
    private Map<String,GridJob> jobMap = new HashMap<String,GridJob>();
    private List<String> users = new ArrayList<String>();
    private Map<String, Integer> slotsUsedByUser = new HashMap<String, Integer>();

    public GridState(Snapshot snapshot, String name) {

        this.name = name;

    	GridConfig config = GridConfig.getInstance();
    	
        for(SnapshotNode ssNode : snapshot.getNodes()) {
        	
    		NodeConfiguration nodeConfig = config.getConfiguration(ssNode.getShortName());
    		int numSlots = nodeConfig.getNodeSet().getSlots();
        	
            GridNode node = new GridNode(ssNode.getShortName(), numSlots);
            nodes.add(node);
            for(SnapshotJob ssJob : ssNode.getJobs()) {
                GridJob job = new GridJob(ssJob);
                log.trace(name+" init - Adding running job {} to node {}",job,node.getShortName());
                jobMap.put(job.getFullJobId(), job);
                List<Integer> slots = node.assignJobToSlots(job);
                log.debug(name+" init - Assigned job {} to slots {}",job,slots);
            }
        }
        
        for(SnapshotJob ssJob : snapshot.getQueuedJobs()) {
            GridJob job = new GridJob(ssJob);
            log.debug(name+" init - Adding queued job {}",job);
            addQueuedJob(job);
        }
            
        init();
    }
    
    public int getNumRunningJobs() {
        return jobMap.size() - getNumQueuedJobs();
    }

    public int getNumQueuedJobs() {
        return queuedJobs.size();
    }
    
    public void removeJob(GridJob job) {
        if (job.getNode()!=null) {
            job.getNode().removeJob(job);
        }
        jobMap.remove(job.getFullJobId());
        queuedJobs.remove(job);
    }
    
    public void addQueuedJob(GridJob job) {
        queuedJobs.add(job);
        jobMap.put(job.getFullJobId(), job);
    }

    public void removeQueuedJob(GridJob job) {
        queuedJobs.remove(job);
    }
    
    public List<GridJob> getQueuedJobs() {
        return queuedJobs;
    }

    public GridNode getNodeByShortName(String shortName) {
        return nodeMap.get(shortName);
    }
    
    public GridJob getJobByFullId(String fullJobId) {
        return jobMap.get(fullJobId);
    }
    
    public Map<String, GridNode> getNodeMap() {
        return nodeMap;
    }

    public Map<String, GridJob> getJobMap() {
        return jobMap;
    }

    public List<String> getUsers() {
        return users;
    }

    public Map<String, Integer> getSlotsUsedByUser() {
        return slotsUsedByUser;
    }

    public void init() {
                
        for(GridNode node : nodes) {
            nodeMap.put(node.getShortName(),node);
            for(GridJob job : node.getSlots()) {
                if (job==null) continue;
                String user = job.getOwner();
                incrementSlots(user, job.getSlots());
            }
        }
        
        Collections.sort(users, new Comparator<String>() {
            @Override
            public int compare(String user1, String user2) {
                Integer slots1 = slotsUsedByUser.get(user1);
                Integer slots2 = slotsUsedByUser.get(user2);
                if (slots1==null && slots2 == null) return 0;
                if (slots1==null) return -1;
                if (slots2==null) return 1;
                return slots2.compareTo(slots1);
            }
        });
    }
    
    private void incrementSlots(String user, int slots) {
        if (!slotsUsedByUser.containsKey(user)) {
            slotsUsedByUser.put(user, slots);
            users.add(user);
        }
        else {
            slotsUsedByUser.put(user, slotsUsedByUser.get(user)+slots);
        }
    }

    public boolean applyEvent(GridEvent event) {
        
        String fullJobId = event.getJobId();
        GridJob stateJob = getJobByFullId(fullJobId);
        
        switch(event.getType()) {
        case SUB:
            try {
                SnapshotJob ssJob = event.getSnapshotJob();
                GridJob gridJob = new GridJob(ssJob);
                log.debug(name+" - {} - queued job {}",event.getOffset(),gridJob);
                addQueuedJob(gridJob);
            }
            catch (Exception e) {
                log.error(name+" - could not sub job {}",fullJobId,e);
                return false;
            }
            break;
        case START:
            try {
                if (stateJob==null) {
                    throw new IllegalStateException("Cannot start job which doesn't exist: "+fullJobId);
                }
                SnapshotJob snapshotJob = event.getSnapshotJob();
                if (snapshotJob==null) {
                    log.error(name+" - cannot start a null job, event={}",event.getCacheKey());
                }
                else {
                    SnapshotNode snapshotNode = snapshotJob.getNode();
                    if (snapshotNode==null) {
                        log.error(name+" - cannot start a job with a null node");   
                    }
                    else {
                        String nodeName = snapshotNode.getShortName();
                        GridNode stateNode = getNodeByShortName(nodeName);
                        if (stateNode==null) {
                            throw new IllegalStateException("No such node in the state: "+nodeName);
                        }
                        log.trace(name+" - starting job {} on node {}",stateJob,nodeName);
                        removeQueuedJob(stateJob);              
                        stateJob.setNode(stateNode);
                        stateJob.update(snapshotJob);
                        List<Integer> slots = stateNode.assignJobToSlots(stateJob);
                        incrementSlots(stateJob.getOwner(), stateJob.getSlots());
                        log.debug(name+" - {} - started job {}",event.getOffset(),stateJob);
                    }
                }
            }
            catch (Exception e) {
                log.error(name+" - could not start job {}",fullJobId,e);
                return false;
            }
            break;
        case END:
            try {
                if (stateJob==null) {
                    throw new IllegalStateException("Cannot end job which doesn't exist: "+fullJobId);
                }
                log.debug(name+" - {} - ended job {}",event.getOffset(),stateJob);
                incrementSlots(stateJob.getOwner(), -1*stateJob.getSlots());
                removeJob(stateJob);
            }
            catch (Exception e) {
                log.error(name+" - could not end job {}",fullJobId,e);
                return false;
            }
            break;
        }
        
        return true;
    }

    public void eraseJob(String fullJobId) {
        log.debug(name+" - erasing job {} from grid state",fullJobId);
        for(Iterator<GridJob> iterator = queuedJobs.iterator(); iterator.hasNext(); ) {
            GridJob gridJob = iterator.next();
            if (gridJob.getFullJobId().equals(fullJobId)) {
                iterator.remove();
            }
        }
        for(Iterator<Map.Entry<String,GridJob>> iterator = jobMap.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String,GridJob> entry = iterator.next();
            GridJob gridJob = entry.getValue();
            if (gridJob.getFullJobId().equals(fullJobId)) {
                GridNode gridNode = gridJob.getNode();
                if (gridNode!=null) {
                    gridNode.removeJob(gridJob);
                }
                iterator.remove();
            }
        }
    }   
    
//    public void printGridSummary() {
//        StringBuilder sb = new StringBuilder();
//        for(int j=0; j<gridWidth; j++) {
//            sb.append("\tu"+(j+1)); 
//        }
//        log.info(sb.toString());
//        sb = new StringBuilder();
//        for(int i=0; i<gridHeight; i++) {
//            sb.append("f"+i+"\t");
//            for(int j=0; j<gridWidth; j++) {
//                GridNode node = grid[j][i];
//                if (node==null) {
//                    sb.append("-\t");   
//                }
//                else {
//                    int totalSlots = 0;
//                    for(GridJob job : node.getSlots()) {
//                        if (job==null) continue;
//                        totalSlots += job.getSlots();
//                    }
//                    sb.append(totalSlots+"\t");
//                }
//            }
//            log.info(sb.toString());
//        }
//        
//        log.info("\nSlots per user:");
//        for(String user : users) {
//            Integer slots = slotsUsedByUser.get(user);
//            log.info(slots+"\t"+user);
//        }
//    }
//    
//    public void printGridState() {
//        log.info(""+name+" Grid State");
//        for(int i=0; i<gridHeight; i++) {
//            for(int j=0; j<gridWidth; j++) {
//                GridNode node = grid[j][i];
//                if (node!=null) {
//                    log.info(node.getShortName());
//                    int s = 0;
//                    for(GridJob job : node.getSlots()) {
//                        if (job==null) {
//                            log.info("    Slot "+s+": empty");  
//                        }
//                        else {
//                            log.info("    Slot "+s+":"+job.getFullJobId()+", "+job.getOwner()+" ("+job.getState()+")");
//                        }
//                        s++;
//                    }
//                }
//            }
//        }
//        log.info("Queued:");
//        for(GridJob job : queuedJobs) {
//            log.info("    "+job.getFullJobId()+", "+job.getOwner()+" ("+job.getState()+")");
//        }
//    }
    
    public void printDifferences(GridState otherState) {
        
        for(GridNode node : nodes) {
            GridNode otherNode = otherState.getNodeByShortName(node.getShortName());
            
            if (otherNode==null) {
                log.error(name+" - other state ("+otherState.getName()+") does not have node: {}",node.getShortName());
            }
            else {
                Set<String> jobs = new HashSet<String>();
                Set<String> otherJobs = new HashSet<String>();
                
                for(GridJob job : node.getSlots()) {
                    if (job!=null) {
                        jobs.add(job.getFullJobId());
                    }
                }
                for(GridJob job : otherNode.getSlots()) {
                    if (job!=null) {
                        otherJobs.add(job.getFullJobId());
                    }
                }
                
                if (!jobs.equals(otherJobs)) {
                    List<String> jobsStr = new ArrayList<String>(jobs);
                    List<String> otherJobsStr = new ArrayList<String>(otherJobs);
                    Collections.sort(jobsStr);
                    Collections.sort(otherJobsStr);
                    String diff = jobsStr+" <> "+otherJobsStr;
                    log.error(name+" - grid node {} differs: {}",node.getShortName(),diff);
                }
            }
        }
        
        for(GridNode otherNode : otherState.getNodeMap().values()) {
            GridNode node = otherState.getNodeByShortName(otherNode.getShortName());
            if (node==null) {
                log.error(name+" - state does not have node found in other state: {}",otherNode.getShortName());
            }
        }
        
        for(GridJob job : queuedJobs) {
            GridJob otherJob = otherState.getJobByFullId(job.getFullJobId());
            if (otherJob==null) {
                log.error(name+" - other state ("+otherState.getName()+") does not have job queued: {}",job.getFullJobId());
            }
            else if (!job.toString().equals(otherJob.toString())) {
                log.error(name+" - queued job is not the same: {}",job.toString()+" <> "+otherJob.toString());
            }
        }

        for(GridJob otherJob : otherState.getQueuedJobs()) {
            GridJob job = getJobByFullId(otherJob.getFullJobId());
            if (job==null) {
                log.error(name+" - state does not have job queued in other state ("+otherState.getName()+"): {}",otherJob.getFullJobId());
            }
        }
    }

    public String getName() {
        return name;
    }
}
