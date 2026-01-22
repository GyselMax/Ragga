package be.ragga.raggabackend.simulation.household;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.util.Set;

@Entity
public class Household {
    @Id
    long Id;
    //  LONGER TERM BUDGETARY EFFECTS
    BigDecimal savings;
    BigDecimal debt;

    //  BUDGET BREAKDOWN
    BigDecimal grossIncome;
    BigDecimal foodBudget;
    BigDecimal RetailBudget;
    BigDecimal LuxuryBudget;

    //  MEMBERS (ONE-TO-MANY)
    Set<Inhabitant> members;
}
