package timeline;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The state of a grid node which may change over time as the state of the grid moved through a timeline. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class GridNode {

    private static final Logger log = LoggerFactory.getLogger(GridNode.class);
    
    private String shortName;
    private GridJob[] slots = new GridJob[GridState.SLOTS_PER_NODE];

    public GridNode(String shortName) {
        this.shortName = shortName;
    }
    
    public String getShortName() {
        return shortName;
    }
    
    public List<Integer> assignJobToSlots(GridJob job) {
        List<Integer> indexes = new ArrayList<Integer>();
        int slotsLeft = job.getSlots();
        for(int s=0; s<slots.length && slotsLeft>0; s++) {
            if (slots[s]==null) {
                slots[s] = job;
                indexes.add(s);
                slotsLeft--;
                // If any slots were assigned, then lets consider the job on the node, and hope all slots were assigned.
                job.setNode(this);
            }
        }
        if (slotsLeft>0) {
            log.debug("Node state: {}",this);
            log.error("Node "+shortName+" cannot allocate "+slotsLeft+" slots for "+job.getFullJobId());
        }
        return indexes;
    }

    public void removeJob(GridJob stateJob) {
        for(int i=0; i<slots.length; i++) {
            if (slots[i]!=null && slots[i].getFullJobId().equals(stateJob.getFullJobId())) {
                log.trace("erasing "+slots[i].getFullJobId()+" from node "+getShortName());
                slots[i] = null;
            }
        }
    }
    
    public GridJob[] getSlots() {
        return slots;
    }
    
    public int getNumJobs() {
        int c = 0;
        for(GridJob job : slots) {
            if (job!=null) c++;
        }
        return c;
    }
    
    @Override
    public String toString() {
        StringBuilder slotStr = new StringBuilder();
        for(int i=0; i<slots.length; i++) {
            if (i>0) {
                slotStr.append(" ");
            }
            if (slots[i]==null) {
                slotStr.append("empty");
            }
            else {
                slotStr.append(slots[i].getFullJobId());
            }
        }
        return "GridNode [name=" + shortName+", ("+slotStr+")]";
    }
}
