package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Inhabitant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    //  SHOP CHOICE MODIFIERS
    private BigDecimal distanceTolerance;
    private BigDecimal substitutionTendency;
    private BigDecimal priceSensitivity;
    private BigDecimal riskTolerance;

    //  BUDGET MODIFIERS
    private BigDecimal grossIncome;
    private BigDecimal personalSavings;
    private BigDecimal personalDebt;
    private BigDecimal personalCreditLimit;
    private BigDecimal averageInterestRate;

    //  HOUSEHOLD
    @ManyToOne
    @JoinColumn(name = "household_id")
    private Household household;

    //  ROLE IN HOUSEHOLD
    @Enumerated(EnumType.STRING)
    private HouseholdRole householdRole;

    //  STATUS MODIFIERS
    @ManyToOne(optional = true)
    @JoinColumn(name = "occupation_id")
    private Occupation occupation;
    private boolean employed;
    private boolean employable;
    private boolean retired;

    //  LIFECYCLE
    private LocalDate birthDate;
    private LocalDate deathDate;

    @Enumerated(EnumType.STRING)
    private EducationLevel educationLevel;
}