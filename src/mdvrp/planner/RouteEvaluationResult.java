package mdvrp.planner;

public class RouteEvaluationResult {
    public double cost = Double.POSITIVE_INFINITY;
    public double fuel = Double.POSITIVE_INFINITY;
    public boolean feasible = false;
    public int endTime = -1;
    public int hypotheticalReloads = 0;
    public double penaltyCost = 0;
    public int extraTimeFromReloads = 0;
}