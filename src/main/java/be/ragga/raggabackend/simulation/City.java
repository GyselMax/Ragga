package be.ragga.raggabackend.simulation;

import be.ragga.raggabackend.simulation.grid.GridCell;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "city_id")
    private List<GridCell> grid = new ArrayList<>();

    public long getId() {
        return id;
    }

    public List<GridCell> getGrid() {
        return grid;
    }

    public void setGrid(List<GridCell> grid) {
        this.grid = grid;
    }
}
