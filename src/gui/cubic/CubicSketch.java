package gui.cubic;

import gui.GridConfig;
import gui.GridSketch;
import gui.Rectangle;
import gui.cubic.CubicSketchState.JobActor;
import ijeoma.motion.Motion;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;

import org.perf4j.LoggingStopWatch;
import org.perf4j.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PVector;
import snapshot.MySQLBasedStateLoader;
import snapshot.StateLoader;
import timeline.Timeline;
import util.ConfigProperties;

import com.google.common.util.concurrent.*;

import controlP5.*;

/**
 * The Derecho visualization which displays the grid cluster as a 3d cube.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class CubicSketch extends PApplet {

    private static final Logger log = LoggerFactory.getLogger(GridSketch.class);

    private static final String MAIN_CLASS = CubicSketch.class.getName();
    private static final long LIVE_POLL_INTERVAL_SECONDS = ConfigProperties.getInteger("derecho.data.poll.secs", 5);
    private static final int MAX_DRAWING_ERRORS = 5;

    private final boolean TIMER = false;
    private final boolean bufferAllBeforePlaying = true;
    private final boolean startAtLivePosition = false;

    private int sliderColor = color("232847");
    private int sliderBarColor = color("323966");
    private int sliderBarActiveColor = color("323966");
    private int controllerActiveColor = color("7D8EFF");

    private float appWidth;
    private float appHeight;

    // Controls
    private ControlP5 cp5;
    private long currTime = 0;
    private Rectangle sliderRect;

    private ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));
    private ListenableFuture<Timeline> futureTimeline;

    // Play state
    private boolean isLive = true;
    private boolean isSummary = true;
    private boolean initialLoadComplete = false;
    private boolean initialStartComplete = false; // has the animation ever been started?
    private float playSpeed = 1.0f;
    private long lastPosition = 0;
    private long lastPositionTime = 0;
    private boolean sliderWasPressedLastFrame = false;

    private int errorsWhileDrawing = 0;

    // Nutation
    private Random random = new Random();
    private Long lastNutateChange = 0L;
    private PVector nutateVector;
    private double nutateSpeed;

    // Auto Zoom
    private Long lastZoomChange = 0L;
    private double zoomSpeed;
    private double minZoom = 500;
    private double maxZoom = 1500;

    // Data model
    private Timeline timeline;
    private CubicSketchState sketchState;

    private PeasyCam cam;

    public static void main(String args[]) {
        boolean fullscreen = ConfigProperties.getBoolean("derecho.viz.fullscreen", true);
        if (fullscreen) {
            PApplet.main(new String[] { "--full-screen", MAIN_CLASS });
        }
        else {
            PApplet.main(new String[] { MAIN_CLASS });
        }
    }

    public void setup() {

        // Attempt to fix the Linux fullscreen bug where the display starts in the middle of the screen
        try {
            Thread.sleep(200);
        }
        catch (Exception e) {
        }

        size(displayWidth, displayHeight, P3D);
        hint(ENABLE_NATIVE_FONTS);
        smooth();
        randomSeed(0);
        frameRate(60);
        lights();

        cam = new PeasyCam(this, 0, 0, 0, 800);
        cam.setMinimumDistance(minZoom);
        cam.setMaximumDistance(maxZoom);

        try {
            this.appWidth = displayWidth;
            this.appHeight = displayHeight;

            // Ensure we can load configurations
            GridConfig.getInstance();
            ConfigProperties.getInstance();

            Motion.setup(this);

            cp5 = new ControlP5(this);
            unregisterMethod("keyEvent", cp5);
            unregisterMethod("keyEvent", cp5.getWindow());

            cp5.setAutoDraw(false);
            cp5.addFrameRate().setInterval(10).setPosition(1, 1);

            CColor sliderCColor = new CColor();
            sliderCColor.setBackground(sliderColor);
            sliderCColor.setForeground(sliderBarColor);
            sliderCColor.setActive(sliderBarActiveColor);

            float progressSliderWidth = displayWidth * .5f;
            float progressSliderHeight = displayHeight * .02f;
            float progressSliderX = (displayWidth - progressSliderWidth) / 2;
            float progressSliderY = (displayHeight - progressSliderHeight) / 2;

            float sliderPadding = displayHeight * .01f;

            sliderRect = new Rectangle(sliderPadding, height - sliderPadding * 2, width - sliderPadding * 2, displayHeight * .01f);

            cp5.addSlider("progress")
                    .setPosition(progressSliderX, progressSliderY)
                    .setSize((int) progressSliderWidth, (int) progressSliderHeight)
                    .setRange(0, 200)
                    .setColor(sliderCColor)
                    .setLabelVisible(false);

            cp5.addSlider("currTime")
                    .setPosition(sliderRect.getPos().x, sliderRect.getPos().y)
                    .setSize((int) sliderRect.getWidth(), (int) sliderRect.getHeight())
                    .setRange(0, 200)
                    .setColor(sliderCColor)
                    .setVisible(false)
                    .setLabelVisible(false);

            Group optionsGroup = cp5.addGroup("options")
                    .setPosition(appWidth - 300, 10)
                    .setWidth(300)
                    .activateEvent(true)
                    .setColorForeground(sliderBarColor)
                    .setColorBackground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setBackgroundColor(color(255, 80))
                    .setBackgroundHeight(90)
                    .setLabel("Options")
                    .close();

            cp5.addCheckBox("setOptions")
                    .setPosition(10, 10)
                    .setSize(20, 9)
                    .addItem("Laser Tracking", 0)
                    .addItem("Indicate Snapshots", 1)
                    .addItem("Anonymize Users", 2)
                    .setCaptionLabel("Mode")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            cp5.addRadioButton("setModes")
                    .setPosition(130, 10)
                    .setSize(20, 9)
                    .addItem("Normal", 0)
                    .addItem("Highlight Users", 1)
                    .addItem("Usage Heatmap", 2)
                    .setNoneSelectedAllowed(false)
                    .activate(0)
                    .setCaptionLabel("Mode")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            cp5.addSlider("setPlaySpeed")
                    .setPosition(10, 50)
                    .setSize(180, 9)
                    .setValue(playSpeed)
                    .setRange(1, 50)
                    .setDecimalPrecision(0)
                    .setCaptionLabel("Play Speed (1x-40x)")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(controllerActiveColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            cp5.addButton("setIsLive")
                    .setSwitch(true)
                    .setPosition(10, 65)
                    .setSize(24, 12)
                    .setCaptionLabel("LIVE")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            cp5.addButton("setSummary")
                    .setSwitch(true)
                    .setPosition(40, 65)
                    .setSize(50, 12)
                    .setCaptionLabel("Summary")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            cp5.addButton("exitSketch")
                    .setPosition(260, 10)
                    .setSize(24, 12)
                    .setCaptionLabel("Exit")
                    .setColorBackground(sliderBarColor)
                    .setColorForeground(sliderBarColor)
                    .setColorActive(controllerActiveColor)
                    .setGroup(optionsGroup);

            if (isLive) goLive();

            log.info("setup() complete");
        }
        catch (Exception e) {
            log.error("Error loading state", e);
            exit();
        }
    }

    public void draw() {
        try {
            background(0);

            StopWatch stopWatch = null;
            if (TIMER) stopWatch = new LoggingStopWatch("draw");

            // Any time the state is ready, just start playback
            if (sketchState != null && sketchState.isReady()) {
                log.info("Beginning playback");
                sketchState.play();
                this.initialStartComplete = true;
            }

            Slider progressBar = (Slider) cp5.getController("progress");
            Slider slider = (Slider) cp5.getController("currTime");

            if (!initialStartComplete) {

                progressBar.setVisible(true);
                slider.setVisible(false);

                // Update the progress indicator
                updateProgressBar();

                // Draw the progress indicator
                cam.beginHUD();
                cp5.draw();
                cam.endHUD();

                // Ready to start yet?
                if (sketchState != null) {
                    if ((initialLoadComplete || !bufferAllBeforePlaying) && timeline.isReady() && sketchState.isPaused()) {
                        log.info(startAtLivePosition ? "Buffering to LIVE starting position" : "Buffering to starting position");
                        sketchState.bufferAtPosition(startAtLivePosition ? timeline.getLiveOffset() : timeline.getFirstOffset());
                    }
                }
            }
            else {

                // Nutate
                if (lastNutateChange != null && System.currentTimeMillis() - lastNutateChange > 2000) {
                    nutateVector = new PVector(random.nextFloat() - .5f, random.nextFloat() - .5f, random.nextFloat() - .5f);
                    lastNutateChange = System.currentTimeMillis();
                    nutateSpeed = random.nextFloat() / 10;
                }

                cam.rotateX(nutateVector.x * nutateSpeed);
                cam.rotateY(nutateVector.y * nutateSpeed);
                cam.rotateZ(nutateVector.z * nutateSpeed);

                // Zoom
                if (lastZoomChange != null && System.currentTimeMillis() - lastZoomChange > 3000) {
                    zoomSpeed = (random.nextFloat() - .5f) / 4;
                    lastZoomChange = System.currentTimeMillis();
                }

                double newZoom = cam.getDistance() * (1 - zoomSpeed);
                if (newZoom > minZoom && newZoom < maxZoom) {
                    cam.setDistance(newZoom);
                }

                progressBar.setVisible(false);
                slider.setVisible(true);

                // Update the timeline slider's properties
                slider.setRange(timeline.getFirstOffset(), timeline.getLastOffset());

                // Interpret the user clicking on the timeline slider
                boolean sliderPressed = false;
                if (sliderRect != null) {
                    sliderPressed = mousePressed && sliderRect.getBounds().contains(mouseX, mouseY);
                    // Stop or start playback when the slider is adjusted
                    if (mousePressed) {
                        if (sliderPressed) {
                            sliderWasPressedLastFrame = true;
                            sketchState.pause();
                        }
                    }
                    else {
                        if (sliderWasPressedLastFrame) {
                            sliderWasPressedLastFrame = false;
                            int value = Math.round(slider.getValue());
                            goDead();
                            sketchState.bufferAtPosition(value);
                        }
                    }
                }

                // Interpolate slider between updates from the SketchState
                if (sketchState.isPlaying()) {
                    long newPosition = sketchState.getPosition();
                    if (newPosition != lastPosition) {
                        lastPosition = currTime = newPosition;
                        lastPositionTime = System.currentTimeMillis();
                    }
                    else {
                        currTime = lastPosition + (long) ((System.currentTimeMillis() - lastPositionTime) * playSpeed);
                    }
                    slider.setValue(currTime);
                }

                if (TIMER) stopWatch.lap("slider");

                // Draw Job Lattice

                for (JobActor jobActor : sketchState.getJobActors().values()) {
                    jobActor.update();
                    jobActor.draw(g);
                }

                // Draw the UI
                cam.beginHUD();
                cp5.draw();
                cam.endHUD();

                if (TIMER) stopWatch.lap("drawAndUpdateSprites");
            }

            if (TIMER) stopWatch.stop("draw");
        }
        catch (Exception e) {
            log.error("Error while drawing", e);

            if (errorsWhileDrawing++ > MAX_DRAWING_ERRORS) {
                JOptionPane.showMessageDialog(frame,
                        "Error encountered. See error log for details.",
                        "Fatal error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }
    }

    private void updateProgressBar() {
        Slider slider = (Slider) cp5.getController("progress");
        float value = slider.getValue();
        if (value + 1 >= slider.getMax()) {
            slider.setValue(slider.getMin());
        }
        else {
            slider.setValue(slider.getValue() + 1);
        }
    }

    public void setPlaySpeed(float playSpeed) {
        if (sketchState != null) {
            log.trace("setPlaySpeed(" + playSpeed + ")");
            this.playSpeed = playSpeed;
            sketchState.setPlaySpeed(playSpeed);
            goDead();
        }
    }

    public void setIsLive(boolean ignore) {
        Button button = (Button) cp5.getController("setIsLive");
        if (button != null) {
            log.trace("isLive=" + isLive + " button.isOn=" + button.isOn());
            if (this.isLive != button.isOn()) {
                this.isLive = button.isOn();
                if (isLive) {
                    goLive();
                }
                else {
                    goDead();
                }
            }
        }
    }

    public void exitSketch() {
        System.exit(0);
    }

    private void goLive() {

        if (sketchState != null) {
            log.info("Ending previous sketch state");
            sketchState.end();
        }

        this.timeline = new Timeline();
        this.sketchState = new CubicSketchState(this, timeline, appWidth, appHeight);

        // Reset the play speed
        // this.playSpeed = 1.0f;
        // sketchState.setPlaySpeed(playSpeed);
        // Slider slider = (Slider)cp5.getController("setPlaySpeed");
        // slider.changeValue(1.0f);
        //
        // Button isLiveButton = (Button)cp5.getController("setIsLive");
        // if (!isLiveButton.isOn()) {
        // isLiveButton.setOn();
        // }

        log.info("Going live...");

        this.initialLoadComplete = false;
        this.initialStartComplete = false;

        this.futureTimeline = executor.submit(new Callable<Timeline>() {
            public Timeline call() throws Exception {
                log.debug("beginning initial load");
                StateLoader stateLoader = new MySQLBasedStateLoader(timeline);
                stateLoader.loadInitial();
                log.debug("initial load completed");
                return stateLoader.getTimeline();
            }
        });

        Futures.addCallback(futureTimeline, new FutureCallback<Timeline>() {
            public void onSuccess(final Timeline timeline) {
                initialLoadComplete = true;

                // Start the playback
                log.info("starting sketch thread...");
                Thread sketchThread = new Thread(sketchState);
                sketchThread.start();

                checkForNewSnapshots();
            }

            public void onFailure(Throwable thrown) {
                futureTimeline = null;
                log.error("Error loading initial timeline", thrown);
                System.exit(1);
            }
        });
    }

    private void goDead() {

        this.isLive = false;
        log.info("Going dead...");
        // Button button = (Button)cp5.getController("setIsLive");
        // if (button!=null && button.isOn()) {
        // button.setOff();
        // return;
        // }
    }

    private void checkForNewSnapshots() {

        if (!isLive) {
            log.info("Not live, will not check for new snapshots.");
            return;
        }

        futureTimeline = executor.submit(new Callable<Timeline>() {
            public Timeline call() throws Exception {
                StateLoader stateLoader = new MySQLBasedStateLoader(timeline);
                if (stateLoader.loadNextSnapshot()) {
                    log.debug("next snapshot loaded");
                }
                // Give the sketch time to read the new data, and turn off its awaiting data flag
                Thread.sleep(LIVE_POLL_INTERVAL_SECONDS * 1000);
                return stateLoader.getTimeline();
            }
        });
        Futures.addCallback(futureTimeline, new FutureCallback<Timeline>() {
            public void onSuccess(Timeline timeline) {
                futureTimeline = null;
                checkForNewSnapshots();
            }

            public void onFailure(Throwable thrown) {
                futureTimeline = null;
                log.error("Error loading next snapshot", thrown);
                checkForNewSnapshots();
            }
        });
    }

    private int color(String hex) {
        return PApplet.unhex(hex.length() == 6 ? "FF" + hex : hex);
    }
}
