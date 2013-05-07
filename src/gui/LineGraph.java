package gui;

import gui.Rectangle.Bounds;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;
import timeline.Timeline;

/**
 * A simple line graph for showing a sampled value over time. 
 * 
 * TODO: This is currently too tightly coupled to the Timeline.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class LineGraph implements Drawable {

    private static final Logger log = LoggerFactory.getLogger(SketchState.class);
    
    private final Timeline timeline;
    private final Map<Long, Integer> graphMap;
    private final Rectangle rect;
    private int maxValue;
    private int color = Utils.color("FF0000");
    
    public LineGraph(Rectangle rect, Timeline timeline, Map<Long, Integer> graphMap) {
        this.rect = rect;
        this.timeline = timeline;
        this.graphMap = graphMap;
    }
    
    public void draw(PGraphics buf) {

        PVector pos = rect.getPos();
        Bounds b = rect.getBounds();
        
        Map<Long,Integer> map = new ConcurrentSkipListMap<Long,Integer>(graphMap);
        long firstOffset = timeline.getFirstOffset();
        long length = timeline.getLength();
        long realLength = map.isEmpty()?0:(Collections.max(map.keySet()) - firstOffset);
        
        int minValue = 0;

        if (realLength>length) {
            log.warn("realLength>length : {}>{}",realLength,length);
            length = realLength;
        }
        
        buf.strokeWeight(2);
        
        Utils.stroke(buf, color);
        Utils.fill(buf, color);
        
        buf.strokeCap(PApplet.SQUARE);

        Float prevX = null;
        Float prevY = null;
        Float firstX = null;
        Float firstY = null;
        
        Integer currx = null;
        Collection<Float> values = new HashSet<Float>();
        
        for(Long offset : map.keySet()) {
            Integer value = map.get(offset);
                
            if (offset > firstOffset+length) {
                log.warn("offset>end : {}>{}",offset,firstOffset+length);
            }
            
            float x = PApplet.map(offset, firstOffset, firstOffset+length, pos.x, b.maxX-1);
            float y = PApplet.map(value, minValue, maxValue, b.maxY, pos.y);
            if (firstX==null) firstX = x;
            if (firstY==null) firstY = y;
            
            int intx = Math.round(x);
            
            if (currx!=null && currx != intx && !values.isEmpty()) {
                
                // Get the average of the values we've seen for this time point
                float avgy = Utils.calculateAverage(values);
                
                values.clear();

                if (prevX==null) prevX = pos.x;
                if (prevY==null) prevY = y;
                
                buf.line(prevX, prevY, x, prevY);
                buf.strokeCap(PApplet.ROUND);
                buf.line(x, prevY, x, avgy);

                prevX = x;
                prevY = avgy;
            }
            
            currx = intx;
            values.add(y);  
        }

        buf.strokeCap(PApplet.SQUARE);
        buf.line(prevX, prevY, b.maxX-1, prevY);
    }
    
    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }

    public Map<Long, Integer> getGraphMap() {
        return graphMap;
    }
}
