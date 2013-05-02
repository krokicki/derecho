package gui;

/**
 * The current state of the visualization playback.
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public enum PlayState {
    /** Paused and waiting */
    PAUSED,
    /** Buffering data */
    BUFFERING,
    /** Data is loaded and ready to begin playing */
    READY,
    /** Playing back */
    PLAYING,
    /** Final end state */
    END
}
