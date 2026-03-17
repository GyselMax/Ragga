package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Occupation {
    @Id
    long Id;

    @OneToMany(mappedBy = "occupation")
    Set<Inhabitant> workers;

    //  JOB
    BigDecimal averageSalary;
    BigDecimal standardDeviation;
    @Enumerated(EnumType.STRING)
    EducationLevel requiredEducation;

    //  ECONOMIC FACTORS
    BigDecimal desirability;
    BigDecimal recessionResistance;

    //  DEMAND
    int currentDemand;
    int currentSupply;

}
