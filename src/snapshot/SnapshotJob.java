package snapshot;
import java.util.Date;

/**
 * A job in a cluster snapshot.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class SnapshotJob {
    
    private int jobId;
    private String name;
    private String state;
    private String owner;
    private boolean exclusive;
    private int slots;
    private String tasks;
    private Date subTime;
    private Date startTime;
    private SnapshotNode node;

    public String getFullJobId() {
        String s = jobId+"";
        if (tasks!=null) {
            s += "."+tasks;
        }
        return s;
    }
    
    public int getJobId() {
        return jobId;
    }
    
    public void setJobId(int jobId) {
        this.jobId = jobId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public boolean isExclusive() {
        return exclusive;
    }
    
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }
    
    public int getSlots() {
        return slots;
    }
    
    public void setSlots(int slots) {
        this.slots = slots;
    }
    
    public String getTasks() {
        return tasks;
    }

    public void setTasks(String tasks) {
        this.tasks = tasks;
    }

    public Date getSubTime() {
        return subTime;
    }
    
    public void setSubTime(Date subTime) {
        this.subTime = subTime;
    }
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    
    public SnapshotNode getNode() {
        return node;
    }

    public void setNode(SnapshotNode node) {
        this.node = node;
    }
    
    @Override
    public String toString() {
        String nodeName = node==null?"":node.getShortName();
        return "SnapshotJob[" + getFullJobId() + ", " + nodeName + ", "
                + owner + ", slots=" + slots + ", state=" + state + "]";
    }
}
