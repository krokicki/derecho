package gui;

import gui.layouts.FlowLayout;

import java.util.*;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.core.PVector;

import com.google.common.collect.ImmutableSet;

/**
 * An alternative middle view which displays users with slot counts. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class SummaryView implements Drawable {
    
    private Legend legend;
    private Map<String,Integer> slotsUsedByUser;
    private Map<String,Integer> slotsQueuedByUser;
    private Map<String,UserSummaryView> summaryViewMap = new LinkedHashMap<String,UserSummaryView>();
    
    private Rectangle rect;
    private int fontHeight;
    private PFont font;
    private float padding = 10;
    private float boxPadding = 5;
    private float xSpacing = 20;
    private float ySpacing = 10;
    private float lineSpacing = 5;
    
    public SummaryView(Rectangle rect, PFont font, int fontHeight, Legend legend) {
        this.rect = rect;
        this.font = font;
        this.legend = legend;
        this.fontHeight = fontHeight;
    }
    
    public void draw(PGraphics buf) {
        
    	if (rect==null) return;
        if (slotsUsedByUser==null || slotsQueuedByUser==null) return;
        
        for(String username : legend.getColorAssignments().keySet()) {
            
            Integer running = slotsUsedByUser.get(username);
            Integer queued = slotsQueuedByUser.get(username);
            if (running==null) running = 0;
            if (queued==null) queued = 0;
            UserSummaryView userSummaryView = new UserSummaryView(username, running, queued);
            summaryViewMap.put(username, userSummaryView);
        }

        buf.beginDraw();
        buf.textFont(font); // set this here so that font width calculations are accurate
        FlowLayout layout = new FlowLayout(buf, xSpacing, ySpacing, padding);
        layout.drawInRegion(summaryViewMap.values(), rect);
        buf.endDraw();
    }

    public void retain(Map<String,Integer> slotsUsedByUser, Map<String,Integer> slotsQueuedByUser) {
        this.slotsUsedByUser = slotsUsedByUser;
        this.slotsQueuedByUser = slotsQueuedByUser;
        Set<String> users = ImmutableSet.copyOf(summaryViewMap.keySet());
        for(String username : users) {
            Integer slots = slotsUsedByUser.get(username);
            if (slots==null) {
                summaryViewMap.remove(username);
            }
        }
    }
    
    public Rectangle getUserRect(String username) {
        UserSummaryView userSummaryView = summaryViewMap.get(username);
        if (userSummaryView!=null) {
            return userSummaryView.getPositionedRect();
        }
        return null;
    }
    
    public void setRect(Rectangle rect) {
    	this.rect = rect;
    }
    
    public class UserSummaryView implements SizedDrawable {

        private Rectangle rect;
        private String userName;
        private String userLabel;
        private List<String> lines = new ArrayList<String>();
        
        public UserSummaryView(String name, int running, int queued) {
            this.userName = name;
            this.userLabel = legend.isAnonUsernames()?legend.getAnonNames().get(name):name;
            lines.add("");
            lines.add(userLabel);
            lines.add(running+" running");
            lines.add(queued+" queued");
        }

        public Rectangle getSize(PGraphics buf) {
            if (rect==null) {
                calculateSize(buf);
            }
            return rect;
        }
        
        public Rectangle getPositionedRect() {
        	if (rect==null) return null;
            if (rect.getPos().x==0 && rect.getPos().y==0) return null; // Not yet positioned
            return rect;
        }
        
        private Rectangle calculateSize(PGraphics buf) {
            
            float w = 0;
            float h = 0;
            
            for(String line : lines) {
                float lineWidth = buf.textWidth(line);
                if (lineWidth > w) w = lineWidth;
                h += fontHeight + lineSpacing;
            }
            
            h -= lineSpacing;
            
            rect = new Rectangle(0, 0, w+boxPadding*2, h+boxPadding*2);
            return rect;
        }
        
        public void setPos(PVector pos) {
            rect.setPos(pos.x, pos.y);
        }
        
        public void draw(PGraphics buf) {

            buf.beginDraw();
            
            buf.textFont(font);
            buf.textAlign(PApplet.TOP, PApplet.TOP);

            float ix = rect.getPos().x;
            float iy = rect.getPos().y;

            int color = legend.getItemColor(userName);
            String highlightUsername = legend.getHighlightUsername();
            if (highlightUsername!=null && !userName.equals(highlightUsername)) {
                buf.colorMode(PApplet.HSB, 360, 100, 100);
                buf.fill(buf.color(buf.hue(color), 0, 50));
                buf.colorMode(PApplet.RGB);
            }
            else {
                buf.fill(color);
            }
            
            for(String line : lines) {
                float lineWidth = buf.textWidth(line);
                ix = rect.getBounds().minX;
                ix += (rect.getWidth()-lineWidth)/2;
                buf.text(line, ix, iy);
                iy += fontHeight + lineSpacing;
            }
            
            buf.endDraw();
        }
    }

}
