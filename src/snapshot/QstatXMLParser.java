package snapshot;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import timeline.GridState;

/**
 * Parser for qstat output created with a command like this:
 * <pre>qstat -u '*' -r -f -xml > qstat.xml</pre>
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class QstatXMLParser {

    private static final Logger log = LoggerFactory.getLogger(QstatXMLParser.class);

    private static final DateFormat qstatDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    public Snapshot loadFromFile(String filename) throws Exception {

        Date samplingTime = null;
        File file = new File(filename);
        String ts = file.getName();
        ts = ts.substring(ts.indexOf('-') + 1, ts.indexOf('.'));
        try {
            samplingTime = fileDateFormat.parse(ts);
        }
        catch (ParseException e) {
            System.out.println("Could not parse date from filename: " + file.getName());
        }

        Snapshot snapshot = new Snapshot(samplingTime);
        SAXReader reader = new SAXReader();
        Document doc = reader.read(file);
        Element root = doc.getRootElement();

        Element queueInfo = root.element("queue_info");
        for (Iterator i = queueInfo.elementIterator("Queue-List"); i.hasNext();) {
            Element queueList = (Element) i.next();
            SnapshotNode node = parseNode(queueList);
            if (node == null) continue;

            for (Iterator j = queueList.elementIterator("job_list"); j.hasNext();) {
                Element jobList = (Element) j.next();
                SnapshotJob job = parseJob(jobList);
                job.setNode(node);
                log.debug("Parsed running job " + job);
                node.addJob(job);
            }
            snapshot.addNode(node);
        }

        Element jobInfo = root.element("job_info");
        for (Iterator i = jobInfo.elementIterator("job_list"); i.hasNext();) {
            Element jobList = (Element) i.next();
            SnapshotJob job = parseJob(jobList);
            log.debug("Parsed queued job " + job);
            snapshot.addQueuedJob(job);
        }

        return snapshot;
    }

    private SnapshotNode parseNode(Element queueList) {
        SnapshotNode node = new SnapshotNode();
        String queueName = queueList.valueOf("name");
        // Ignore the all.q's
        if (queueName.startsWith("all")) return null;
        String hostname = queueName.substring(queueName.indexOf('@') + 1);
        node.setName(hostname);
        return node;
    }

    private SnapshotJob parseJob(Element jobList) throws Exception {
        SnapshotJob job = new SnapshotJob();
        job.setState(jobList.valueOf("@state"));

        job.setName(jobList.valueOf("JB_name"));
        job.setOwner(jobList.valueOf("JB_owner"));

        String jobNum = jobList.valueOf("JB_job_number");
        if (jobNum != null && !"".equals(jobNum)) {
            job.setJobId(Integer.parseInt(jobNum));
        }

        String slots = jobList.valueOf("slots");
        if (slots != null && !"".equals(slots)) {
            job.setSlots(Integer.parseInt(slots));
        }

        String tasks = jobList.valueOf("tasks");
        if (tasks != null && !"".equals(tasks)) {
            job.setTasks(tasks);
        }

        String startTime = jobList.valueOf("JAT_start_time");
        if (startTime != null && !"".equals(startTime)) {
            job.setStartTime(qstatDateFormat.parse(startTime));
        }

        String subTime = jobList.valueOf("JB_submission_time");
        if (subTime != null && !"".equals(subTime)) {
            job.setSubTime(qstatDateFormat.parse(subTime));
        }

        String excl = jobList.valueOf("hard_request[@name='exclusive']");
        if ("true".equals(excl)) {
            job.setExclusive(true);
            job.setSlots(8);
        }

        return job;
    }

    public static void main(String args[]) throws Exception {
        QstatXMLParser parser = new QstatXMLParser();
        Snapshot snapshot = parser.loadFromFile("grid1.xml");
        GridState state = new GridState(snapshot, "testState");
        // state.printGridSummary();
    }

}
