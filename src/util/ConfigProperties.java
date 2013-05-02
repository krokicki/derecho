package util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Properties for configuring the visualization. The filename to load is given by the CONFIG system property. If no 
 * filename is specified, the default is derecho.properties. The properties file to load must be in the classpath.
 *
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class ConfigProperties extends Properties {

    private static final Logger log = LoggerFactory.getLogger(ConfigProperties.class);
    
    private static final String DEFAULT_CONFIG_FILENAME = "derecho.properties";
    
    private static ConfigProperties singleton;

    public static ConfigProperties getInstance() {
        if (singleton==null) {
            String propertiesFileName = System.getProperty("CONFIG");
            if (propertiesFileName == null) propertiesFileName = DEFAULT_CONFIG_FILENAME;
            singleton = new ConfigProperties();
            singleton.load(propertiesFileName);
        }
        return singleton;
    }
    
    private void load(String propertiesFileName) {
        try {
            InputStream is = ConfigProperties.class.getClassLoader().getResourceAsStream(propertiesFileName);
            if (is==null) {
                is = new FileInputStream(propertiesFileName);
            }
            load(is);
            log.info("Loaded properties from "+propertiesFileName);
        }
        catch (Exception e) {
            log.error("Could not read properties file: " + propertiesFileName);
        }
    }

    public static String getString(String name) {
        return getString(name, null);
    }
    
    public static String getString(String name, String defaultValue) {
        String s = getInstance().getProperty(name);
        if (s==null) return defaultValue;
        return s;
    }
    
    public static Boolean getBoolean(String name) {
        return getBoolean(name, null);
    }
    
    public static Boolean getBoolean(String name, Boolean defaultValue) {
        String s = getInstance().getProperty(name);
        if (s==null) return defaultValue;
        return Boolean.valueOf(s);
    }
    
    public static Integer getInteger(String name) {
        return getInteger(name, null);
    }
    
    public static Integer getInteger(String name, Integer defaultValue) {
        String s = getInstance().getProperty(name);
        if (s==null) return defaultValue;
        return Integer.valueOf(s);
    }
}
