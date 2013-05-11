package gui.layouts;

import gui.Rectangle;
import gui.SizedDrawable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import processing.core.PGraphics;
import processing.core.PVector;

/**
 * Draws a collection of SizedDrawables (rectangles) in a flow-based layout with a prescribed region.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class FlowLayout {

    private PGraphics buf;
    private float xSpacing;
    private float ySpacing;
    private float padding;
    
    public FlowLayout(PGraphics buf, float xSpacing, float ySpacing, float padding) {
        this.buf = buf;
        this.xSpacing = xSpacing;
        this.ySpacing = ySpacing;
    }
    
    public void drawInRegion(Collection<? extends SizedDrawable> drawables, Rectangle region) {
    	if (region==null) return;
    	
        Rectangle drawArea = new Rectangle(region.getPos().x+padding, region.getPos().y+padding, region.getWidth()-padding*2, region.getHeight()-padding*2);
        
        List<Row> rows = new ArrayList<Row>();
        rows.add(new Row());        
        
        // Calculate number of rows and row widths
        for(SizedDrawable d : drawables) {
            Row row = rows.get(rows.size()-1);
            row.addItem(d); 
            
            if (row.getWidth() > drawArea.getWidth()) {
                // Start a new row
                row.removeItem(d);
                row = new Row();
                rows.add(row);
                row.addItem(d); 
            }
        }
        
        // Calculate the height we need 
        float height = 0;
        for(Row row : rows) {
            height += row.getHeight();
        }

        // Draw all the rows
        float iy = drawArea.getPos().y + (drawArea.getHeight()-height)/2;
        for(Row row : rows) {
            float ix = drawArea.getPos().x + (drawArea.getWidth()-row.getWidth())/2;
            
            for(SizedDrawable item : row.getItems()) {
                item.setPos(new PVector(ix, iy));
                item.draw(buf);
                ix += item.getSize(buf).getWidth() + xSpacing;
            }
            
            // Move to next row
            iy += row.getHeight() + ySpacing;
        }
    }
    
    private class Row {
        
        private Collection<SizedDrawable> items = new ArrayList<SizedDrawable>();
        private float width;
        private float height;
        
        public void addItem(SizedDrawable item) {
            items.add(item);
            Rectangle rect = item.getSize(buf);
            width += rect.getWidth();
            if (rect.getHeight() > height) {
                height = rect.getHeight();
            }
        }

        public void removeItem(SizedDrawable item) {
            items.remove(item);
            Rectangle rect = item.getSize(buf);
            width -= rect.getWidth();
            // TODO: This leaves the height alone. Not a big deal right now, because all our items are the same height, but in the future...
        }
        
        public Collection<SizedDrawable> getItems() {
            return items;
        }

        public float getWidth() {
            return width + items.size()*xSpacing;
        }

        public float getHeight() {
            return height;
        }
    }
}
