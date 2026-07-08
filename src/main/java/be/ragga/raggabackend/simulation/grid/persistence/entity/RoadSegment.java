package be.ragga.raggabackend.simulation.grid.persistence.entity;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import jakarta.persistence.*;

/**
 * A persisted road segment - the finished form of a generation-time
 * {@code RoadDraft}. A straight, axis-aligned, 1-tile-wide run from
 * {@code start} to {@code end} inclusive. ROAD tiles in the grid are derived
 * from these segments, never the other way around.
 */
@Entity
public class RoadSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // Two GridPosition embeddables in one row: both default to x/y columns, so
    // the second is remapped to end_x/end_y.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "x", column = @Column(name = "start_x")),
            @AttributeOverride(name = "y", column = @Column(name = "start_y"))
    })
    private GridPosition start;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "x", column = @Column(name = "end_x")),
            @AttributeOverride(name = "y", column = @Column(name = "end_y"))
    })
    private GridPosition end;

    @Enumerated(EnumType.STRING)
    private RoadClass roadClass;

    protected RoadSegment() {
    }

    public RoadSegment(GridPosition start, GridPosition end, RoadClass roadClass) {
        this.start = start;
        this.end = end;
        this.roadClass = roadClass;
    }

    public long getId() {
        return id;
    }

    public GridPosition getStart() {
        return start;
    }

    public GridPosition getEnd() {
        return end;
    }

    public RoadClass getRoadClass() {
        return roadClass;
    }
}
