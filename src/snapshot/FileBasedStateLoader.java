package snapshot;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import timeline.Timeline;

/**
 * XML loader for cluster timelines in qstat's XML format.
 * 
 * @see QstatXMLParser
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class FileBasedStateLoader extends StateLoader {

    private static final Logger log = LoggerFactory.getLogger(FileBasedStateLoader.class);

    public FileBasedStateLoader(Timeline timeline) {
        super(timeline);
    }

    public boolean loadInitial() throws Exception {

        QstatXMLParser parser = new QstatXMLParser();
        File dir = new File("test-small/");
        for (File file : dir.listFiles()) {
            if (!file.getName().endsWith(".xml")) continue;

            log.debug("Loading snapshot: {}", file);
            Snapshot snapshot = null;
            try {
                snapshot = parser.loadFromFile(file.getAbsolutePath());
            }
            catch (Exception e) {
                log.error("Error parsing snapshot: {}", file, e);
                continue;
            }

            try {
                timeline.addSnapshot(snapshot);
            }
            catch (Exception e) {
                log.error("Error adding snapshot: {}", file, e);
            }
        }

        log.debug("Loaded {} snapshots", timeline.getSnapshots().size());
        return !timeline.getSnapshots().isEmpty();
    }

    public boolean loadNextSnapshot() throws Exception {
        return false;
    }
}
