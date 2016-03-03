package gui;

import processing.core.PGraphics;
import processing.core.PVector;

/**
 * A Drawable object which may be sized in a PGraphics buffer, then positioned by a layout algorithm, and finally drawn. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public interface SizedDrawable extends Drawable {

    public void draw(PGraphics buf);

    public Rectangle getSize(PGraphics buf);

    public void setPos(PVector pos);
}
