package be.ragga.raggabackend.simulation;

import be.ragga.raggabackend.simulation.building.Building;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.persistence.entity.Lot;
import be.ragga.raggabackend.simulation.grid.persistence.entity.PlacedBuilding;
import be.ragga.raggabackend.simulation.grid.persistence.entity.RoadSegment;
import be.ragga.raggabackend.simulation.grid.persistence.mapping.GenerationConfigConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A stored city: the full output of one generation run, persisted so it
 * survives a reboot and can back the live simulation.
 *
 * The generation package produces an in-memory GenerationResult; Phase B's
 * mapper turns that into this entity graph. Provenance (seed + the full config
 * snapshot) is kept so a stored city stays reproducible and traceable as the
 * generator's tuning knobs evolve.
 */
@Entity
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    // The seed that produced this city; with the config snapshot below it fully
    // reproduces the map, since generation is deterministic per (config, seed).
    private long seed;

    // Full GenerationConfig serialized to JSON. A column rather than 36 embedded
    // fields so the schema doesn't churn every time a knob is added.
    @Convert(converter = GenerationConfigConverter.class)
    @Column(columnDefinition = "TEXT")
    private GenerationConfig generationConfig;

    private Instant generatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<GridCell> grid = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<Lot> lots = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<RoadSegment> roads = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<PlacedBuilding> buildings = new ArrayList<>();

    // The economic Building instances bridged from PlacedBuilding placements
    // (currently residential only - see GenerationResultMapper). Additive,
    // separate from `buildings` (the physical PlacedBuilding list) so
    // CitySummary.buildingCount's existing meaning doesn't shift.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<Building> simulatedBuildings = new ArrayList<>();

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public GenerationConfig getGenerationConfig() {
        return generationConfig;
    }

    public void setGenerationConfig(GenerationConfig generationConfig) {
        this.generationConfig = generationConfig;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<GridCell> getGrid() {
        return grid;
    }

    public void setGrid(List<GridCell> grid) {
        this.grid = grid;
    }

    public List<Lot> getLots() {
        return lots;
    }

    public void setLots(List<Lot> lots) {
        this.lots = lots;
    }

    public List<RoadSegment> getRoads() {
        return roads;
    }

    public void setRoads(List<RoadSegment> roads) {
        this.roads = roads;
    }

    public List<PlacedBuilding> getBuildings() {
        return buildings;
    }

    public void setBuildings(List<PlacedBuilding> buildings) {
        this.buildings = buildings;
    }

    public List<Building> getSimulatedBuildings() {
        return simulatedBuildings;
    }

    public void setSimulatedBuildings(List<Building> simulatedBuildings) {
        this.simulatedBuildings = simulatedBuildings;
    }
}
