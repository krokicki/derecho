package timeline;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import snapshot.SnapshotJob;

/**
 * The state of a grid job which may change over time as the state of the grid moved through a timeline. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class GridJob {

    private static final Logger log = LoggerFactory.getLogger(GridJob.class);

    private int jobId;
    private String name;
    private String state;
    private String owner;
    private boolean exclusive;
    private int slots;
    private String tasks;
    private Date subTime;
    private Date startTime;
    private GridNode node;

    public GridJob(SnapshotJob job) {
        this.jobId = job.getJobId();
        this.name = job.getName();
        this.state = job.getState();
        this.owner = job.getOwner();
        this.exclusive = job.isExclusive();
        this.slots = job.getSlots();
        this.tasks = job.getTasks();
        this.subTime = job.getSubTime();
        this.startTime = job.getStartTime();
    }

    public int getJobId() {
        return jobId;
    }

    public String getFullJobId() {
        StringBuilder s = new StringBuilder();
        s.append(jobId);
        if (tasks != null) {
            s.append(".").append(tasks);
        }
        return s.toString();
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getOwner() {
        return owner;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public int getSlots() {
        return slots;
    }

    public String getTasks() {
        return tasks;
    }

    public Date getSubTime() {
        return subTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public GridNode getNode() {
        return node;
    }

    public void setNode(GridNode node) {
        this.node = node;
    }

    public boolean update(SnapshotJob job) {

        if (job.getJobId() != jobId) throw new IllegalStateException("Attempt to update job " + jobId + " with " + job.getJobId());

        boolean changed = false;

        if (job.getName() != null && !job.getName().equals(name)) {
            log.trace("Updading name from {} to {}", name, job.getName());
            this.name = job.getName();
            changed = true;
        }

        if (job.getState() != null && !job.getState().equals(state)) {
            log.trace("Updading state from {} to {}", state, job.getState());
            this.state = job.getState();
            changed = true;
        }

        if (job.getOwner() != null && !job.getOwner().equals(owner)) {
            log.trace("Updading owner from {} to {}", owner, job.getOwner());
            this.owner = job.getOwner();
            changed = true;
        }

        if (exclusive != job.isExclusive()) {
            log.trace("Updading exclusive from {} to {}", exclusive, job.isExclusive());
            this.exclusive = job.isExclusive();
            changed = true;
        }

        if (job.getSlots() > 0 && slots != job.getSlots()) {
            log.trace("Updading slots from {} to {}", slots, job.getSlots());
            this.slots = job.getSlots();
            changed = true;
        }

        if (job.getTasks() != null && !job.getTasks().equals(tasks)) {
            log.trace("Updading tasks from {} to {}", tasks, job.getTasks());
            this.tasks = job.getTasks();
            changed = true;
        }

        if (job.getSubTime() != null && !job.getSubTime().equals(subTime)) {
            log.trace("Updading subTime from {} to {}", subTime, job.getSubTime());
            this.subTime = job.getSubTime();
            changed = true;
        }

        if (job.getStartTime() != null && !job.getStartTime().equals(startTime)) {
            log.trace("Updading startTime from {} to {}", startTime, job.getStartTime());
            this.startTime = job.getStartTime();
            changed = true;
        }

        return changed;
    }

    @Override
    public String toString() {
        String nodeName = node == null ? "" : node.getShortName();
        return "GridJob[" + getFullJobId() + ", " + nodeName + ", "
                + owner + ", slots=" + slots + ", state=" + state + "]";
    }
}
