package snapshot;

import timeline.Timeline;

/**
 * The interface for snapshot loaders. 
 * 
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public abstract class StateLoader {

    protected Timeline timeline;

    public StateLoader(Timeline timeline) {
        this.timeline = timeline;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public abstract boolean loadInitial() throws Exception;

    public abstract boolean loadNextSnapshot() throws Exception;

}
