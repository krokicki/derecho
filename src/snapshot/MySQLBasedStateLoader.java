package snapshot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import timeline.Timeline;
import util.ConfigProperties;

/**
 * Loader for cluster timelines in a MySQL database. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class MySQLBasedStateLoader extends StateLoader {
    
    private static final Logger log = LoggerFactory.getLogger(MySQLBasedStateLoader.class);
    
    private static final String jdbcDriver = ConfigProperties.getString("derecho.db.jdbc.driver");
    private static final String jdbcUrl = ConfigProperties.getString("derecho.db.jdbc.url");
    private static final String jdbcUser = ConfigProperties.getString("derecho.db.jdbc.user");
    private static final String jdbcPw = ConfigProperties.getString("derecho.db.jdbc.password");
    private static final int initialHours = ConfigProperties.getInteger("derecho.data.initial.load.hours", 6);
    
    private List<Timestamp> snapshotDates = new ArrayList<Timestamp>();
    
    public MySQLBasedStateLoader(Timeline timeline) {
        super(timeline);
    }
    
    public Connection getJdbcConnection() throws Exception {
        Class.forName(jdbcDriver);
        Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPw);
        connection.setAutoCommit(false);
        return connection;
    }
    
    public boolean loadInitial() throws Exception {
        log.info("Loading {} initial hours",initialHours);
        return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time >= convert_tz(now(), @@global.time_zone, 'US/Eastern') - INTERVAL "+initialHours+" HOUR order by poll_date_time");
//      return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2013/05/07 13:00:00' and '2013/05/07 13:30:00' order by poll_date_time"); // new grid
//      return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2013/05/07 09:00:00' and '2013/05/07 9:05:00' order by poll_date_time"); // demo 1
//      return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2013/04/16 11:00:00' and '2013/04/16 19:30:00' order by poll_date_time"); // demo 2
//        return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2013/04/25 12:00:00' and '2013/04/25 12:20:00' order by poll_date_time"); // cubic demo 1
//        loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time >= now() - INTERVAL 30 MINUTE order by poll_date_time");
//        loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time >= now() - INTERVAL 6 HOUR order by poll_date_time");
//        loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2012/12/20' and '2012/12/21' order by poll_date_time");
//        return loadInitial("select distinct poll_date_time from webqstat_node where poll_date_time between '2014/12/12 12:00:00' and '2014/12/12 12:30:00' order by poll_date_time"); // for parallel parent job fix
    }
    
    public boolean loadInitial(String sql) throws Exception {

        log.debug("Loading initial");
        int numLoaded = 0;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getJdbcConnection();
            
            stmt = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                
                Timestamp snapshotDate = rs.getTimestamp(1);
                log.debug("Loading snapshot: {}",snapshotDate);
                try {
                    loadSnapshot(snapshotDate);
                    numLoaded++;
                }
                catch (Exception e) {
                    log.error("Error adding snapshot: {}",snapshotDate,e);
                }
            }

            log.debug("Loaded {} snapshots",timeline.getSnapshots().size());
        }
        finally {
            if (rs!=null) rs.close();
            if (stmt!=null) stmt.close();
            if (conn!=null) conn.close();   
        }
        
        return numLoaded>0;
    }

    public boolean loadNextSnapshot() throws Exception {

        log.debug("Loading next snapshot, if available");

        Snapshot lastSnapshot = timeline.getLastLoadedSnapshot();

        if (lastSnapshot==null) {
            throw new IllegalStateException("Timeline has no snapshots loaded, cannot load next");
        }
        
        int numLoaded = 0;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getJdbcConnection();
            
            Timestamp lastSnapshotDate = new Timestamp(lastSnapshot.getSamplingTime().getTime());
            
            StringBuffer sql = new StringBuffer();
            sql.append("select distinct poll_date_time from webqstat_node where poll_date_time > ? order by poll_date_time");
            
            stmt = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
            stmt.setTimestamp(1, lastSnapshotDate);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                
                Timestamp snapshotDate = rs.getTimestamp(1);
                log.debug("Loading next snapshot: {}",snapshotDate);
                try {
                    loadSnapshot(snapshotDate);
                    numLoaded++;
                }
                catch (Exception e) {
                    log.error("Error adding snapshot: {}",snapshotDate,e);
                }
                
                break;
            }
            
            log.debug("Loaded {} snapshots",numLoaded);
        }
        finally {
            if (rs!=null) rs.close();
            if (stmt!=null) stmt.close();
            if (conn!=null) conn.close();   
        }
        
        return numLoaded > 0;
    }
    
    public Snapshot loadSnapshot(Timestamp snapshotDate) throws Exception {
        
        Snapshot snapshot = new Snapshot(snapshotDate);
        Map<String,SnapshotNode> nodeNameMap = new HashMap<String,SnapshotNode>();
        Map<Integer, String> nodeIdToNameMap = new HashMap<Integer, String>();
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getJdbcConnection();
            
            StringBuffer sql = new StringBuffer();
            sql.append("select id,name from webqstat_node where poll_date_time = ?");
            stmt = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
            stmt.setTimestamp(1, snapshotDate);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Integer nodeId = (Integer)rs.getObject(1);
                String queueName = rs.getString(2);
                String hostname = queueName.substring(queueName.indexOf('@')+1);
                if (!nodeNameMap.containsKey(hostname)) {
                    SnapshotNode node = new SnapshotNode();
                    node.setName(hostname);
                    snapshot.addNode(node);
                    nodeNameMap.put(hostname, node);
                }
                else {
                    log.trace("Already have node with hostname="+hostname);
                }
                nodeIdToNameMap.put(nodeId, hostname);
            }

            stmt.close();
            rs.close();
            sql = new StringBuffer();
            
            sql.append("select id,number,name,owner,assigned_node_id,state,submission_time,start_time,tasks,slots,hard_request_name from webqstat_job where poll_date_time = ?");
            stmt = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
            stmt.setTimestamp(1, snapshotDate);
            
            rs = stmt.executeQuery();

            log.debug("got snapshotDate: "+snapshotDate);
                        
            while (rs.next()) {

                SnapshotJob job = new SnapshotJob();
                
                Long number = rs.getLong("number");
                if (number!=null) {
                    job.setJobId(number.intValue());
                }

                String jobName = rs.getString("name");
                job.setName(jobName);

                String owner = rs.getString("owner");
                job.setOwner(owner);

                String state = rs.getString("state");
                if (state!=null) {
                    job.setState(state); 
                }
                
                Timestamp startDate = rs.getTimestamp("start_time");
                if (startDate != null) {
                    job.setStartTime(startDate);    
                }
                
                Timestamp subDate = rs.getTimestamp("submission_time");
                if (subDate != null) {
                    job.setSubTime(subDate);    
                }
                
                String tasks = rs.getString("tasks");
                if (tasks != null) {
                    job.setTasks(tasks);
                }
                
                Integer slots = (Integer)rs.getObject("slots");
                if (slots!=null) {
                    job.setSlots(slots);
                }

                String hard_request_name = rs.getString("hard_request_name");
                if (hard_request_name!=null) {
                	if ("exclusive".equals(hard_request_name)) {
	                    job.setExclusive(true);
	                    job.setSlots(8);
                	}
                	else if ("hadoop_exclusive".equals(hard_request_name)) {
                		job.setExclusive(true);
	                    job.setSlots(16);
                	}
                }

                Integer nodeId = (Integer)rs.getObject("assigned_node_id");
                if (nodeId!=null) {
                    String nodeName = nodeIdToNameMap.get(nodeId);
                    if (nodeName==null) {
                        log.error("Don't know about node with id: "+nodeId);
                    }
                    else {
                        SnapshotNode node = nodeNameMap.get(nodeName);
                        if (node!=null) {
                            // Bi-directional association
                            job.setNode(node);
                            node.addJob(job);
                        }
                        else if (startDate!=null) {
                            log.error("Job was started without node: "+job);
                        }
                    }
                }
                else {
                    snapshot.addQueuedJob(job);
                }
            }
        }
        finally {
            if (rs!=null) rs.close();
            if (stmt!=null) stmt.close();
            if (conn!=null) conn.close();   
        }

        snapshotDates.add(snapshotDate);
        snapshot.init();
        timeline.addSnapshot(snapshot);
        return snapshot;
    }
}
