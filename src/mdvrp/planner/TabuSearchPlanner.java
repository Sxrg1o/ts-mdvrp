package mdvrp.planner;

import mdvrp.model.CustomerPart;
import mdvrp.model.Depot;
import mdvrp.model.Location;
import mdvrp.model.Truck;
import mdvrp.simulation.SimulationUtils;
import mdvrp.state.GlobalState;
import mdvrp.simulation.TruckState;

import java.util.*;
import java.util.stream.Collectors;

import static mdvrp.simulation.SimulationUtils.*;
import static mdvrp.state.GlobalState.*;

public class TabuSearchPlanner {

    private static final int TS_MAX_ITERATIONS = GlobalState.TS_MAX_ITERATIONS;
    private static final int TS_TABU_TENURE = GlobalState.TS_TABU_TENURE;

    private static final Queue<Move> tabuQueue = new LinkedList<>();
    private static final Set<Move> tabuSet = new HashSet<>();

    public static PlanningSolution planRoutes(List<CustomerPart> customersToServe, int planningStartTime) {
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

            // Vecindario 2: Reallocate
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
            if (iter > 0 && iter % 100 == 0) {
                System.out.println("  Iter " + iter + " | Costo Actual: " + formatCost(currentSolution.totalCost) + " | Mejor: " + formatCost(bestSolution.totalCost) + " | Sin Asignar: " + currentSolution.unassignedParts.size());
            }

        }

        long endTime = System.currentTimeMillis();
        System.out.println("\n🏁 Búsqueda Tabú completada en " + (endTime - startTime) + " ms.");
        System.out.println("🏆 Mejor solución encontrada:");
        System.out.println("  Costo Total (para Optimizador): " + formatCost(bestSolution.totalCost));
        System.out.println("  Costo Operacional (Rutas Factibles): " + formatCost(bestSolution.operationalFuelCost));
        System.out.println("  Totalmente Factible: " + bestSolution.fullyFeasible);
        System.out.println("  Clientes Sin Asignar: " + bestSolution.unassignedParts.size());
        bestSolution.unassignedParts.forEach(p -> System.out.println("Ruta sin asignar: " + p.originalOrderId));
        System.out.println("  Rutas (" + bestSolution.routes.size() + "):");
        bestSolution.routes.forEach(r ->
                System.out.println("    " + r + " | Costo: " + formatCost(r.cost) + " | Fuel: " + String.format("%.2f", r.estimatedFuel) + " Gal | Feasible: " + r.feasible));

