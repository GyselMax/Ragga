package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Job {
    @Id
    long Id;

    //  INHABITANTS
    @OneToMany(mappedBy = "job")
    Set<Inhabitant> inhabitants;

    //  JOB
    BigDecimal averageSalary;
    BigDecimal standardDeviation;

    //  DESCRIPTION
    String description;
    private EducationLevel educationLevel;
    BigDecimal desirability;
    BigDecimal recessionResistance;


}
