package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    BigDecimal grossIncome;
    BigDecimal personalSavings;
    BigDecimal personalDebt;
    BigDecimal personalCreditLimit;
    BigDecimal averageInterestRate;

    //  STATUS MODIFIERS
    @ManyToOne(optional = true)
    @JoinColumn(name = "OccupationId")
    Occupation occupation;
    boolean employed;
    boolean employable;
    boolean retired;

    //  LIFECYCLE
    LocalDate birthDate;
    LocalDate deathDate;

    @Enumerated(EnumType.STRING)
    EducationLevel educationLevel;

}