        return bestSolution;
    }

    public static PlanningSolution createInitialSolutionBestFit(List<CustomerPart> customersToServe, List<Truck> availableTrucks, int planningStartTime) {
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
    public static double calculatePlannedRouteLoad(PlannedRoute route) {
        if (route == null || route.sequence == null) return 0.0;
        return route.sequence.stream().mapToDouble(c -> c.demandM3).sum();
    }

    public static void evaluateSolution(PlanningSolution solution, int planningStartTime) {
        solution.totalCost = 0;
        solution.operationalFuelCost = 0; // Costo real de fuel
        solution.fullyFeasible = true;
        if(solution.routes == null) { return; }

        for (PlannedRoute r : solution.routes) {
            evaluatePlannedRoute(r, planningStartTime);
            if (!r.feasible) {
                solution.fullyFeasible = false;
            }

            if (r.feasible) {
                // Acumular costo CON penalización para la optimización
                if (solution.totalCost != Double.POSITIVE_INFINITY) {
                    if (r.cost != Double.POSITIVE_INFINITY) {
                        solution.totalCost += r.cost;
                    } else { solution.totalCost = Double.POSITIVE_INFINITY; }
                }
                // Acumular costo de fuel SIN penalización
                if (solution.operationalFuelCost != Double.POSITIVE_INFINITY) {
                    if (r.estimatedFuel != Double.POSITIVE_INFINITY) {
                        solution.operationalFuelCost += r.estimatedFuel;
                    } else { solution.operationalFuelCost = Double.POSITIVE_INFINITY; }
                }
            } else {
                solution.totalCost = Double.POSITIVE_INFINITY;
                solution.operationalFuelCost = Double.POSITIVE_INFINITY;
            }
        }

        if (!solution.unassignedParts.isEmpty()) {
            solution.fullyFeasible = false;
            solution.totalCost = Double.POSITIVE_INFINITY;
            solution.operationalFuelCost = Double.POSITIVE_INFINITY;
        }
    }

    public static void evaluatePlannedRoute(PlannedRoute route, int planningStartTime) {
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

    public static RouteEvaluationResult calculatePlannedRouteCostAndFuel(PlannedRoute route, int startTime) {
        RouteEvaluationResult result = new RouteEvaluationResult();
        double totalFuelConsumed = 0;
        double currentLoadM3 = 0;

        if (route == null || route.truck == null || route.startDepot == null || route.endDepot == null || route.sequence == null) {
            result.feasible = false;
            return result;
        }

        int currentTime = startTime + GlobalState.PRE_TRIP_CHECK_MINUTES;
        Location currentLocation = route.startDepot;
        double initialNeededLoad = route.sequence.stream().mapToDouble(c -> c.demandM3).sum();
        currentLoadM3 = Math.min(initialNeededLoad, route.truck.type.capacidadM3);
        double fuelRemaining = GlobalState.MAX_FUEL_GAL;

        // Log inicial
        // System.out.printf("PlanEval [%s]: Start %s @ t=%d, Load=%.2f, Fuel=%.2f\n",
        //        route.truck.id, currentLocation, currentTime, currentLoadM3, fuelRemaining);


        // Iterar por los clientes en la secuencia planificada
        for (CustomerPart customer : route.sequence) {

            // Si la carga actual es menor que la demanda del siguiente cliente (con tolerancia)
            if (currentLoadM3 < customer.demandM3 - 0.01) {
                result.hypotheticalReloads++;
                // System.out.printf("    PlanEval [%s]: Needs GLP Reload (%.2f < %.2f) before CPart %d\n",
                //         route.truck.id, currentLoadM3, customer.demandM3, customer.partId);

                Depot reloadDepot = SimulationUtils.findBestDepotForReload(currentLocation, customer.demandM3);

                if (reloadDepot == null) {
                    // System.out.println("      PlanEval: No suitable depot found. Route INFEASIBLE.");
                    result.feasible = false; return result;
                }

                int distToDepot = SimulationUtils.distanciaReal(currentLocation, reloadDepot);
                int distDepotToCustomer = SimulationUtils.distanciaReal(reloadDepot, customer);

                if (distToDepot == Integer.MAX_VALUE || distDepotToCustomer == Integer.MAX_VALUE) {
                    // System.out.println("      PlanEval: Path to/from reload depot blocked. Route INFEASIBLE.");
                    result.feasible = false; return result;
                }

                // Calcular tiempo extra (viaje al depot + tiempo de carga)
                int travelTimeToDepot = (int) Math.round(distToDepot * SimulationUtils.MINUTOS_POR_KM);
                int currentTotalExtraTime = travelTimeToDepot + GlobalState.RELOAD_GLP_MINUTES;
                result.extraTimeFromReloads += currentTotalExtraTime;

                // Calcular fuel para ir al depot
                double fuelToDepot = SimulationUtils.calculateFuelConsumed(distToDepot, currentLoadM3, route.truck);
                if (fuelToDepot > fuelRemaining) {
                    // System.out.println("      PlanEval: Not enough fuel to reach reload depot. Route INFEASIBLE.");
                    result.feasible = false; return result;
                }
                // Consumir fuel, añadir al total, y repostar *combustible* (asumido en depot)
                totalFuelConsumed += fuelToDepot;
                fuelRemaining -= fuelToDepot;
                fuelRemaining = GlobalState.MAX_FUEL_GAL;

                // Penalizar el costo de la ruta por necesitar recarga
                result.penaltyCost += RELOAD_PENALTY_COST_GAL;

                // SIMULAR LA RECARGA GLP
                currentLoadM3 = route.truck.type.capacidadM3;
                // System.out.printf("      PlanEval: Simulating GLP reload at %s. New Load=%.2f. Time increases by %d min.\n",
                //         reloadDepot.id, currentLoadM3, currentTotalExtraTime);


                // Actualizar ubicación y tiempo actuales
                currentTime += currentTotalExtraTime; // Añadir tiempo de viaje a depot + recarga
                currentLocation = reloadDepot; // Ahora estamos en el depot
            }

            // Proceder con el viaje desde la ubicación actual (cliente anterior o depot de recarga) hacia el cliente
            int distToCustomer = SimulationUtils.distanciaReal(currentLocation, customer);
            if (distToCustomer == Integer.MAX_VALUE) {
                // System.out.printf("    PlanEval [%s]: Path blocked to CPart %d. Route INFEASIBLE.\n", route.truck.id, customer.partId);
                result.feasible = false; return result;
            }

            double fuelNeeded = SimulationUtils.calculateFuelConsumed(distToCustomer, currentLoadM3, route.truck);
            if (fuelNeeded > fuelRemaining) {
                // System.out.printf("    PlanEval [%s]: Not enough fuel (%.2f > %.2f) to reach CPart %d. Route INFEASIBLE.\n",
                //        route.truck.id, fuelNeeded, fuelRemaining, customer.partId);
                result.feasible = false; return result;
            }

            totalFuelConsumed += fuelNeeded;
            fuelRemaining -= fuelNeeded;
            currentTime += (int) Math.round(distToCustomer * SimulationUtils.MINUTOS_POR_KM);
            // System.out.printf("    PlanEval [%s]: Traveled to %s. Current Time: %d, Fuel Rem: %.2f\n",
            //         route.truck.id, customer, currentTime, fuelRemaining);


            if (currentTime > customer.deadlineMinutes) {
                // System.out.printf("    PlanEval [%s]: Deadline miss for CPart %d (Arrives: %d, Deadline: %d). Route INFEASIBLE.\n",
                //        route.truck.id, customer.partId, currentTime, customer.deadlineMinutes);
                result.feasible = false; return result;
            }

            currentTime += GlobalState.DISCHARGE_TIME_MINUTES;
            currentLocation = customer;
            currentLoadM3 -= customer.demandM3;
            if (currentLoadM3 < -0.01) { currentLoadM3 = 0; } // Ajuste por precisión
            // System.out.printf("    PlanEval [%s]: Discharged at %s. Current Time: %d, Load Rem: %.2f\n",
            //        route.truck.id, customer, currentTime, currentLoadM3);


        }

        int distReturn = SimulationUtils.distanciaReal(currentLocation, route.endDepot);
        if (distReturn == Integer.MAX_VALUE) { result.feasible = false; return result; }

        double fuelReturn = SimulationUtils.calculateFuelConsumed(distReturn, currentLoadM3, route.truck);
        if (fuelReturn > fuelRemaining) { result.feasible = false; return result; }

        totalFuelConsumed += fuelReturn;
        currentTime += (int) Math.round(distReturn * SimulationUtils.MINUTOS_POR_KM);
        // System.out.printf("    PlanEval [%s]: Returned to %s. Final Time: %d, Total Fuel: %.2f\n",
        //        route.truck.id, route.endDepot, currentTime, totalFuelConsumed);

        result.feasible = true;
        result.endTime = currentTime;
        result.fuel = totalFuelConsumed;
        result.cost = totalFuelConsumed + result.penaltyCost;

        return result;
    }

    // Aplicar 20pt
    public static void apply2OptToPlannedRoute(PlannedRoute route, int index1, int index2) {
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

    public static PlannedRoute findPlannedRouteInSolution(PlanningSolution solution, String truckId) {
        for(PlannedRoute r : solution.routes){
            if(r.truck.id.equals(truckId))
                return r;
        }
        return null;
    }

}
