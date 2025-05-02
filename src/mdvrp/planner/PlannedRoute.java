package mdvrp.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mdvrp.model.*;

public class PlannedRoute {
    public Truck truck;
    public Depot startDepot;
    public List<CustomerPart> sequence = new ArrayList<>();
    public Depot endDepot;
    public double cost = Double.POSITIVE_INFINITY;
    public boolean feasible = false;
    public double estimatedFuel = Double.POSITIVE_INFINITY;

    public PlannedRoute(Truck t, Depot d) {
        truck = t; startDepot = d; endDepot = d;
    }

    public PlannedRoute(PlannedRoute other) {
        this.truck = other.truck;
        this.startDepot = other.startDepot;
        this.sequence = new ArrayList<>(other.sequence);
        this.endDepot = other.endDepot;
        this.cost = other.cost;
        this.feasible = other.feasible;
        this.estimatedFuel = other.estimatedFuel;
    }

    @Override public String toString() {
        String p=sequence.stream().map(cp->String.valueOf(cp.partId)).collect(Collectors.joining("->"));
        return "PRoute["+truck.id+":"+startDepot.id+"->"+p+"->"+endDepot.id+"]";
    }
}