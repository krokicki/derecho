package gui;

import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PVector;

/**
 * A rectangle class adapted from http://code.compartmental.net/boombox/Rectangle.pde
 */
public class Rectangle {

    public class Bounds {
        public float minX, minY, maxX, maxY;

        public Bounds(float x1, float y1, float x2, float y2) {
            minX = x1;
            minY = y1;
            maxX = x2;
            maxY = y2;
        }

        public boolean contains(float x, float y) {
            return (x >= minX && x <= maxX && y >= minY && y <= maxY);
        }

        public boolean collidesWith(Bounds otherBounds) {
            if (maxY < otherBounds.minY)
                return false;
            if (minY > otherBounds.maxY)
                return false;

            if (maxX < otherBounds.minX)
                return false;
            if (minX > otherBounds.maxX)
                return false;

            return true;
        }

        public void constrain(PVector pos) {
            pos.x = PApplet.constrain(pos.x, minX, maxX);
            pos.y = PApplet.constrain(pos.y, minY, maxY);
        }
    }

    private int mHorzAlign, mVertAlign;
    private float mWidth, mHeight;
    private float x, y;
    private Bounds bounds;
    private ArrayList<Rectangle> adjacentRects;

    public Rectangle(float x, float y, float w, float h) {
        this(x, y, w, h, PApplet.LEFT, PApplet.TOP);
    }

    public Rectangle(float x, float y, float w, float h, int mHorzAlign, int mVertAlign) {
        this.x = x;
        this.y = y;
        this.mWidth = w;
        this.mHeight = h;
        this.mHorzAlign = mHorzAlign;
        this.mVertAlign = mVertAlign;
        this.adjacentRects = new ArrayList<Rectangle>();
        this.bounds = new Bounds(0, 0, 0, 0);
        calcBounds();
    }

    void draw(PGraphics buf) {
        buf.rectMode(PApplet.CORNERS);
        buf.rect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY);
        buf.rectMode(PApplet.CORNER);
    }

    public PVector getPos() {
        return new PVector(x, y);
    }

    public void setPos(float x, float y) {
        this.x = x;
        this.y = y;
        calcBounds();
    }

    public float getWidth() {
        return mWidth;
    }

    public float getHeight() {
        return mHeight;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void constrain(PVector pos) {
        bounds.constrain(pos);
    }

    public boolean pointInside(PVector pos) {
        return pointInside(pos.x, pos.y);
    }

    public boolean pointInside(float x, float y) {
        return bounds.contains(x, y);
    }

    public boolean collidesWith(Rectangle otherRect) {
        return bounds.collidesWith(otherRect.bounds);
    }

    private void calcBounds() {
        // assume LEFT / TOP
        bounds.minX = x;
        bounds.minY = y;
        bounds.maxX = x + mWidth;
        bounds.maxY = y + mHeight;

        switch (mHorzAlign) {
        case PApplet.CENTER: {
            bounds.minX -= mWidth * 0.5f;
            bounds.maxX -= mWidth * 0.5f;
            break;
        }

        case PApplet.RIGHT: {
            bounds.minX -= mWidth;
            bounds.maxX -= mWidth;
            break;
        }
        }

        switch (mVertAlign) {
        case PApplet.CENTER: {
            bounds.minY -= mHeight * 0.5f;
            bounds.maxY -= mHeight * 0.5f;
            break;
        }

        case PApplet.BOTTOM: {
            bounds.minY -= mHeight;
            bounds.maxY -= mHeight;
            break;
        }
        }
    }

    @Override
    public String toString() {
        return "Rectangle [" + x + "," + y + ", "
                + mWidth + "x" + mHeight + "]";
    }

}
