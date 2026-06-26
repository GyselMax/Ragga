package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Occupation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToMany(mappedBy = "occupation")
    private Set<Inhabitant> workers;

    //  JOB
    private BigDecimal averageSalary;
    private BigDecimal standardDeviation;

    @Enumerated(EnumType.STRING)
    private EducationLevel requiredEducation;

    //  ECONOMIC FACTORS
    private BigDecimal desirability;
    private BigDecimal recessionResistance;

    //  DEMAND
    private int currentDemand;
    private int currentSupply;
}