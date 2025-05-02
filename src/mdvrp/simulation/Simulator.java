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

    public void runSimulation(int durationMinutes, boolean enableReplanning) {
        System.out.println("--- Iniciando Simulación por " + durationMinutes + " minutos ---");
        while (currentSimTime <= durationMinutes) {
            boolean newOrderActivated = activateNewPedidos(currentSimTime);
            boolean blockadeChanged = updateBlockages(currentSimTime);
            refillIntermediateDepotsIfNeeded(currentSimTime);

            // Actualizar estado de los camiones (útil luego para averías y mantenimientos)
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

    public void updateTrucks(int minute) {
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
                            ts.currentRoutePlan.clear();
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
                    if (minute >= ts.timeAvailable) {
                        CustomerPart servedPart = (CustomerPart) ts.destination;
                        System.out.println("Truck " + ts.truck.id + " terminó descarga en CPart " + servedPart.partId + " en t=" + minute);
                        ts.currentLoadM3 -= servedPart.demandM3;
                        if (ts.currentLoadM3 < -0.01) { ts.currentLoadM3 = 0; }
                        servedPart.served = true;
                        GlobalState.activeCustomerParts.remove(servedPart);

                        // Quitar el cliente recién servido del plan de acción
                        if (!ts.currentRoutePlan.isEmpty() && ts.currentRoutePlan.get(0).equals(servedPart)) {
                            ts.currentRoutePlan.remove(0);
                        } else {
                            System.err.println("WARN: CPart " + servedPart.partId + " no era el primer elemento del plan de " + ts.truck.id + " al terminar descarga?");
                        }


                        if (ts.currentRoutePlan.isEmpty()) {
                            // Última entrega, regresar a casa
                            System.out.println("  Truck " + ts.truck.id + " última entrega, regresando a " + ts.truck.homeDepot);
                            ts.destination = ts.truck.homeDepot;
                            ts.status = TruckState.Status.RETURNING;
                            int distRet = SimulationUtils.distanciaReal(ts.currentLocation, ts.destination);
                            if (distRet == Integer.MAX_VALUE) {
                                System.err.println("ERROR: Ruta bloqueada para retorno de " + ts.truck.id + ". INACTIVE.");
                                ts.status = TruckState.Status.INACTIVE; ts.timeAvailable = Integer.MAX_VALUE;
                            } else {
                                int travelTimeRet = (int) Math.round(distRet * SimulationUtils.MINUTOS_POR_KM);
                                double fuelNeededRet = SimulationUtils.calculateFuelConsumed(distRet, ts.currentLoadM3, ts.truck);
                                if (fuelNeededRet > ts.currentFuelGal) {
                                    System.err.println("ERROR CRITICO: Combustible insuficiente para RETORNO " + ts.truck.id + ". INACTIVE.");
                                    ts.status = TruckState.Status.INACTIVE; ts.timeAvailable = Integer.MAX_VALUE;
                                } else {
                                    ts.arrivalTimeAtDestination = minute + travelTimeRet;
                                    ts.timeAvailable = ts.arrivalTimeAtDestination;
                                }
                            }
                        } else {
                            Object nextStep = ts.currentRoutePlan.get(0);
                            if (nextStep instanceof CustomerPart) {
                                CustomerPart nextCustomer = (CustomerPart) nextStep;
                                System.out.println("  Truck " + ts.truck.id + " siguiente destino planificado: CPart " + nextCustomer.partId);

                                // Necesita recargar GLP ANTES de ir al siguiente cliente
                                if (ts.currentLoadM3 < nextCustomer.demandM3 - 0.01) {
                                    System.out.println("    ** Necesita recargar GLP (" + String.format("%.2f", ts.currentLoadM3) + " m3) para CPart " + nextCustomer.partId + " (demanda " + nextCustomer.demandM3 + " m3). Buscando depósito...");

                                    Depot chosenDepot = SimulationUtils.findBestDepotForReload(ts.currentLocation, nextCustomer.demandM3);

                                    if (chosenDepot != null) {
                                        System.out.println("    Depósito elegido para recarga: " + chosenDepot.id);
                                        int distToDepot = SimulationUtils.distanciaReal(ts.currentLocation, chosenDepot);
                                        if (distToDepot == Integer.MAX_VALUE) {
                                            System.err.println("ERROR: Ruta bloqueada hacia depósito de recarga " + chosenDepot.id + " para " + ts.truck.id + ". INACTIVE.");
                                            ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                        } else {
                                            int travelTimeToDepot = (int) Math.round(distToDepot * SimulationUtils.MINUTOS_POR_KM);
                                            double fuelNeededToDepot = SimulationUtils.calculateFuelConsumed(distToDepot, ts.currentLoadM3, ts.truck);

                                            if (fuelNeededToDepot > ts.currentFuelGal) {
                                                System.err.println("ERROR CRITICO: Combustible insuficiente para ir a recargar GLP a " + chosenDepot.id + " para " + ts.truck.id + ". INACTIVE.");
                                                ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                            } else {
                                                // Ruta a depósito de recarga es viable
                                                ts.destination = chosenDepot;
                                                ts.status = TruckState.Status.EN_ROUTE_TO_RELOAD;
                                                ts.arrivalTimeAtDestination = minute + travelTimeToDepot;
                                                ts.timeAvailable = ts.arrivalTimeAtDestination;
                                                System.out.println("    Dirigiéndose a " + chosenDepot.id + " para recargar GLP. Llegada estimada: " + SimulationUtils.formatTime(ts.arrivalTimeAtDestination));
                                            }
                                        }
                                    } else {
                                        System.err.println("ERROR CRITICO: No se encontró depósito intermedio viable con suficiente GLP para CPart " + nextCustomer.partId + " para camión " + ts.truck.id + ". INACTIVE.");
                                        ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                    }
                                } else {
                                    System.out.println("    Tiene suficiente GLP. Dirigiéndose a CPart " + nextCustomer.partId);
                                    ts.destination = nextCustomer;
                                    ts.status = TruckState.Status.EN_ROUTE;
                                    int distNext = SimulationUtils.distanciaReal(ts.currentLocation, ts.destination);
                                    if (distNext == Integer.MAX_VALUE) {
                                        System.err.println("ERROR: Ruta bloqueada para siguiente tramo de " + ts.truck.id + ". INACTIVE.");
                                        ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                    } else {
                                        int travelTimeNext = (int) Math.round(distNext * SimulationUtils.MINUTOS_POR_KM);
                                        double fuelNeededNext = SimulationUtils.calculateFuelConsumed(distNext, ts.currentLoadM3, ts.truck);
                                        if (fuelNeededNext > ts.currentFuelGal) {
                                            System.err.println("ERROR CRITICO: Combustible insuficiente para tramo post-descarga " + ts.truck.id + ". INACTIVE.");
                                            ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                        } else {
                                            ts.arrivalTimeAtDestination = minute + travelTimeNext;
                                            ts.timeAvailable = ts.arrivalTimeAtDestination;
                                        }
                                    }
                                }
                            } else if (nextStep instanceof Depot) {
                                System.out.println("  Truck " + ts.truck.id + " siguiente destino planificado: Depot " + ((Depot)nextStep).id);
                                ts.destination = (Location) nextStep;
                                ts.status = TruckState.Status.EN_ROUTE;
                                int distNext = SimulationUtils.distanciaReal(ts.currentLocation, ts.destination);
                                if (distNext == Integer.MAX_VALUE) {
                                    System.err.println("ERROR: Ruta bloqueada para tramo final a Depot para " + ts.truck.id + ". INACTIVE.");
                                    ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                } else {
                                    int travelTimeNext = (int) Math.round(distNext * SimulationUtils.MINUTOS_POR_KM);
                                    double fuelNeededNext = SimulationUtils.calculateFuelConsumed(distNext, ts.currentLoadM3, ts.truck);
                                    if (fuelNeededNext > ts.currentFuelGal) {
                                        System.err.println("ERROR CRITICO: Combustible insuficiente para tramo final a Depot " + ts.truck.id + ". INACTIVE.");
                                        ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                    } else {
                                        ts.arrivalTimeAtDestination = minute + travelTimeNext;
                                        ts.timeAvailable = ts.arrivalTimeAtDestination;
                                    }
                                }
                            } else {
                                System.err.println("ERROR INESPERADO: Siguiente paso en plan no es Cliente ni Deposito: " + nextStep);
                                ts.status = TruckState.Status.IDLE; ts.currentRoutePlan.clear(); ts.timeAvailable = minute;
                            }
                        }
                    } // else { // Aún no ha terminado la descarga }
                    break;

                case EN_ROUTE_TO_RELOAD:
                    if (minute >= ts.arrivalTimeAtDestination) {
                        Depot arrivedDepot = (Depot) ts.destination;
                        System.out.println("Truck " + ts.truck.id + " llegó a " + arrivedDepot.id + " para recargar GLP en t=" + minute);

                        distTraveled = SimulationUtils.distanciaReal(ts.currentLocation, arrivedDepot);
                        fuelConsumed = SimulationUtils.calculateFuelConsumed(distTraveled, ts.currentLoadM3, ts.truck);
                        ts.currentFuelGal -= fuelConsumed;
                        if (ts.currentFuelGal < 0) System.err.println("ALERTA: Combustible negativo para " + ts.truck.id + " al llegar a recargar GLP.");
                        ts.currentLocation = arrivedDepot;

                        if (ts.currentFuelGal < GlobalState.MAX_FUEL_GAL) {
                            System.out.println("    Recargando combustible...");
                            ts.currentFuelGal = GlobalState.MAX_FUEL_GAL;
                        }

                        double neededForRestOfPlan = SimulationUtils.calculateRequiredLoadForPlan(ts.currentRoutePlan);
                        double spaceInTruck = ts.truck.type.capacidadM3 - ts.currentLoadM3;
                        double glpToRequest = Math.min(neededForRestOfPlan, spaceInTruck);
                        double amountToLoad = 0;

                        if (glpToRequest > 0.01) {
                            double glpAvailableAtDepot = arrivedDepot.capacidadActualM3;
                            amountToLoad = Math.min(glpToRequest, glpAvailableAtDepot);

                            if (amountToLoad > 0.01) {
                                ts.currentLoadM3 += amountToLoad;
                                arrivedDepot.capacidadActualM3 -= amountToLoad;
                                ts.timeAvailable = minute + GlobalState.RELOAD_GLP_MINUTES;
                                System.out.println("    Recargó " + String.format("%.2f", amountToLoad) + " m3 GLP. Nueva Carga: " + String.format("%.2f", ts.currentLoadM3) + " m3.");
                                System.out.println("    Capacidad restante en " + arrivedDepot.id + ": " + String.format("%.2f", arrivedDepot.capacidadActualM3) + " m3.");
                            } else {
                                System.out.println("    No se pudo recargar GLP (Depósito vacío o camión lleno/sin necesidad).");
                                ts.timeAvailable = minute;
                            }
                        } else {
                            System.out.println("    No necesita o no puede cargar más GLP en este momento.");
                            ts.timeAvailable = minute;
                        }

                        // Establecer el siguiente destino (el cliente original)
                        if (ts.currentRoutePlan.isEmpty()) {
                            System.err.println("ERROR INESPERADO: Plan vacío después de recargar GLP para " + ts.truck.id + ". INACTIVE.");
                            ts.status = TruckState.Status.INACTIVE; ts.timeAvailable = Integer.MAX_VALUE;
                        } else {
                            nextDestinationObj = ts.currentRoutePlan.get(0);
                            if (!(nextDestinationObj instanceof CustomerPart)) {
                                System.err.println("ERROR INESPERADO: Siguiente paso después de recarga no es Cliente: " + nextDestinationObj + ". INACTIVE.");
                                ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                            } else {
                                ts.destination = (Location) nextDestinationObj;
                                ts.status = TruckState.Status.EN_ROUTE;
                                System.out.println("  Truck " + ts.truck.id + " saliendo de " + arrivedDepot.id + " hacia " + ts.destination);

                                int distNext = SimulationUtils.distanciaReal(ts.currentLocation, ts.destination);
                                if (distNext == Integer.MAX_VALUE) {
                                    System.err.println("ERROR: Ruta bloqueada desde depot de recarga a cliente para " + ts.truck.id + ". INACTIVE.");
                                    ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                } else {
                                    int travelTimeNext = (int) Math.round(distNext * SimulationUtils.MINUTOS_POR_KM);
                                    double fuelNeededNext = SimulationUtils.calculateFuelConsumed(distNext, ts.currentLoadM3, ts.truck);
                                    if (fuelNeededNext > ts.currentFuelGal) {
                                        System.err.println("ERROR CRITICO: Combustible insuficiente DESPUÉS de recarga GLP para tramo a cliente " + ts.truck.id + ". INACTIVE.");
                                        ts.status = TruckState.Status.INACTIVE; ts.currentRoutePlan.clear(); ts.timeAvailable = Integer.MAX_VALUE;
                                    } else {
                                        ts.arrivalTimeAtDestination = ts.timeAvailable + travelTimeNext;
                                        ts.timeAvailable = ts.arrivalTimeAtDestination;
                                    }
                                }
                            }
                        }
                    } // else { // Aún no ha llegado al depósito de recarga }
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

    public boolean activateNewPedidos(int minute) {
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
                            p.momentoPedido, p.momentoPedido + p.horaLimite * 60, p.idCliente);
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

    public boolean updateBlockages(int minute) {
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

    public void activateOrDeactivateBloqueo(Bloqueo b, boolean activate) {
        for (int[] punto : b.puntosBloqueados) {
            if (punto[0] >= 0 && punto[0] < GRID_WIDTH && punto[1] >= 0 && punto[1] < GRID_HEIGHT) {
                blockedNodes[punto[0]][punto[1]] = activate;
            }
        }
    }

    public void refillIntermediateDepotsIfNeeded(int minute) {
        if (minute > 0 && minute % (24 * 60) == 0) {
            System.out.println("--- Medianoche día " + (minute / (24 * 60)) + ": Reabasteciendo Depósitos Intermedios ---");
            for (Depot d : depots) {
                if (!d.isMainPlant()) {
                    d.capacidadActualM3 = d.capacidadMaximaM3;
                }
            }
        }
    }

    public List<CustomerPart> getUnservedCustomerParts() {
        Set<Integer> partsInProgressIds = new HashSet<>();
        for (TruckState ts : GlobalState.truckStates.values()) {
            // Considerar camiones que están trabajando activamente en una ruta
            if (ts.status != TruckState.Status.IDLE && ts.status != TruckState.Status.INACTIVE) {
                if (ts.currentRoutePlan != null) {
                    for (Object step : ts.currentRoutePlan) {
                        if (step instanceof CustomerPart) {
                            partsInProgressIds.add(((CustomerPart) step).partId);
                        }
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

    public void applyPlannedRoutes(PlanningSolution solution, int applyTime) {
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
    }

    public TabuSearchPlanner getPlanner() {
        return planner;
    }

    public void setPlanner(TabuSearchPlanner planner) {
        this.planner = planner;
    }
}
