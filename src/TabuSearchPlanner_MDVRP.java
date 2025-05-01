import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TabuSearchPlanner_MDVRP {
    // Constantes del problema principal
    static final int GRID_WIDTH = 70;
    static final int GRID_HEIGHT = 50;
    static final double VELOCIDAD_KMH = 50.0;
    static final double MINUTOS_POR_KM = 60.0 / VELOCIDAD_KMH;
    static final int PRE_TRIP_CHECK_MINUTES = 15;
    static final int DISCHARGE_TIME_MINUTES = 15;
    static final double MAX_FUEL_GAL = 25.0;
    static final double MAX_TRUCK_CAPACITY_M3 = 25.0;

    // Clases principales

    static class Truck {
        String id; TruckType type; Depot homeDepot; boolean disponible;
        Location currentLocation; double cargaActualM3;
        Truck(String id, TruckType type, Depot homeDepot) {
            this.id = id; this.type = type; this.homeDepot = homeDepot;
            this.disponible = true; this.currentLocation = homeDepot; this.cargaActualM3 = 0;
        }
        @Override public String toString() { return "Truck[" + id + "]"; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Truck truck = (Truck) o;
            return Objects.equals(id, truck.id);
        }
        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    static class Location {
        int x, y;
        Location(int x, int y) { this.x = x; this.y = y; }
        Point toPoint() { return new Point(x, y); }
        @Override public String toString() { return "(" + x + "," + y + ")"; }
        @Override public boolean equals(Object o) { if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Location loc = (Location) o; return x == loc.x && y == loc.y; }
        @Override public int hashCode() { return Objects.hash(x, y); }
        int distanceTo(Location other) { return distanciaReal(this, other); }
    }

    static class Depot extends Location {
        String id; double capacidadActualM3; final double capacidadMaximaM3;
        Depot(String id, int x, int y, double capMax) {
            super(x, y); this.id = id;
            this.capacidadMaximaM3 = capMax;
            this.capacidadActualM3 = capMax;
        }
        @Override public String toString() { return "Depot[" + id + "]"; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Depot depot = (Depot) o;
            return Objects.equals(id, depot.id);
        }
        @Override public int hashCode() { return Objects.hash(id); }
        boolean isMainPlant() { return capacidadMaximaM3 == Double.POSITIVE_INFINITY;}
    }

    enum TruckType {
        TA(2.5, 25.0, 12.5), TB(2.0, 15.0, 7.5), TC(1.5, 10.0, 5.0), TD(1.0, 5.0, 2.5);
        final double taraTon; final double capacidadM3; final double pesoCargaMaxTon;
        TruckType(double t, double c, double p){taraTon=t; capacidadM3=c; pesoCargaMaxTon=p;}
        double calcularPesoCargaActualTon(double cargaM3){
            if(capacidadM3==0) return 0;
            return Math.min(1.0, cargaM3 / capacidadM3) * pesoCargaMaxTon;
        }
    }

    // Necesario para manejar la división de pedidos grandes.
    static class CustomerPart extends Location {
        int partId;         // ID único de esta parte
        int originalOrderId;// ID para agrupar partes del mismo pedido original
        double demandM3;    // Demanda de esta parte específica
        int arrivalTimeMinutes;
        int deadlineMinutes;
        boolean served = false;

        static int nextPartId = 0;

        CustomerPart(int originalOrderId, int x, int y, double demand, int tArrival, int deadline) {
            super(x, y);
            this.partId = nextPartId++;
            this.originalOrderId = originalOrderId;
            this.demandM3 = demand;
            this.arrivalTimeMinutes = tArrival;
            this.deadlineMinutes = deadline;
        }
        @Override public String toString() {
            return "CustPart[" + partId + "(Orig:" + originalOrderId + ")@" + super.toString() + ", Dem:" + demandM3 + ", Lim:" + deadlineMinutes + "]";
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomerPart that = (CustomerPart) o;
            return partId == that.partId;
        }
        @Override public int hashCode() { return Objects.hash(partId); }
    }

    // Estado de un camión en la simulación
    static class TruckState {
        Truck truck;
        enum Status { IDLE, PRE_TRIP, EN_ROUTE, DISCHARGING, RETURNING, INACTIVE }
        Status status = Status.IDLE;
        Location currentLocation;
        double currentLoadM3 = 0.0;
        double currentFuelGal;
        int timeAvailable;
        List<Object> currentRoutePlan = new LinkedList<>();
        Location destination = null;
        int arrivalTimeAtDestination = -1;

        TruckState(Truck truck) {
            this.truck = truck;
            this.currentLocation = truck.homeDepot;
            this.currentFuelGal = MAX_FUEL_GAL;
            this.timeAvailable = 0;
        }
    }

    // Representa una ruta planificada para Tabu Search
    static class PlannedRoute {
        Truck truck;
        Depot startDepot;
        List<CustomerPart> sequence = new ArrayList<>();
        Depot endDepot;
        double cost = Double.POSITIVE_INFINITY;
        boolean feasible = false;
        double estimatedFuel = Double.POSITIVE_INFINITY;

        PlannedRoute(Truck t, Depot d) {
            truck = t; startDepot = d; endDepot = d;
        }

        PlannedRoute(PlannedRoute other) {
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

    // Representa la solución completa de planificación
    static class PlanningSolution {
        List<PlannedRoute> routes = new ArrayList<>();
        Set<CustomerPart> unassignedParts = new HashSet<>();
        double totalCost = Double.POSITIVE_INFINITY;
        boolean fullyFeasible = false;
        double operationalFuelCost = 0.0;

        PlanningSolution() {}
        PlanningSolution(PlanningSolution other) {
            this.routes = new ArrayList<>();
            for (PlannedRoute r : other.routes) {
                this.routes.add(new PlannedRoute(r));
            }
            this.unassignedParts = new HashSet<>(other.unassignedParts);
            this.totalCost = other.totalCost;
            this.fullyFeasible = other.fullyFeasible;
            this.operationalFuelCost = other.operationalFuelCost;
        }
    }

    // Estado global de la simulación
    static boolean[][] blockedNodes = new boolean[GRID_WIDTH][GRID_HEIGHT];
    static List<Depot> depots = new ArrayList<>();
    static List<Truck> fleet = new ArrayList<>();
    static Map<String, TruckState> truckStates = new HashMap<>();
    static List<CustomerPart> activeCustomerParts = new ArrayList<>();
    static List<Pedido> pendingPedidos;
    static List<Bloqueo> definedBloqueos;
    static int currentSimTime = 0;

    // Parámetros y lista tabú
    static final int TS_MAX_ITERATIONS = 1000; // Iteraciones para Tabu Search
    static final int TS_TABU_TENURE = 15;      // Duración tabú (unificada)

    interface Move { }

    static class Move_2Opt implements Move {
        String truckId; int custIdx1, custIdx2;
        Move_2Opt(String t, int i1, int i2){truckId=t; custIdx1=Math.min(i1,i2); custIdx2=Math.max(i1,i2);}
        @Override public String toString(){return "2Opt(T:"+truckId+",P:"+custIdx1+","+custIdx2+")";}
        @Override public boolean equals(Object o){
            if(this==o) return true;
            if(o==null||getClass()!=o.getClass()) return false;
            Move_2Opt m=(Move_2Opt)o; return custIdx1==m.custIdx1 && custIdx2==m.custIdx2 && Objects.equals(truckId, m.truckId);}
        @Override public int hashCode(){ return Objects.hash(truckId, custIdx1, custIdx2); }
    }

    static class Move_Relocate implements Move {
        int customerPartId; String sourceTruckId; String destTruckId;
        Move_Relocate(int cpId, String src, String dest){customerPartId=cpId; sourceTruckId=src; destTruckId=dest;}
        @Override public String toString(){return "Reloc(CP:"+customerPartId+",From:"+sourceTruckId+",To:"+destTruckId+")";}
        @Override public boolean equals(Object o){
            if(this==o) return true;
            if(o==null||getClass()!=o.getClass()) return false;
            Move_Relocate m=(Move_Relocate)o; return customerPartId==m.customerPartId && Objects.equals(destTruckId,m.destTruckId);}
        @Override public int hashCode(){ return Objects.hash(customerPartId, destTruckId); }
    }

    static Queue<Move> tabuQueue = new LinkedList<>();
    static Set<Move> tabuSet = new HashSet<>();

    public static void main(String[] args) throws Exception {
        initializeSimulation();

        int simulationDuration = 7 * 24 * 60; // 1 semana
        boolean replanOnNewOrder = false; // Cambiar a true para replanificación dinámica (todavía no, me da miedo)

        runSimulation(simulationDuration, replanOnNewOrder);

        PlanningSolution finalSolution = null;
        List<CustomerPart> finalUnserved = getUnservedCustomerParts();
        if (!finalUnserved.isEmpty()) {
            System.out.println("\n🚀 Planificación Final con Tabu Search... (" + finalUnserved.size() + " partes de cliente)");
            finalSolution = planRoutes(finalUnserved, 0);
        } else {
            System.out.println("🤷 No hay clientes activos/pendientes para planificar al final.");
        }

        if (finalSolution != null && !finalSolution.routes.isEmpty()) {
            System.out.println("\n📈 Mostrando visualización de la solución final...");
            visualizarSolucion(finalSolution);
        } else if (finalSolution != null && !finalSolution.unassignedParts.isEmpty()) {
            System.out.println(" T_T No se pudieron asignar todas las partes. Sin asignar: " + finalSolution.unassignedParts.size());
        } else {
            System.out.println(" T_T No hay solución final generada o rutas para visualizar.");
        }
        System.out.println("\nPrograma terminado.");
    }

    static String formatCost(double cost) {
        if (cost == Double.POSITIVE_INFINITY) return "INF";
        return String.format("%.2f Gal", cost);
    }

    // Simulación
    static void runSimulation(int durationMinutes, boolean enableReplanning) {
        System.out.println("--- Iniciando Simulación por " + durationMinutes + " minutos ---");
        while (currentSimTime <= durationMinutes) {
            // Procesar eventos del minuto actual
            boolean newOrderActivated = activateNewPedidos(currentSimTime);
            boolean blockadeChanged = updateBlockages(currentSimTime);
            refillIntermediateDepotsIfNeeded(currentSimTime);

            // Actualizar estado de los camiones (útil luego para averías, etc)
            updateTrucks(currentSimTime);

            // Replanificación (TODAVIA)
            if (enableReplanning && (newOrderActivated /*|| truckBecameInactive */ )) {
                List<CustomerPart> unservedParts = getUnservedCustomerParts();
                if (!unservedParts.isEmpty()) {
                    System.out.println("\n=== REPLANIFICANDO RUTAS en t=" + currentSimTime + " para " + unservedParts.size() + " partes ===");
                    // La replanificación debería considerar el estado ACTUAL de los camiones
                    PlanningSolution replannedSolution = planRoutes(unservedParts, currentSimTime); // Pasar tiempo actual
                    if (replannedSolution != null) {
                        System.out.println("  (Replanificación completada, costo: " + formatCost(replannedSolution.totalCost) + ", Sin asignar: " + replannedSolution.unassignedParts.size() + ")");
                        // APLICAR la nueva planificación a los camiones IDLE o que puedan ser redirigidos
                        applyPlannedRoutes(replannedSolution, currentSimTime);
                    } else {
                        System.err.println("  (Replanificación falló o no fue necesaria)");
                    }
                }
            }

            if (currentSimTime > 0 && currentSimTime % 60 == 0) {
                System.out.println("--- Tiempo: " + formatTime(currentSimTime) + " --- (" + activeCustomerParts.size() + " partes activas)");
            }

            currentSimTime++;
        }
        System.out.println("\n✅ Simulación finalizada en minuto " + (currentSimTime - 1));
    }

    static void initializeSimulation() throws Exception {
        System.out.println("Inicializando simulación...");
        pendingPedidos = cargarPedidos("pedidos.txt");
        definedBloqueos = cargarBloqueos("bloqueos.txt");
        blockedNodes = new boolean[GRID_WIDTH][GRID_HEIGHT];
        activeCustomerParts.clear();
        currentSimTime = 0;
        fleet.clear();
        depots.clear();
        truckStates.clear();

        depots.add(new Depot("Planta", 12, 8, Double.POSITIVE_INFINITY));
        depots.add(new Depot("Norte", 42, 42, 160.0));
        depots.add(new Depot("Este", 63, 3, 160.0));
        System.out.println("Depósitos creados: " + depots.size());

        Depot mainDepot = depots.get(0);
        int count = 0;
        String[] types = {"TA", "TB", "TC", "TD"};
        int[] counts = {2, 4, 4, 10};
        TruckType[] enumTypes = {TruckType.TA, TruckType.TB, TruckType.TC, TruckType.TD};

        for (int typeIdx = 0; typeIdx < types.length; typeIdx++) {
            for (int i = 1; i <= counts[typeIdx]; i++) {
                String truckId = String.format("%s%02d", types[typeIdx], i);
                Truck truck = new Truck(truckId, enumTypes[typeIdx], mainDepot);
                fleet.add(truck);
                truckStates.put(truckId, new TruckState(truck));
                count++;
            }
        }
        System.out.println("Flota creada: " + count + " camiones.");
        System.out.println("Inicialización completa.");
    }

    static boolean activateNewPedidos(int minute) {
        boolean added = false;
        Iterator<Pedido> it = pendingPedidos.iterator();
        int originalOrderId = activeCustomerParts.size();

        while (it.hasNext()) {
            Pedido p = it.next();
            if (p.momentoPedido == minute) {
                originalOrderId++;
                double remainingDemand = p.volumen;
                int partCount = 0;
                // Dividir pedido si es necesario
                while (remainingDemand > 0) {
                    partCount++;
                    double partDemand = Math.min(remainingDemand, MAX_TRUCK_CAPACITY_M3);
                    CustomerPart part = new CustomerPart(originalOrderId, p.x, p.y, partDemand,
                            p.momentoPedido, p.momentoPedido + p.horaLimite * 60);
                    activeCustomerParts.add(part);
                    remainingDemand -= partDemand;
                    System.out.println("⏰ t=" + minute + " -> Nueva Parte Pedido ID:" + part.partId + "(Orig:"+originalOrderId+"."+partCount+") en " + part + " recibida.");
                }
                it.remove();
                added = true;
            } else if (p.momentoPedido > minute) {
                // break; // Si está ordenado
            }
        }
        return added;
    }

    static boolean updateBlockages(int minute) {
        boolean changed = false;
        for (Bloqueo b : definedBloqueos) {
            boolean currentState = blockedNodes[b.puntosBloqueados.get(0)[0]][b.puntosBloqueados.get(0)[1]];
            boolean shouldBeActive = minute >= b.inicioMinutos && minute < b.finMinutos;
            if (currentState != shouldBeActive) {
                changed = true;
                activateOrDeactivateBloqueo(b, shouldBeActive);
                // System.out.println("Bloqueo en " + b.puntosBloqueados.get(0)[0]+","+b.puntosBloqueados.get(0)[1] + " cambiado a " + shouldBeActive + " en t="+minute);
            }
        }
        return changed;
    }

    static void activateOrDeactivateBloqueo(Bloqueo b, boolean activate) {
        for (int[] punto : b.puntosBloqueados) {
            if (punto[0] >= 0 && punto[0] < GRID_WIDTH && punto[1] >= 0 && punto[1] < GRID_HEIGHT) {
                blockedNodes[punto[0]][punto[1]] = activate;
            }
        }
    }

    static void refillIntermediateDepotsIfNeeded(int minute) {
        if (minute > 0 && minute % (24 * 60) == 0) { // A las 00:00 de cada día (excepto inicio)
            System.out.println("--- Medianoche día " + (minute / (24 * 60)) + ": Reabasteciendo Depósitos Intermedios ---");
            for (Depot d : depots) {
                if (!d.isMainPlant()) {
                    d.capacidadActualM3 = d.capacidadMaximaM3;
                }
            }
        }
    }

    static void updateTrucks(int minute) {
        for (TruckState ts : truckStates.values()) {
            if (ts.status == TruckState.Status.INACTIVE || minute < ts.timeAvailable) {
                continue;
            }

            // Procesar estado actual y transicionar
            switch (ts.status) {
                case IDLE:
                    if (!ts.currentRoutePlan.isEmpty()) {
                        System.out.println("Truck " + ts.truck.id + " iniciando PRE_TRIP en t=" + minute);
                        ts.status = TruckState.Status.PRE_TRIP;
                        ts.timeAvailable = minute + PRE_TRIP_CHECK_MINUTES;
                        ts.currentLoadM3 = calculateRequiredLoadForPlan(ts.currentRoutePlan);
                        if (ts.currentLoadM3 > ts.truck.type.capacidadM3) {
                            System.err.println("ERROR: Plan asigna carga > capacidad a " + ts.truck.id);
                            ts.currentRoutePlan.clear(); // Abortar plan
                            ts.status = TruckState.Status.IDLE;
                            ts.timeAvailable = minute;
                        } else {
                            System.out.println("  Truck " + ts.truck.id + " cargado con " + ts.currentLoadM3 + " m3.");
                        }
                        // Recarga de combustible en Home Depot
                        if(ts.currentLocation.equals(ts.truck.homeDepot)) {
                            if(ts.currentFuelGal < MAX_FUEL_GAL) {
                                // System.out.println("  Truck " + ts.truck.id + " recargando combustible.");
                                ts.currentFuelGal = MAX_FUEL_GAL;
                            }
                        }
                        // FALTA: Lógica de recarga en tanque intermedio (pendiente)
                    }
                    break;

                case PRE_TRIP:
                    Object nextDestinationObj = ts.currentRoutePlan.get(0);
                    if (nextDestinationObj instanceof Location) {
                        ts.destination = (Location) nextDestinationObj;
                        int dist = ts.currentLocation.distanceTo(ts.destination);
                        if (dist == Integer.MAX_VALUE) {
                            System.err.println("ERROR: Ruta bloqueada desde depot para " + ts.truck.id + ". Abortando.");
                            ts.status = TruckState.Status.IDLE; ts.currentRoutePlan.clear(); ts.timeAvailable = minute;
                        } else {
                            int travelTime = (int) Math.round(dist * MINUTOS_POR_KM);
                            double fuelNeeded = calculateFuelConsumed(dist, ts.currentLoadM3, ts.truck);
                            if (fuelNeeded > ts.currentFuelGal) {
                                System.err.println("ERROR: Combustible insuficiente para primer tramo para " + ts.truck.id + ". Abortando.");
                                ts.status = TruckState.Status.IDLE; ts.currentRoutePlan.clear(); ts.timeAvailable = minute;
                            } else {
                                System.out.println("Truck " + ts.truck.id + " saliendo hacia " + ts.destination + " en t=" + minute);
                                ts.status = TruckState.Status.EN_ROUTE;
                                ts.arrivalTimeAtDestination = minute + travelTime;
                                ts.timeAvailable = ts.arrivalTimeAtDestination;
                                // Consumir combustible ahora (o al llegar?) Por ahora mejor al llegar para simplificar
                            }
                        }
                    } else {
                        System.err.println("ERROR: Plan de ruta inválido para " + ts.truck.id);
                        ts.status = TruckState.Status.IDLE; ts.currentRoutePlan.clear(); ts.timeAvailable = minute;
                    }
                    break;

                case EN_ROUTE:
                    System.out.println("Truck " + ts.truck.id + " llegó a " + ts.destination + " en t=" + minute);
                    int distTraveled = ts.currentLocation.distanceTo(ts.destination);
                    double fuelConsumed = calculateFuelConsumed(distTraveled, ts.currentLoadM3, ts.truck);
                    ts.currentFuelGal -= fuelConsumed;
                    if (ts.currentFuelGal < 0) System.err.println("ALERTA: Combustible negativo para " + ts.truck.id);

                    ts.currentLocation = ts.destination;

                    if (ts.destination instanceof CustomerPart) {
                        System.out.println("  Truck " + ts.truck.id + " iniciando descarga...");
                        ts.status = TruckState.Status.DISCHARGING;
                        ts.timeAvailable = minute + DISCHARGE_TIME_MINUTES;
                    } else if (ts.destination instanceof Depot) {
                        System.out.println("  Truck " + ts.truck.id + " llegó a Depot " + ((Depot)ts.destination).id);
                        if (((Depot)ts.destination).isMainPlant()) {
                            ts.currentFuelGal = MAX_FUEL_GAL;
                            System.out.println("  Truck " + ts.truck.id + " combustible recargado en Planta.");
                        }
                        // FALTA: Lógica de recarga en tanque intermedio (pendiente)

                        ts.currentRoutePlan.remove(0);
                        if (ts.currentRoutePlan.isEmpty()) {
                            System.out.println("  Truck " + ts.truck.id + " completó ruta. IDLE.");
                            ts.status = TruckState.Status.IDLE;
                            ts.timeAvailable = minute;
                        } else {
                            System.out.println("  Truck " + ts.truck.id + " iniciando PRE_TRIP para siguiente parte de ruta.");
                            ts.status = TruckState.Status.PRE_TRIP;
                            ts.timeAvailable = minute + PRE_TRIP_CHECK_MINUTES;
                            // No se recarga GLP aquí, se asume que cargó todo al inicio
                        }
                    }
                    break;

                case DISCHARGING:
                    CustomerPart servedPart = (CustomerPart) ts.destination; // El destino era el cliente
                    System.out.println("Truck " + ts.truck.id + " terminó descarga en CPart " + servedPart.partId + " en t=" + minute);
                    ts.currentLoadM3 -= servedPart.demandM3;
                    servedPart.served = true;
                    activeCustomerParts.remove(servedPart);

                    ts.currentRoutePlan.remove(0);

                    if (ts.currentRoutePlan.isEmpty()) {
                        System.out.println("  Truck " + ts.truck.id + " última entrega, regresando a " + ts.truck.homeDepot);
                        ts.destination = ts.truck.homeDepot;
                        ts.status = TruckState.Status.RETURNING;
                    } else {
                        ts.destination = (Location) ts.currentRoutePlan.get(0);
                        System.out.println("  Truck " + ts.truck.id + " yendo a siguiente destino: " + ts.destination);
                        ts.status = TruckState.Status.EN_ROUTE;
                    }

                    int distNext = ts.currentLocation.distanceTo(ts.destination);
                    if (distNext == Integer.MAX_VALUE) {
                        System.err.println("ERROR: Ruta bloqueada para siguiente tramo de " + ts.truck.id + ". Abortando.");
                        ts.status = TruckState.Status.IDLE; ts.currentRoutePlan.clear(); ts.timeAvailable = minute;
                    } else {
                        int travelTimeNext = (int) Math.round(distNext * MINUTOS_POR_KM);
                        double fuelNeededNext = calculateFuelConsumed(distNext, ts.currentLoadM3, ts.truck);
                        if (fuelNeededNext > ts.currentFuelGal) {
                            // Si se queda sin combustible después de esto, (POR AHORA SE MARCA COMO NO DISPONIBLE)
                            System.err.println("ERROR CRITICO: Combustible insuficiente para tramo post-descarga " + ts.truck.id + " (Need:" + fuelNeededNext + ", Have:" + ts.currentFuelGal + "). Abortando ruta.");
                            ts.status = TruckState.Status.INACTIVE;
                            ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                        } else {
                            ts.arrivalTimeAtDestination = minute + travelTimeNext;
                            ts.timeAvailable = ts.arrivalTimeAtDestination;
                        }
                    }
                    break;

                case RETURNING:
                    System.out.println("Truck " + ts.truck.id + " regresó a Depot " + ((Depot)ts.destination).id + " en t=" + minute);
                    int distRet = ts.currentLocation.distanceTo(ts.destination);
                    double fuelRet = calculateFuelConsumed(distRet, 0.0, ts.truck);
                    ts.currentFuelGal -= fuelRet;
                    ts.currentLocation = ts.destination;
                    if (((Depot)ts.destination).isMainPlant()) {
                        ts.currentFuelGal = MAX_FUEL_GAL;
                        System.out.println("  Truck " + ts.truck.id + " combustible recargado.");
                    }
                    ts.status = TruckState.Status.IDLE;
                    ts.timeAvailable = minute;
                    ts.destination = null;
                    ts.currentLoadM3 = 0;
                    break;

                case INACTIVE:
                    break;
            }
        }
    }

    // Utils
    static List<CustomerPart> getUnservedCustomerParts() {
        // Devuelve la lista actual de partes activas que no están marcadas como servidas
        // Contiene solo las no servidas.
        return new ArrayList<>(activeCustomerParts);
    }

    static double calculateRequiredLoadForPlan(List<Object> plan) {
        double requiredLoad = 0;
        for (Object step : plan) {
            if (step instanceof CustomerPart) {
                requiredLoad += ((CustomerPart) step).demandM3;
            }
        }
        return requiredLoad;
    }

    static void applyPlannedRoutes(PlanningSolution solution, int applyTime) {
        if (solution == null) return;

        System.out.println("Aplicando rutas planificadas a camiones IDLE...");
        Set<String> assignedTrucks = new HashSet<>();

        for (PlannedRoute route : solution.routes) {
            TruckState ts = truckStates.get(route.truck.id);
            if (ts != null && ts.status == TruckState.Status.IDLE && ts.timeAvailable <= applyTime) {
                // Construir el plan de acción: Lista de CustomerPart y Depot final
                List<Object> actionPlan = new LinkedList<>();
                actionPlan.addAll(route.sequence);
                actionPlan.add(route.endDepot);

                ts.currentRoutePlan = actionPlan;
                assignedTrucks.add(ts.truck.id);
                System.out.println("  Ruta asignada a " + ts.truck.id + " (#Clientes: " + route.sequence.size() + ")");
            } else if (ts != null && ts.status != TruckState.Status.IDLE) {
                // El camión estaba ocupado, la replanificación idealmente lo consideraría
                // System.out.println("  WARN: Camión " + ts.truck.id + " está ocupado ("+ts.status+"), no se pudo asignar nueva ruta planificada.");
            }
        }
        System.out.println("Total de camiones con nuevas rutas asignadas: " + assignedTrucks.size());

        // Marcar partes como 'atendidas' si están en alguna ruta asignada
        // La simulación las marcará como 'served' al completar descarga.
        // Esto es más para la lógica interna del planificador
    }


    // Heurística + Tabu Search
    static PlanningSolution planRoutes(List<CustomerPart> customersToServe, int planningStartTime) {
        if (customersToServe == null || customersToServe.isEmpty()) {
            System.out.println("Planificador: No hay clientes para servir.");
            return new PlanningSolution();
        }

        long startTime = System.currentTimeMillis();

        List<Truck> availableTrucks = fleet.stream()
                .filter(t -> truckStates.get(t.id).status == TruckState.Status.IDLE &&
                        truckStates.get(t.id).timeAvailable <= planningStartTime)
                .collect(Collectors.toList());

        if (availableTrucks.isEmpty()) {
            System.err.println("Planificador: No hay camiones disponibles en t=" + planningStartTime);
            PlanningSolution noSolution = new PlanningSolution();
            noSolution.unassignedParts.addAll(customersToServe);
            return noSolution;
        }
        System.out.println("Planificador: " + availableTrucks.size() + " camiones disponibles.");


        // Crear solución inicial con heurística Best Fit Insertion
        System.out.println("  Generando solución inicial con Best Fit (ref t=" + planningStartTime + ")...");
        PlanningSolution currentSolution = createInitialSolutionBestFit(customersToServe, availableTrucks, planningStartTime);

        if (currentSolution == null) {
            System.err.println("Planificador: Falló la creación de la solución inicial.");
            PlanningSolution failedSolution = new PlanningSolution();
            failedSolution.unassignedParts.addAll(customersToServe);
            return failedSolution;
        }

        // Se evalúa solución inicial
        evaluateSolution(currentSolution, planningStartTime);
        PlanningSolution bestSolution = new PlanningSolution(currentSolution);

        System.out.println("  Solución Inicial | Costo: " + formatCost(bestSolution.totalCost) + " | Rutas: " + bestSolution.routes.size() + " | Sin Asignar: " + bestSolution.unassignedParts.size() + " | Factible: " + bestSolution.fullyFeasible);

        // Si la solución inicial ya es buena y asignó todo, quizás no necesitemos TS intensivo
        if (bestSolution.fullyFeasible && bestSolution.unassignedParts.isEmpty() && bestSolution.totalCost < Double.POSITIVE_INFINITY) { // Podríamos hacer un TS corto o saltarlo
            System.out.println("  Solución inicial parece completa y factible.");
        }

        // Búsqueda Tabú en sí
        tabuQueue.clear();
        tabuSet.clear();

        for (int iter = 0; iter < TS_MAX_ITERATIONS; iter++) {
            PlanningSolution bestNeighborOverall = null;
            Move bestMoveOverall = null;
            double bestNeighborCostOverall = Double.POSITIVE_INFINITY;
            boolean bestMoveIsTabuOverall = false;

            // Vecindario 1: 2-Opt
            for (PlannedRoute currentPRoute : currentSolution.routes) {
                if (currentPRoute.sequence.size() < 2) continue;
                for (int i = 0; i < currentPRoute.sequence.size() - 1; i++) {
                    for (int j = i + 1; j < currentPRoute.sequence.size(); j++) {
                        Move_2Opt move = new Move_2Opt(currentPRoute.truck.id, i, j);
                        PlanningSolution neighborSolution = new PlanningSolution(currentSolution);
                        PlannedRoute routeToModify = findPlannedRouteInSolution(neighborSolution, currentPRoute.truck.id);
                        if (routeToModify != null) {
                            apply2OptToPlannedRoute(routeToModify, i, j);
                            evaluateSolution(neighborSolution, planningStartTime);
                            boolean isTabu = tabuSet.contains(move);
                            if (neighborSolution.totalCost < bestNeighborCostOverall) {
                                bestNeighborCostOverall=neighborSolution.totalCost;
                                bestNeighborOverall=neighborSolution;
                                bestMoveOverall=move;
                                bestMoveIsTabuOverall=isTabu;
                            }
                        }
                    }
                }
            }

            // Vecindario 2: Realocate
            for (int routeIdxA = 0; routeIdxA < currentSolution.routes.size(); routeIdxA++) {
                PlannedRoute routeA = currentSolution.routes.get(routeIdxA);
                if (routeA.sequence.isEmpty()) continue;
                for (int custIdxA = routeA.sequence.size() - 1; custIdxA >= 0; custIdxA--) {
                    CustomerPart customerToMove = routeA.sequence.get(custIdxA);
                    for (int routeIdxB = 0; routeIdxB < currentSolution.routes.size(); routeIdxB++) {
                        if (routeIdxA == routeIdxB) continue;
                        PlannedRoute routeB = currentSolution.routes.get(routeIdxB);
                        for (int posB = 0; posB <= routeB.sequence.size(); posB++) {
                            PlanningSolution neighborSolution = new PlanningSolution(currentSolution);
                            PlannedRoute neighborRouteA = findPlannedRouteInSolution(neighborSolution, routeA.truck.id);
                            PlannedRoute neighborRouteB = findPlannedRouteInSolution(neighborSolution, routeB.truck.id);
                            if(neighborRouteA == null || neighborRouteB == null) continue;

                            if (calculatePlannedRouteLoad(neighborRouteB) + customerToMove.demandM3 > neighborRouteB.truck.type.capacidadM3) {
                                continue;
                            }

                            CustomerPart movedCustomer = neighborRouteA.sequence.remove(custIdxA);
                            neighborRouteB.sequence.add(posB, movedCustomer);

                            evaluateSolution(neighborSolution, planningStartTime);
                            Move_Relocate move = new Move_Relocate(movedCustomer.partId, routeA.truck.id, routeB.truck.id);
                            boolean isTabu = tabuSet.contains(move);
                            if (neighborSolution.totalCost < bestNeighborCostOverall) {
                                bestNeighborCostOverall=neighborSolution.totalCost;
                                bestNeighborOverall=neighborSolution;
                                bestMoveOverall=move;
                                bestMoveIsTabuOverall=isTabu;
                            }
                        }
                    }
                }
            }

            // Vecindario 3: Insertar no Asignados
            if (!currentSolution.unassignedParts.isEmpty()) {
                List<CustomerPart> customersToTryAssigning = new ArrayList<>(currentSolution.unassignedParts);
                for(CustomerPart customer : customersToTryAssigning) {
                    for(PlannedRoute route : currentSolution.routes) {
                        for (int pos = 0; pos <= route.sequence.size(); pos++) {
                            PlanningSolution neighborSolution = new PlanningSolution(currentSolution);
                            PlannedRoute routeToInsert = findPlannedRouteInSolution(neighborSolution, route.truck.id);
                            Set<CustomerPart> neighborUnassigned = neighborSolution.unassignedParts;
                            if(routeToInsert == null) continue;

                            if (calculatePlannedRouteLoad(routeToInsert) + customer.demandM3 > routeToInsert.truck.type.capacidadM3) {
                                continue;
                            }

                            routeToInsert.sequence.add(pos, customer);
                            neighborUnassigned.remove(customer);

                            evaluateSolution(neighborSolution, planningStartTime);

                            // Crear un "Move" representativo
                            // Podríamos definir Move_Assign o simplemente usar null/omitir tabú para asignaciones
                            Move assignmentMove = null;
                            boolean isTabu = false;

                            // Priorizar la asignación de clientes
                            // Si asigna un cliente y es factible, puede ser mejor que una ruta óptima pero incompleta.
                            // Se puede aceptar la primera inserción factible pero evaluarlo mejor
                            // Por ahora, la comparación normal por costo total (que incluye penalización por no asignados) debería funcionar
                            if (neighborSolution.totalCost < bestNeighborCostOverall) {
                                bestNeighborCostOverall=neighborSolution.totalCost; bestNeighborOverall=neighborSolution;
                                bestMoveOverall=assignmentMove;
                                bestMoveIsTabuOverall=isTabu;
                            }
                        }
                    }
                }
            }


            // Selección y actualizaciónn
            if (bestNeighborOverall == null) { System.out.println("  Iter " + iter + ": No se encontraron vecinos válidos/mejoradores."); break; }
            boolean moveChosen = false;
            if (bestMoveIsTabuOverall) {
                if (bestNeighborCostOverall < bestSolution.totalCost) {
                    currentSolution = bestNeighborOverall;
                    moveChosen = true;
                } else
                {
                    moveChosen = false;
                }
            }
            else {
                currentSolution = bestNeighborOverall;
                moveChosen = true;
            }

            if (moveChosen && bestMoveOverall != null) { // Solo aplicar tabú si fue un movimiento real (2opt/reloc)
                tabuQueue.offer(bestMoveOverall); tabuSet.add(bestMoveOverall);
                while (tabuQueue.size() > TS_TABU_TENURE) { tabuSet.remove(tabuQueue.poll()); }
            }
            if (currentSolution.totalCost < bestSolution.totalCost) {
                bestSolution = new PlanningSolution(currentSolution);
                System.out.println("  Iter " + iter + ": ✨ Nueva Mejor Solución! Costo: " + formatCost(bestSolution.totalCost) + " Sin Asignar: " + bestSolution.unassignedParts.size() + " Factible: " + bestSolution.fullyFeasible);
            }
            if (iter > 0 && iter % 100 == 0) { System.out.println("  Iter " + iter + " | Costo Actual: " + formatCost(currentSolution.totalCost) + " | Mejor: " + formatCost(bestSolution.totalCost) + " | Sin Asignar: " + currentSolution.unassignedParts.size()); }

        }

        long endTime = System.currentTimeMillis();
        System.out.println("\n🏁 Búsqueda Tabú completada en " + (endTime - startTime) + " ms.");
        System.out.println("🏆 Mejor solución encontrada:");
        System.out.println("  Costo Total (para Optimizador): " + formatCost(bestSolution.totalCost));
        System.out.println("  Costo Operacional (Rutas Factibles): " + formatCost(bestSolution.operationalFuelCost));
        System.out.println("  Totalmente Factible: " + bestSolution.fullyFeasible);
        System.out.println("  Clientes Sin Asignar: " + bestSolution.unassignedParts.size());
        System.out.println("  Rutas (" + bestSolution.routes.size() + "):");
        bestSolution.routes.forEach(r -> System.out.println("    " + r + " | Costo: " + formatCost(r.cost) + " | Fuel: " + String.format("%.2f", r.estimatedFuel) + " Gal | Feasible: " + r.feasible));

        return bestSolution;
    }


    // Heurística inicial (Best Fit)
    static PlanningSolution createInitialSolutionBestFit(List<CustomerPart> customersToServe, List<Truck> availableTrucks, int planningStartTime) {
        PlanningSolution initialSol = new PlanningSolution();
        initialSol.unassignedParts.addAll(customersToServe);

        for (Truck truck : availableTrucks) {
            initialSol.routes.add(new PlannedRoute(truck, truck.homeDepot));
        }

        if (initialSol.routes.isEmpty()) return null;

        int customersAssignedThisPass;
        do {
            customersAssignedThisPass = 0;
            CustomerPart bestPartToAssign = null;
            PlannedRoute bestRouteForPart = null;
            int bestInsertionPos = -1;
            double minGlobalCostIncrease = Double.POSITIVE_INFINITY;

            List<CustomerPart> candidates = new ArrayList<>(initialSol.unassignedParts);

            for (CustomerPart part : candidates) {
                PlannedRoute currentBestRoute = null; int currentBestPos = -1; double currentMinCostInc = Double.POSITIVE_INFINITY;

                for (PlannedRoute route : initialSol.routes) {
                    for (int pos = 0; pos <= route.sequence.size(); pos++) {
                        // Simular inserción creando copia de la ruta
                        PlannedRoute testRoute = new PlannedRoute(route);
                        testRoute.sequence.add(pos, part);

                        // Verificar capacidad
                        if (calculatePlannedRouteLoad(testRoute) <= route.truck.type.capacidadM3) {
                            // Evaluar costo y factibilidad completo de la ruta modificada
                            RouteEvaluationResult result = calculatePlannedRouteCostAndFuel(testRoute, planningStartTime);
                            double potentialCost = result.cost;
                            if (potentialCost < Double.POSITIVE_INFINITY) {
                                double originalRouteCost = (route.cost == Double.POSITIVE_INFINITY || route.sequence.isEmpty()) ? 0 : route.cost;
                                double costIncrease = potentialCost - originalRouteCost;
                                if (costIncrease < currentMinCostInc) {
                                    currentMinCostInc = costIncrease; currentBestRoute = route; currentBestPos = pos;
                                }
                            }
                        }
                    }
                }

                if (currentBestRoute != null && currentMinCostInc < minGlobalCostIncrease) {
                    minGlobalCostIncrease = currentMinCostInc;
                    bestPartToAssign = part;
                    bestRouteForPart = currentBestRoute;
                    bestInsertionPos = currentBestPos;
                }
            }

            if (bestPartToAssign != null) {
                bestRouteForPart.sequence.add(bestInsertionPos, bestPartToAssign);
                evaluatePlannedRoute(bestRouteForPart, planningStartTime);
                initialSol.unassignedParts.remove(bestPartToAssign);
                customersAssignedThisPass++;
                // System.out.println("  Init Sol: Asignado CPart" + bestPartToAssign.partId + " a " + bestRouteForPart.truck.id);
            }

        } while (customersAssignedThisPass > 0 && !initialSol.unassignedParts.isEmpty());

        initialSol.routes.removeIf(route -> route.sequence.isEmpty());
        return initialSol;
    }

    // Evaluación de soluciones y rutas
    static double calculatePlannedRouteLoad(PlannedRoute route) {
        if (route == null || route.sequence == null) return 0.0;
        return route.sequence.stream().mapToDouble(c -> c.demandM3).sum();
    }

    static void evaluateSolution(PlanningSolution solution, int planningStartTime) {
        solution.totalCost = 0; solution.fullyFeasible = true;
        if(solution.routes == null) {
            solution.totalCost=Double.POSITIVE_INFINITY;
            solution.fullyFeasible=false;
            return;
        }

        for (PlannedRoute r : solution.routes) {
            evaluatePlannedRoute(r, planningStartTime);
            if (!r.feasible) { solution.fullyFeasible = false; }
            if (r.cost != Double.POSITIVE_INFINITY) {
                if (solution.totalCost != Double.POSITIVE_INFINITY) {
                    solution.totalCost += r.cost;
                    solution.operationalFuelCost += r.cost;
                }
                else {
                    solution.totalCost = r.cost;
                    solution.operationalFuelCost = r.cost;
                }
            } else {
                solution.totalCost = Double.POSITIVE_INFINITY;
            }
        }
        if (!solution.unassignedParts.isEmpty()) {
            solution.fullyFeasible = false;
            // Penalización fuerte si hay no asignados, haciendo el costo efectivamente infinito
            solution.totalCost = Double.POSITIVE_INFINITY;
        }
    }

    static void evaluatePlannedRoute(PlannedRoute route, int planningStartTime) {
        if (route == null || route.truck == null) {
            route.cost = Double.POSITIVE_INFINITY;
            route.feasible = false;
            route.estimatedFuel = Double.POSITIVE_INFINITY;
            return;
        }
        RouteEvaluationResult result = calculatePlannedRouteCostAndFuel(route, planningStartTime);
        route.cost = result.cost;
        route.estimatedFuel = result.fuel;
        route.feasible = result.feasible;
    }

    static class RouteEvaluationResult {
        double cost = Double.POSITIVE_INFINITY;
        double fuel = Double.POSITIVE_INFINITY;
        boolean feasible = false;
        int endTime = -1;
    }

    static RouteEvaluationResult calculatePlannedRouteCostAndFuel(PlannedRoute route, int startTime) {
        RouteEvaluationResult result = new RouteEvaluationResult();
        double totalFuelCost = 0;
        double currentLoadM3 = 0;

        if (route == null || route.truck == null || route.startDepot == null || route.endDepot == null || route.sequence == null) {
            return result;
        }

        int currentTime = startTime + PRE_TRIP_CHECK_MINUTES;
        Location currentLocation = route.startDepot;

        currentLoadM3 = calculatePlannedRouteLoad(route);
        if (currentLoadM3 > route.truck.type.capacidadM3) {
            // System.err.println(" Ruta " + route.truck.id + ": CAPACIDAD excedida");
            return result;
        }

        double fuelRemaining = MAX_FUEL_GAL;

        for (CustomerPart customer : route.sequence) {
            int dist = distanciaReal(currentLocation, customer);
            if (dist == Integer.MAX_VALUE) return result;

            double fuelNeeded = calculateFuelConsumed(dist, currentLoadM3, route.truck);
            if (fuelNeeded > fuelRemaining) return result;

            totalFuelCost += fuelNeeded;
            fuelRemaining -= fuelNeeded;
            currentTime += (int) Math.round(dist * MINUTOS_POR_KM);

            if (currentTime > customer.deadlineMinutes) return result;

            currentTime += DISCHARGE_TIME_MINUTES;

            currentLocation = customer;
            currentLoadM3 -= customer.demandM3;
        }

        int distReturn = distanciaReal(currentLocation, route.endDepot);
        if (distReturn == Integer.MAX_VALUE) return result;

        double fuelReturn = calculateFuelConsumed(distReturn, 0.0, route.truck);
        if (fuelReturn > fuelRemaining) return result;

        totalFuelCost += fuelReturn;
        currentTime += (int) Math.round(distReturn * MINUTOS_POR_KM);

        result.cost = totalFuelCost;
        result.fuel = MAX_FUEL_GAL - fuelRemaining + fuelReturn;
        result.fuel = totalFuelCost;
        result.feasible = true;
        result.endTime = currentTime;
        return result;
    }

    // Aplicar 20pt
    static void apply2OptToPlannedRoute(PlannedRoute route, int index1, int index2) {
        List<CustomerPart> seq = route.sequence;
        int start = Math.min(index1, index2);
        int end = Math.max(index1, index2);
        while(start < end){
            CustomerPart temp = seq.get(start);
            seq.set(start, seq.get(end));
            seq.set(end, temp);
            start++; end--;
        }
    }

    static PlannedRoute findPlannedRouteInSolution(PlanningSolution solution, String truckId) {
        for(PlannedRoute r : solution.routes){
            if(r.truck.id.equals(truckId))
                return r;
        }
        return null;
    }

    static double calculateFuelConsumed(int d, double load, Truck t) {
        if(d==0) return 0;
        double pc=t.type.calcularPesoCargaActualTon(load);
        double pt=t.type.taraTon+pc;
        return (double)d*pt/180.0;
    }

    private static int distanciaReal(Location f, Location t) {
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

    static List<Pedido> cargarPedidos(String a) throws Exception {
        System.out.println("Cargando pedidos desde: " + a);
        List<Pedido> p=new ArrayList<>();
        List<String> l=Files.readAllLines(Paths.get(a));
        for(String s:l){
            try{
                String[] p1=s.split(":");
                if(p1.length!=2)continue;
                int m=TiempoUtils.parsearMarcaDeTiempo(p1[0].trim());
                String[] d=p1[1].split(",");
                if(d.length!=5)continue;
                int x=Integer.parseInt(d[0].trim());
                int y=Integer.parseInt(d[1].trim());
                double v=Double.parseDouble(d[3].trim().replace("m3",""));
                int h=Integer.parseInt(d[4].trim().replace("h",""));
                p.add(new Pedido(x,y,v,h,m));
            } catch(Exception e)
            {
                System.err.println("Error P: "+s+" - "+e.getMessage());
            }
        }
        p.sort(Comparator.comparingInt(pd->pd.momentoPedido));
        System.out.println("Pedidos cargados: "+p.size());
        return p;
    }
    static List<Bloqueo> cargarBloqueos(String a) throws Exception {
        System.out.println("Cargando bloqueos desde: " + a);
        List<Bloqueo> b=new ArrayList<>();
        List<String> l=Files.readAllLines(Paths.get(a));
        for(String s:l){
            try{
                String[] p1=s.split(":");
                if(p1.length!=2)continue;
                String[] t=p1[0].split("-");
                if(t.length!=2)continue;
                int i=TiempoUtils.parsearMarcaDeTiempo(t[0].trim());
                int f=TiempoUtils.parsearMarcaDeTiempo(t[1].trim());
                String[] c=p1[1].split(",");
                List<int[]> pts=new ArrayList<>();
                for(int k=0; k<c.length; k+=2){
                    pts.add(new int[]{Integer.parseInt(c[k].trim()),Integer.parseInt(c[k+1].trim())});
                }
                if(!pts.isEmpty()) b.add(new Bloqueo(i,f,pts));
            } catch(Exception e)
            {
                System.err.println("Error B: "+s+" - "+e.getMessage());
            }
        }
        System.out.println("Bloqueos cargados: "+b.size());
        return b;
    }

    static void visualizarSolucion(PlanningSolution solution) {
        System.out.println("Preparando visualización...");
        if (solution == null) {
            System.out.println("No hay solución para visualizar.");
            return;
        }

        List<GridVisualizer_MDVRP.Punto> cliVis_temp;
        if (solution.routes != null) {
            cliVis_temp = solution.routes.stream()
                    .filter(route -> route.sequence != null && !route.sequence.isEmpty())
                    .flatMap(route -> route.sequence.stream())
                    .map(part -> new GridVisualizer_MDVRP.Punto(
                            part.x,
                            part.y,
                            GridVisualizer_MDVRP.PuntoTipo.CLIENTE,
                            String.valueOf(part.partId)))
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            cliVis_temp = new ArrayList<>();
        }

        List<GridVisualizer_MDVRP.Punto> depVis_temp = depots.stream()
                .map(d -> new GridVisualizer_MDVRP.Punto(d.x, d.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, d.id))
                .collect(Collectors.toList());

        List<GridVisualizer_MDVRP.RutaVisual> rutVis_temp = new ArrayList<>();
        Color[] C = {Color.BLUE, Color.RED, Color.ORANGE, Color.MAGENTA, Color.PINK, Color.YELLOW.darker(), Color.CYAN, Color.GRAY, Color.GREEN.darker().darker(), Color.BLUE.darker(), Color.RED.darker(), Color.ORANGE.darker()};
        int ci = 0;

        if (solution.routes != null) {
            for (PlannedRoute route : solution.routes) {
                if (route.sequence != null && !route.sequence.isEmpty()) {
                    List<GridVisualizer_MDVRP.Punto> seq = new ArrayList<>();
                    seq.add(new GridVisualizer_MDVRP.Punto(route.startDepot.x, route.startDepot.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, route.startDepot.id));
                    route.sequence.forEach(part -> seq.add(new GridVisualizer_MDVRP.Punto(part.x, part.y, GridVisualizer_MDVRP.PuntoTipo.CLIENTE, String.valueOf(part.partId))));
                    seq.add(new GridVisualizer_MDVRP.Punto(route.endDepot.x, route.endDepot.y, GridVisualizer_MDVRP.PuntoTipo.DEPOSITO, route.endDepot.id));
                    Color clr = C[ci % C.length]; ci++;
                    rutVis_temp.add(new GridVisualizer_MDVRP.RutaVisual(route.truck.id, seq, clr));
                }
            }
        }

        final List<GridVisualizer_MDVRP.Punto> finalDepVis = depVis_temp;
        final List<GridVisualizer_MDVRP.Punto> finalCliVis = cliVis_temp;
        final List<GridVisualizer_MDVRP.RutaVisual> finalRutVis = rutVis_temp;

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Visualización GLP - Rutas MDVRP (TS)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GridVisualizer_MDVRP p = new GridVisualizer_MDVRP(finalDepVis, finalCliVis, finalRutVis, blockedNodes);
            p.setPreferredSize(new Dimension(GRID_WIDTH * 12 + 50, GRID_HEIGHT * 12 + 50));
            JScrollPane sp = new JScrollPane(p);
            f.add(sp);
            f.setSize(900, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    static String formatTime(int totalMinutes) {
        int days = totalMinutes / (24 * 60);
        int remainingMinutes = totalMinutes % (24 * 60);
        int hours = remainingMinutes / 60;
        int minutes = remainingMinutes % 60;
        return String.format("%dd %02dh %02dm", days, hours, minutes);
    }

    static class Pedido {
        int x,y;
        double volumen;
        int horaLimite,momentoPedido;
        Pedido(int x,int y,double v,int h,int m){
            this.x=x;
            this.y=y;
            this.volumen=v;
            this.horaLimite=h;
            this.momentoPedido=m;
        }
    }

    static class Bloqueo {
        int inicioMinutos,finMinutos;
        List<int[]> puntosBloqueados;
        Bloqueo(int i,int f,List<int[]> p){
            inicioMinutos=i;finMinutos=f;puntosBloqueados=p;
        }
    }
}