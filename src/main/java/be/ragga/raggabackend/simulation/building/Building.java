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
}