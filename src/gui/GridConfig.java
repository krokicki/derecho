package gui;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for grid nodes (number of slots, row/column position in the visualizations, etc)
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class GridConfig {

    private static final Logger log = LoggerFactory.getLogger(GridConfig.class);
    
    private static final String DEFAULT_CONFIG_FILENAME = "grid_config.xml";

    private static GridConfig singleton;

    private Map<String, NodeSubSet> subsets = new LinkedHashMap<String, NodeSubSet>();
    
    public static GridConfig getInstance() {
        if (singleton==null) {
            String configFileName = System.getProperty("GRID_CONFIG");
            if (configFileName == null) configFileName = DEFAULT_CONFIG_FILENAME;
            singleton = new GridConfig();
            singleton.load(configFileName);
        }
        return singleton;
    }
    
    private void load(String configFileName) {
        try {
            InputStream is = GridConfig.class.getClassLoader().getResourceAsStream(configFileName);
            if (is==null) {
                is = new FileInputStream(configFileName);
            }

            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element root = doc.getRootElement();
            
            for (Iterator i = root.elementIterator("subset"); i.hasNext();) {
                Element subsetElement = (Element)i.next();

                String subsetName = subsetElement.valueOf("@name");
                NodeSubSet nodeSubSet = new NodeSubSet(subsetName);
                
                log.trace("got subset {}",subsetName);
                
                for (Iterator j = subsetElement.elementIterator("nodes"); j.hasNext();) {
                    Element nodesElement = (Element)j.next();
                    String pattern = nodesElement.valueOf("@pattern");
                    log.trace("got pattern {}",pattern);
                    
                    Integer slots = parseIntegerOrNull(nodesElement.valueOf("@slots"));
                    Integer row = parseIntegerOrNull(nodesElement.valueOf("@row"));
                    Integer col = parseIntegerOrNull(nodesElement.valueOf("@col"));
                    NodeSet nodeSet = new NodeSet(pattern, slots, row, col);
                    nodeSubSet.addNodeSet(nodeSet);
                }
                
                subsets.put(subsetName, nodeSubSet);
            }
            
            log.info("Loaded grid config from "+configFileName);
        }
        catch (Exception e) {
            log.error("Could not load grid config file: " + configFileName);
            throw new RuntimeException(e);
        }
    }
    
    private Integer parseIntegerOrNull(String s) {
    	if (s==null || "".equals(s)) return null;
    	try {
    		return Integer.parseInt(s);	
    	}
    	catch (NumberFormatException e) {
    		return null;
    	}
    }

    public NodeConfiguration getConfiguration(String nodeShortName) {
    	
    	for(NodeSubSet nodeSubSet : subsets.values()) {
    		for(NodeSet nodeSet : nodeSubSet.getNodeSets()) {
                Pattern p = Pattern.compile(nodeSet.getPattern());
                Matcher m = p.matcher(nodeShortName);
                if (m.matches()) {
                	int i = 0;
                	Integer row = nodeSet.getRow()!=null ? nodeSet.getRow() : Integer.parseInt(m.group(++i));
                	Integer col = nodeSet.getCol()!=null ? nodeSet.getCol() : Integer.parseInt(m.group(++i));
                	return new NodeConfiguration(nodeShortName, nodeSet, row, col);
                }
    		}
    	}
    	
    	return null;
    }

    public class NodeSubSet {
    	private String name;
    	private List<NodeSet> nodeSets = new ArrayList<NodeSet>();
		public NodeSubSet(String name) {
			this.name = name;
		}
		public String getName() {
			return name;
		}
		public List<NodeSet> getNodeSets() {
			return nodeSets;
		}
		public void addNodeSet(NodeSet nodeSet) {
			this.nodeSets.add(nodeSet);
			nodeSet.setSubset(this);
		}
    }
    
    public class NodeSet {
    	private NodeSubSet subset;
    	private String pattern;
    	private Integer slots;
    	private Integer row;
    	private Integer col;
    	
		NodeSet(String pattern, Integer slots, Integer row, Integer col) {
			this.pattern = pattern;
			this.slots = slots;
			this.row = row;
			this.col = col;
		}
		public NodeSubSet getSubset() {
			return subset;
		}
		void setSubset(NodeSubSet subset) {
			this.subset = subset;
		}
		public String getPattern() {
			return pattern;
		}
		public Integer getSlots() {
			return slots;
		}
		public Integer getRow() {
			return row;
		}
		public Integer getCol() {
			return col;
		}
    }
    
    public class NodeConfiguration {
    	private String nodeShortName;
    	private NodeSet nodeSet;
    	private int row;
    	private int col;
    	
		NodeConfiguration(String nodeShortName, NodeSet nodeSet, int row, int col) {
			this.nodeShortName = nodeShortName;
			this.nodeSet = nodeSet;
			this.row = row;
			this.col = col;
		}
		
		public String getNodeShortName() {
			return nodeShortName;
		}
		public NodeSet getNodeSet() {
			return nodeSet;
		}
		public int getRow() {
			return row;
		}
		public int getCol() {
			return col;
		}
		public void log() {
			log.info("Name: "+nodeShortName);
			log.info("  Subset: "+nodeSet.getSubset().getName());
			log.info("  Location: "+row+","+col);
		}
    }
    
    public static final void main(String[] args) throws Exception {
    	GridConfig config = getInstance();
    	config.getConfiguration("f00u25").log();
    	config.getConfiguration("f02u02").log();
    	config.getConfiguration("f15u04a").log();
    	config.getConfiguration("f16u01").log();
    	config.getConfiguration("f16u33").log();
    	config.getConfiguration("h04u15").log();
    }
    
}
