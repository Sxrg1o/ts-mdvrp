package mdvrp.simulation;

import mdvrp.model.CustomerPart;
import mdvrp.model.Depot;
import mdvrp.model.Location;
import mdvrp.model.Truck;
import mdvrp.state.GlobalState;

import java.awt.*;
import java.util.*;
import java.util.List;

import static mdvrp.state.GlobalState.*;

public class SimulationUtils {

    public static double MINUTOS_POR_KM = 60.0 / VELOCIDAD_KMH;

    public static String formatCost(double cost) {
        if (cost == Double.POSITIVE_INFINITY) return "INF";
        return String.format("%.2f Gal", cost);
    }

    public static String formatTime(int totalMinutes) {
        int days = totalMinutes / (24 * 60);
        int remainingMinutes = totalMinutes % (24 * 60);
        int hours = remainingMinutes / 60;
        int minutes = remainingMinutes % 60;
        return String.format("%dd %02dh %02dm", days, hours, minutes);
    }

    public static double calculateFuelConsumed(int d, double load, Truck t) {
        if(d==0) return 0;
        double pc=t.type.calcularPesoCargaActualTon(load);
        double pt=t.type.taraTon+pc;
        return (double)d*pt/180.0;
    }

    public static int distanciaReal(Location f, Location t) {
        if(f==null || t==null) return Integer.MAX_VALUE;
        if(f.x<0||f.x>=GRID_WIDTH||f.y<0||f.y>=GRID_HEIGHT) return Integer.MAX_VALUE;
        if(t.x<0||t.x>=GRID_WIDTH||t.y<0||t.y>=GRID_HEIGHT) return Integer.MAX_VALUE;
        Queue<int[]> q=new LinkedList<>(); Map<Point,Integer> ds=new HashMap<>(); Set<Point> v=new HashSet<>();
        Point s=f.toPoint(), e=t.toPoint(); if(s.equals(e)) return 0;
        q.add(new int[]{f.x,f.y}); ds.put(s,0); v.add(s); int[][] DIRS={{0,1},{0,-1},{1,0},{-1,0}};
        while(!q.isEmpty()){ int[] c=q.poll(); Point p=new Point(c[0],c[1]); int d=ds.get(p);
            for(int[] dir:DIRS){ int nx=c[0]+dir[0],ny=c[1]+dir[1]; Point n=new Point(nx,ny);
                if(n.equals(e)) return d+1;
                if(nx>=0&&nx<GRID_WIDTH&&ny>=0&&ny<GRID_HEIGHT&&!v.contains(n)&&!blockedNodes[nx][ny]){
                    v.add(n); ds.put(n,d+1); q.add(new int[]{nx,ny});}
            }
        }
        return Integer.MAX_VALUE;
    }

    public static double calculateRequiredLoadForPlan(List<Object> plan) {
        double requiredLoad = 0;
        for (Object step : plan) {
            if (step instanceof CustomerPart) {
                requiredLoad += ((CustomerPart) step).demandM3;
            }
        }
        return requiredLoad;
    }

    public Depot findBestDepotForReload(Location currentLocation, double minRequiredGLP) {
        Depot bestDepot = null;
        int minDistance = Integer.MAX_VALUE;
        for (Depot depot : GlobalState.depots) {
            if (depot.isMainPlant()) continue;
            if (depot.capacidadActualM3 < minRequiredGLP - 0.01) continue;
            int distance = distanciaReal(currentLocation, depot);
            if (distance != Integer.MAX_VALUE && distance < minDistance) {
                minDistance = distance;
                bestDepot = depot;
            }
        }
        return bestDepot;
    }

}
