package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Household {
    @Id
    long Id;
    //  LONGER TERM BUDGETARY EFFECTS
    BigDecimal SharedSavings;
    BigDecimal SharedDebt;

    //  BUDGET BREAKDOWN
    BigDecimal totalGrossIncome;
    BigDecimal foodBudget;
    BigDecimal RetailBudget;
    BigDecimal LuxuryBudget;

    //  MEMBERS (ONE-TO-MANY)
    @OneToMany
    Set<Inhabitant> members;
    //  MEMBER LEAVES WHEN (FULL-TIME JOB/30/MARRIED)
}
