package mdvrp.simulation;
import mdvrp.model.*;
import mdvrp.planner.PlannedRoute;
import mdvrp.planner.PlanningSolution;
import mdvrp.planner.TabuSearchPlanner;
import mdvrp.state.GlobalState;

import java.util.*;

import static mdvrp.planner.TabuSearchPlanner.planRoutes;
import static mdvrp.simulation.SimulationUtils.*;
import static mdvrp.state.GlobalState.*;

public class Simulator {

    private TabuSearchPlanner planner;

    public Simulator(TabuSearchPlanner planner) {
        this.planner = planner;
    }
    public Simulator() {
        this.planner = new TabuSearchPlanner();
    }

    public static void runSimulation(int durationMinutes, boolean enableReplanning) {
        System.out.println("--- Iniciando Simulación por " + durationMinutes + " minutos ---");
        while (currentSimTime <= durationMinutes) {
            // Procesar eventos del minuto actual
            boolean newOrderActivated = activateNewPedidos(currentSimTime);
            boolean blockadeChanged = updateBlockages(currentSimTime);
            refillIntermediateDepotsIfNeeded(currentSimTime);

            // Actualizar estado de los camiones (útil luego para averías, etc)
            updateTrucks(currentSimTime);

            // Replanificación
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
                            if (ts.currentFuelGal < MAX_FUEL_GAL) {
                                // System.out.println("  Truck " + ts.truck.id + " recargando combustible.");
                                ts.currentFuelGal = MAX_FUEL_GAL;
                            }
                        }
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
                    } else if (ts.destination instanceof Depot) {   // Recarga
                        System.out.println("  Truck " + ts.truck.id + " llegó a Depot " + ((Depot)ts.destination).id);
                        ts.currentFuelGal = MAX_FUEL_GAL;
                        System.out.println("  Truck " + ts.truck.id + " combustible recargado en Planta.");
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

    static List<CustomerPart> getUnservedCustomerParts() {
        Set<Integer> partsInProgressIds = new HashSet<>();
        for (TruckState ts : GlobalState.truckStates.values()) {
            // Considerar camiones que están trabajando activamente en una ruta
            if (ts.status != TruckState.Status.IDLE && ts.status != TruckState.Status.INACTIVE) {
                if (ts.currentRoutePlan != null) {
                    for (Object step : ts.currentRoutePlan) {
                        if (step instanceof CustomerPart) {
                            partsInProgressIds.add(((CustomerPart) step).partId);
                        }
                        // También podríamos considerar el 'destination' si es CustomerPart
                        if (ts.destination instanceof CustomerPart) {
                            partsInProgressIds.add(((CustomerPart) ts.destination).partId);
                        }
                    }
                }
            }
        }

        // Filtrar la lista global de partes activas
        List<CustomerPart> needingAssignment = new ArrayList<>();
        for (CustomerPart part : GlobalState.activeCustomerParts) {
            if (!partsInProgressIds.contains(part.partId)) {
                needingAssignment.add(part);
            }
        }

        return needingAssignment;
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
                ts.routes.add(route);
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

    public TabuSearchPlanner getPlanner() {
        return planner;
    }

    public void setPlanner(TabuSearchPlanner planner) {
        this.planner = planner;
    }
}
