package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Inhabitant {
    @Id
    long Id;

    //  SHOP CHOICE MODIFIERS
    BigDecimal distanceTolerance;
    BigDecimal substitutionTendency;
    BigDecimal priceSensitivity;
    BigDecimal riskTolerance;

    //  BUDGET MODIFIERS
    BigDecimal income;
    BigDecimal savings;
    BigDecimal debt;
    BigDecimal personalCreditLimit;
    BigDecimal averageInterestRate;

    //  STATUS MODIFIERS
    @ManyToOne(optional = true)
    @JoinColumn(name = "jobId")
    Job job;
    boolean employed;
    boolean employable;
    boolean retired;

}
