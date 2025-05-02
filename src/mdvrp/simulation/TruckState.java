package mdvrp.simulation;

import mdvrp.model.Location;
import mdvrp.model.Truck;
import mdvrp.planner.PlannedRoute;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static mdvrp.state.GlobalState.MAX_FUEL_GAL;

public class TruckState {
    Truck truck;
    public enum Status { IDLE, PRE_TRIP, EN_ROUTE, EN_ROUTE_TO_RELOAD, DISCHARGING, RETURNING, INACTIVE }
    public Status status = Status.IDLE;
    Location currentLocation;
    double currentLoadM3 = 0.0;
    double currentFuelGal;
    public int timeAvailable;
    List<Object> currentRoutePlan = new LinkedList<>();
    Location destination = null;
    int arrivalTimeAtDestination = -1;
    public List<PlannedRoute> routes;

    public TruckState(Truck truck) {
        this.truck = truck;
        this.currentLocation = truck.homeDepot;
        this.currentFuelGal = MAX_FUEL_GAL;
        this.timeAvailable = 0;
        this.routes = new ArrayList<>();
    }
}