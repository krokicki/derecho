package gui;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * A legend which associates users with colors from a select color palette. Also supports anonymizing user names 
 * and highlighing a single user while greying out the others. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class Legend implements Drawable {

    private static final Logger log = LoggerFactory.getLogger(Legend.class);
    
    private Rectangle rect;
    private float itemHorizontalSpacing = 20;
    private float itemVerticalSpacing = 10;
    private float itemHeight = 10;

    private int[] colorPalette = {
            Utils.color("ED1515"), // red
            Utils.color("80FF00"), // neon green
            Utils.color("1264FF"), // sky blue
            
            Utils.color("FF6600"), // orange
            Utils.color("F4FA48"), // yellow
            
            Utils.color("E352EB"), // pink
            Utils.color("00BF23"), // forest green
            Utils.color("00B7FF"), // aqua

            Utils.color("930CED"), // purple
            Utils.color("30F27B"), // mint
            Utils.color("63EFFF"), // cyan

            Utils.color("FF6B90"), // skin
            Utils.color("A0F078"), // light green
            Utils.color("6428FC"), // blue purple

            Utils.color("FFA600"), // yellow orange
            Utils.color("93BD39"), // camo
            Utils.color("EDA3FF"), // lavendar

            Utils.color("257A4A"), // dark green
            Utils.color("A36E24"), // brown       
    };
    
    private PFont font;
    private int fontHeight;
    private float legendRowHeight;
    private LinkedList<Integer> colorQueue = new LinkedList<Integer>();
    private Map<String, Integer> colorAssignment = new LinkedHashMap<String, Integer>();
    private Map<String, String> anonNames = new HashMap<String, String>();
    private int currAnon = 1;
    private int rows = 1;

    // Anonymize user names?
    private boolean isAnonUsernames = false;
    
    // Highlight a single username?
    private String highlightUsername;
    
    public Legend(Rectangle rect, PFont font, int fontHeight) {
        this.rect = rect;
        this.font = font;
        this.fontHeight = fontHeight;
        this.legendRowHeight = fontHeight + 6;
        for (int color : colorPalette) {
            colorQueue.add(color);
        }
    }
    
    public int getItemColor(String item) {
        Integer color = colorAssignment.get(item);
        if (color == null) {
            color = colorQueue.removeFirst();
            colorQueue.addLast(color);
            colorAssignment.put(item, color);
            anonNames.put(item, "user"+currAnon);
            currAnon++;
        }
        return color;
    }
    
    public Map<String, Integer> getColorAssignments() {
        return ImmutableMap.copyOf(colorAssignment);
    }
    
    public void retain(Map<String,Integer> userSlotMap) {
        Set<String> users = ImmutableSet.copyOf(getColorAssignments().keySet());
        for(String username : users) {
            Integer slots = userSlotMap.get(username);
            if (slots==null) {
                // Reclaim color, and put it at the front of the queue
                Integer color = colorAssignment.remove(username);
                colorQueue.remove(color);
                colorQueue.addFirst(color);
            }
        }
    }
    
    public float getHeight() {
        return rows*legendRowHeight;
    }
    
    public void draw(PGraphics buf) {
        
    	if (rect==null) return;
    	
        buf.beginDraw();
        buf.textFont(font);
        buf.textAlign(PApplet.TOP, PApplet.TOP);
        
        float ix = rect.getBounds().minX;
        float iy = rect.getBounds().minY;

        List<Float> rowWidths = new ArrayList<Float>();
        
        float widthNeeded = 0;
        float currWidthNeeded = 0;
        for(String item : colorAssignment.keySet()) {
            
            String label = isAnonUsernames?anonNames.get(item):item;
            currWidthNeeded = widthNeeded;
            float itemWidth = buf.textWidth(label) + itemHorizontalSpacing;
            widthNeeded += itemWidth;
            
            if (widthNeeded > rect.getWidth()) {
                rowWidths.add(currWidthNeeded);
                widthNeeded -= currWidthNeeded;
                currWidthNeeded = widthNeeded;
            }
        }
        
        if (widthNeeded>0) {
            rowWidths.add(widthNeeded);
        }
        
        rows = rowWidths.size();
        if (rows==0) {
            return;
        }
        else if (rowWidths.size()>1) {
            iy -= (rowWidths.size()-1) * (itemHeight+itemVerticalSpacing);
        }

        int row = 0;
        ix = rect.getBounds().minX + (rect.getWidth()-rowWidths.get(row))/2;

        widthNeeded = 0;
        currWidthNeeded = 0;
        for(String item : colorAssignment.keySet()) {
            
            String label = isAnonUsernames?anonNames.get(item):item;
            currWidthNeeded = widthNeeded;
            float itemWidth = buf.textWidth(label) + itemHorizontalSpacing;
            widthNeeded += itemWidth;
            
            if (widthNeeded > rect.getWidth()) {
                row++;
                ix = rect.getBounds().minX;
                try {
                    ix += (rect.getWidth()-rowWidths.get(row))/2;
                }
                catch (IndexOutOfBoundsException e) {
                    log.error("Legend row width did not get calculated for row="+row,e);
                }
                iy += itemHeight+itemVerticalSpacing;
                widthNeeded -= currWidthNeeded;
                currWidthNeeded = widthNeeded;
            }

            int color = getItemColor(item);
            if (highlightUsername!=null && !item.equals(highlightUsername)) {
                buf.colorMode(PApplet.HSB, 360, 100, 100);
                buf.fill(buf.color(buf.hue(color), 0, 50));
                buf.colorMode(PApplet.RGB);
            }
            else {
                buf.fill(color);
            }
            buf.text(label, ix, iy);
            
            ix += itemWidth;
        }
        
        buf.endDraw();
    }

    public boolean isAnonUsernames() {
        return isAnonUsernames;
    }

    public void setAnonUsernames(boolean isAnonUsernames) {
        this.isAnonUsernames = isAnonUsernames;
    }
    
    public Map<String, String> getAnonNames() {
        return anonNames;
    }

    public String getHighlightUsername() {
        return highlightUsername;
    }

    public void setHighlightUsername(String highlightUsername) {
        this.highlightUsername = highlightUsername;
    }

    public Rectangle getRect() {
        return rect;
    }

    public void setRect(Rectangle rect) {
        this.rect = rect;
    }
}
