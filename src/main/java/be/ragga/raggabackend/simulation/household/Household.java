package be.ragga.raggabackend.simulation.household;

import be.ragga.raggabackend.simulation.building.Building;
import be.ragga.raggabackend.simulation.owner.Owner;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

// A household is an Owner: an owner-occupier holds its own home, and a private
// landlord holds several. Company-owned rental blocks and player ownership use
// the same Building.owner relationship (see Owner).
@Entity
public class Household extends Owner {

    private BigDecimal sharedSavings;
    private BigDecimal sharedDebt;
    private BigDecimal totalGrossIncome;
    private BigDecimal foodBudget;
    private BigDecimal retailBudget;
    private BigDecimal luxuryBudget;

    @OneToMany(mappedBy = "household")
    private Set<Inhabitant> members;

    // --- Housing (filled by the market-clearing pass at generation time) ---

    // OWN (owner-occupier) or RENT (tenant). Null until the household is housed.
    @Enumerated(EnumType.STRING)
    private Tenure tenure;

    // The building this household lives in. The building carries no coordinates
    // itself; its grid position is on the PlacedBuilding that points AT it
    // (PlacedBuilding -> Building is the only mapped direction), so recovering a
    // household's location is a reverse lookup by residence, not a field walk.
    // Household therefore needs no coordinates of its own. Null while unhoused.
    @ManyToOne
    @JoinColumn(name = "residence_building_id")
    private Building residence;

    // True when rent sits in the 30-40% of income "housing cost overburden"
    // band (Eurostat). Above 40% the market never houses a renter, so this flag
    // only ever marks the moderate-stress band, not an unaffordable placement.
    private boolean housingCostOverburdened;

    public BigDecimal getSharedSavings() {
        return sharedSavings;
    }

    public void setSharedSavings(BigDecimal sharedSavings) {
        this.sharedSavings = sharedSavings;
    }

    public BigDecimal getSharedDebt() {
        return sharedDebt;
    }

    public void setSharedDebt(BigDecimal sharedDebt) {
        this.sharedDebt = sharedDebt;
    }

    public BigDecimal getTotalGrossIncome() {
        return totalGrossIncome;
    }

    public void setTotalGrossIncome(BigDecimal totalGrossIncome) {
        this.totalGrossIncome = totalGrossIncome;
    }

    public BigDecimal getFoodBudget() {
        return foodBudget;
    }

    public void setFoodBudget(BigDecimal foodBudget) {
        this.foodBudget = foodBudget;
    }

    public BigDecimal getRetailBudget() {
        return retailBudget;
    }

    public void setRetailBudget(BigDecimal retailBudget) {
        this.retailBudget = retailBudget;
    }

    public BigDecimal getLuxuryBudget() {
        return luxuryBudget;
    }

    public void setLuxuryBudget(BigDecimal luxuryBudget) {
        this.luxuryBudget = luxuryBudget;
    }

    public Set<Inhabitant> getMembers() {
        return members;
    }

    public void setMembers(Set<Inhabitant> members) {
        this.members = members;
    }

    public Tenure getTenure() {
        return tenure;
    }

    public void setTenure(Tenure tenure) {
        this.tenure = tenure;
    }

    public Building getResidence() {
        return residence;
    }

    public void setResidence(Building residence) {
        this.residence = residence;
    }

    public boolean isHousingCostOverburdened() {
        return housingCostOverburdened;
    }

    public void setHousingCostOverburdened(boolean housingCostOverburdened) {
        this.housingCostOverburdened = housingCostOverburdened;
    }
}
