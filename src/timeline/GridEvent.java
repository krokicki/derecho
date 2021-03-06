package timeline;

import snapshot.SnapshotJob;

/**
 * A job event on the grid corresponding to a job being queued, started, or ended.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class GridEvent extends Event {

    public static enum EventType {
        SUB, START, END
    }

    private EventType type;
    private String fullJobId;
    private SnapshotJob ssJob;

    public GridEvent(EventType type, Long offset, String fullJobId) {
        super(offset);
        this.type = type;
        this.fullJobId = fullJobId;
    }

    public GridEvent(EventType type, Long offset, SnapshotJob ssJob) {
        super(offset);
        this.type = type;
        this.ssJob = ssJob;
        this.fullJobId = ssJob.getFullJobId();
    }

    public EventType getType() {
        return type;
    }

    public String getJobId() {
        return fullJobId;
    }

    public SnapshotJob getSnapshotJob() {
        return ssJob;
    }

    public void print() {
        StringBuffer buf = new StringBuffer();
        buf.append(getOffset());
        buf.append("\t");
        buf.append(type.toString());
        buf.append("\t");
        if (fullJobId != null) {
            buf.append("(jobId=");
            buf.append(fullJobId);
            buf.append(")");
        }
        System.out.println(buf.toString());
    }

    public String getCacheKey() {
        if (fullJobId == null) return type.toString();
        return type.toString() + "_" + fullJobId + "@" + getOffset();
    }

    @Override
    public String toString() {
        return "GridEvent[" + getCacheKey() + "]";
    }
}
