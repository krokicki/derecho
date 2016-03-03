package timeline;

/**
 * An event at a given offset in the current time-line. 
 *
 * @author <a href="mailto:krokicki@gmail.com">Konrad Rokicki</a>
 */
public class Event {

    private Long offset;

    public Event(Long offset) {
        this.offset = offset;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

}
