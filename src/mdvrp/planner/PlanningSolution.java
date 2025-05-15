package mdvrp.planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mdvrp.model.*;

public class PlanningSolution {
    public List<PlannedRoute> routes = new ArrayList<>();
    public Set<CustomerPart> unassignedParts = new HashSet<>();
    public double totalCost = Double.POSITIVE_INFINITY;
    public boolean fullyFeasible = false;
    public double operationalFuelCost = 0.0;
    public int totalSolutionTimeSlackMinutes = 0; // Nuevo campo

    public PlanningSolution() {}
    public PlanningSolution(PlanningSolution other) {
        this.routes = new ArrayList<>();
        for (PlannedRoute r : other.routes) {
            this.routes.add(new PlannedRoute(r));
        }
        this.unassignedParts = new HashSet<>(other.unassignedParts);
        this.totalCost = other.totalCost;
        this.fullyFeasible = other.fullyFeasible;
        this.operationalFuelCost = other.operationalFuelCost;
        this.totalSolutionTimeSlackMinutes = other.totalSolutionTimeSlackMinutes; // Copiar nuevo campo
    }
}