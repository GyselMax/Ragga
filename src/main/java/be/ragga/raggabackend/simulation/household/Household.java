package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private BigDecimal sharedSavings;
    private BigDecimal sharedDebt;
    private BigDecimal totalGrossIncome;
    private BigDecimal foodBudget;
    private BigDecimal retailBudget;
    private BigDecimal luxuryBudget;

    @OneToMany(mappedBy = "household")
    private Set<Inhabitant> members;
}