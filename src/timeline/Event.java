package timeline;

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
