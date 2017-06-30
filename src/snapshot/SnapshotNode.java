package snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a cluster snapshot.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class SnapshotNode {

    private String name;
    private String qtype;
    private List<SnapshotJob> jobs = new ArrayList<SnapshotJob>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQtype() {
        return qtype;
    }

    public void setQtype(String qtype) {
        this.qtype = qtype;
    }

    public void addJob(SnapshotJob job) {
        jobs.add(job);
    }

    public List<SnapshotJob> getJobs() {
        return jobs;
    }

    public String getShortName() {
        return name.substring(0, name.indexOf('.'));
    }

    @Override
    public String toString() {
        return "SnapshotNode [name=" + name + ", qtype=" + qtype + ",  numJobs=" + jobs.size() + "]";
    }
}
