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

    public long getId() {
        return id;
    }

    public BigDecimal getDistanceTolerance() {
        return distanceTolerance;
    }

    public void setDistanceTolerance(BigDecimal distanceTolerance) {
        this.distanceTolerance = distanceTolerance;
    }

    public BigDecimal getSubstitutionTendency() {
        return substitutionTendency;
    }

    public void setSubstitutionTendency(BigDecimal substitutionTendency) {
        this.substitutionTendency = substitutionTendency;
    }

    public BigDecimal getPriceSensitivity() {
        return priceSensitivity;
    }

    public void setPriceSensitivity(BigDecimal priceSensitivity) {
        this.priceSensitivity = priceSensitivity;
    }

    public BigDecimal getRiskTolerance() {
        return riskTolerance;
    }

    public void setRiskTolerance(BigDecimal riskTolerance) {
        this.riskTolerance = riskTolerance;
    }

    public BigDecimal getGrossIncome() {
        return grossIncome;
    }

    public void setGrossIncome(BigDecimal grossIncome) {
        this.grossIncome = grossIncome;
    }

    public BigDecimal getPersonalSavings() {
        return personalSavings;
    }

    public void setPersonalSavings(BigDecimal personalSavings) {
        this.personalSavings = personalSavings;
    }

    public BigDecimal getPersonalDebt() {
        return personalDebt;
    }

    public void setPersonalDebt(BigDecimal personalDebt) {
        this.personalDebt = personalDebt;
    }

    public BigDecimal getPersonalCreditLimit() {
        return personalCreditLimit;
    }

    public void setPersonalCreditLimit(BigDecimal personalCreditLimit) {
        this.personalCreditLimit = personalCreditLimit;
    }

    public BigDecimal getAverageInterestRate() {
        return averageInterestRate;
    }

    public void setAverageInterestRate(BigDecimal averageInterestRate) {
        this.averageInterestRate = averageInterestRate;
    }

    public Household getHousehold() {
        return household;
    }

    public void setHousehold(Household household) {
        this.household = household;
    }

    public HouseholdRole getHouseholdRole() {
        return householdRole;
    }

    public void setHouseholdRole(HouseholdRole householdRole) {
        this.householdRole = householdRole;
    }

    public Occupation getOccupation() {
        return occupation;
    }

    public void setOccupation(Occupation occupation) {
        this.occupation = occupation;
    }

    public boolean isEmployed() {
        return employed;
    }

    public void setEmployed(boolean employed) {
        this.employed = employed;
    }

    public boolean isEmployable() {
        return employable;
    }

    public void setEmployable(boolean employable) {
        this.employable = employable;
    }

    public boolean isRetired() {
        return retired;
    }

    public void setRetired(boolean retired) {
        this.retired = retired;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public LocalDate getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(LocalDate deathDate) {
        this.deathDate = deathDate;
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
    }
}
