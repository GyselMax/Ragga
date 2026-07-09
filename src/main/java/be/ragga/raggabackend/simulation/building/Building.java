package be.ragga.raggabackend.simulation.building;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private BigDecimal price;
    private BigDecimal rent;
    private BigDecimal desirability;
    private int sizeX;
    private int sizeY;

    // Vertical extent in floors, copied from the blueprint at creation time.
    // Footprint (sizeX/sizeY) rotates with placement; floors never do.
    private int floors;

    protected Building() {
    }

    protected Building(int sizeX, int sizeY, int floors) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.floors = floors;
    }

    public long getId() {
        return id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getRent() {
        return rent;
    }

    public void setRent(BigDecimal rent) {
        this.rent = rent;
    }

    public BigDecimal getDesirability() {
        return desirability;
    }

    public void setDesirability(BigDecimal desirability) {
        this.desirability = desirability;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getFloors() {
        return floors;
    }
}
