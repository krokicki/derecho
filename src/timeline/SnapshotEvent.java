package timeline;

public class SnapshotEvent extends Event {

    public SnapshotEvent(Long offset) {
        super(offset);
    }

    @Override
    public String toString() {
        return "SnapshotEvent[offset=" + getOffset() + "]";
    }
}
