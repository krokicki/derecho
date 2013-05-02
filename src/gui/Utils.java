package gui;

import java.util.Collection;

import processing.core.PApplet;
import processing.core.PGraphics;

/**
 * Some handy utility methods. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class Utils {

    public static int color(String hex) {
        return PApplet.unhex("FF"+hex);
    }
    
    public static void stroke(PGraphics buf, int color) {
        stroke(buf, color, 255);
    }
    
    public static void stroke(PGraphics buf, int color, float opacity) {
        buf.colorMode(PApplet.RGB);
        buf.stroke(color, opacity);
    }

    public static void fill(PGraphics buf, int color) {
        fill(buf, color, 255);
    }
    
    public static void fill(PGraphics buf, int color, float opacity) {
        buf.colorMode(PApplet.RGB);
        buf.fill(color, opacity);   
    }

    public static float calculateAverage(Collection<Float> values) {
        if (values.isEmpty()) return 0f;
        float sum = 0;
        for (Float f : values) {
            sum += f.floatValue();
        }
        return sum / values.size();
    }
}
