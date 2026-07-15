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

    public long getId() {
        return id;
    }

    public Set<Inhabitant> getWorkers() {
        return workers;
    }

    public void setWorkers(Set<Inhabitant> workers) {
        this.workers = workers;
    }

    public BigDecimal getAverageSalary() {
        return averageSalary;
    }

    public void setAverageSalary(BigDecimal averageSalary) {
        this.averageSalary = averageSalary;
    }

    public BigDecimal getStandardDeviation() {
        return standardDeviation;
    }

    public void setStandardDeviation(BigDecimal standardDeviation) {
        this.standardDeviation = standardDeviation;
    }

    public EducationLevel getRequiredEducation() {
        return requiredEducation;
    }

    public void setRequiredEducation(EducationLevel requiredEducation) {
        this.requiredEducation = requiredEducation;
    }

    public BigDecimal getDesirability() {
        return desirability;
    }

    public void setDesirability(BigDecimal desirability) {
        this.desirability = desirability;
    }

    public BigDecimal getRecessionResistance() {
        return recessionResistance;
    }

    public void setRecessionResistance(BigDecimal recessionResistance) {
        this.recessionResistance = recessionResistance;
    }

    public int getCurrentDemand() {
        return currentDemand;
    }

    public void setCurrentDemand(int currentDemand) {
        this.currentDemand = currentDemand;
    }

    public int getCurrentSupply() {
        return currentSupply;
    }

    public void setCurrentSupply(int currentSupply) {
        this.currentSupply = currentSupply;
    }
}
