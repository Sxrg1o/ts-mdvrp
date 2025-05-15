package mdvrp;

import mdvrp.model.CustomerPart;
import mdvrp.model.Depot;
import mdvrp.model.Location;
import mdvrp.model.Truck;
import mdvrp.planner.PlannedRoute;
import mdvrp.planner.TabuSearchPlanner;
import mdvrp.simulation.Simulator;
import mdvrp.simulation.TruckState;
import mdvrp.state.GlobalState;
import mdvrp.ui.SimulationVisualizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Inicializar el Estado Global
            GlobalState.initialize("pedidos.txt", "bloqueos.txt");

            // 2. Crear instancias de los componentes principales
            TabuSearchPlanner planner = new TabuSearchPlanner();
            Simulator simulator = new Simulator(planner);

            // 3. Configurar y correr la simulación
            int simulationDuration = 8 * 24 * 60; // 1 semana (ejemplo)
            boolean enableReplanning = true;
            simulator.runSimulation(simulationDuration, enableReplanning);

            // 4. Planificación Final (Opcional, si quedan partes no servidas)
            List<CustomerPart> finalUnserved = GlobalState.activeCustomerParts;

            if (!finalUnserved.isEmpty()) {
                System.out.println(" T_T Quedaron " + finalUnserved.size() + " partes sin servir al final de la simulación.");
                for(CustomerPart cp : finalUnserved) {
                    System.out.println(" Parte: " + cp.partId + "(" + cp.originalClientId + ")");
                }
            } else {
                System.out.println("✅ Todas las partes activas fueron atendidas.");
            }

            System.out.println("\n--- Historial Completo de Rutas por Camión ---");
            printAllTruckHistories();

            // 5. Visualizar el resultado final (historial de rutas)
            System.out.println("\n📈 Mostrando visualización del historial de rutas...");
            SimulationVisualizer.visualizeSolution();

        } catch (Exception e) {
            System.err.println("Error fatal en la aplicación:");
            e.printStackTrace();
        } finally {
            System.out.println("\nPrograma terminado.");
        }
    }

    public static void printAllTruckHistories() {
        // Es buena idea ordenar la flota por ID para una salida consistente
        List<Truck> sortedFleet = new ArrayList<>(GlobalState.fleet);
        sortedFleet.sort(Comparator.comparing(truck -> truck.id));

        for (Truck truck : sortedFleet) {
            TruckState ts = GlobalState.truckStates.get(truck.id);
            StringBuilder sb = new StringBuilder();
            sb.append(truck.id).append(": ");

            // Verificar si el camión tiene estado y historial
            if (ts == null || ts.routes == null || ts.routes.isEmpty()) {
                sb.append("No routes assigned/executed.");
            } else {
                List<PlannedRoute> history = ts.routes;
                Location lastPrintedLocation = null; // Para evitar imprimir depots duplicados consecutivamente

                for (int i = 0; i < history.size(); i++) {
                    PlannedRoute route = history.get(i);

                    // 1. Manejar el punto de inicio de la ruta
                    Depot start = route.startDepot;
                    if (start != null) {
                        // Solo imprimir el depot de inicio si es la PRIMERA localización de todas
                        // O si es DIFERENTE del depot final de la ruta ANTERIOR.
                        if (lastPrintedLocation == null || !start.equals(lastPrintedLocation)) {
                            if (lastPrintedLocation != null) { // Añadir separador si no es el primero
                                sb.append(" -> ");
                            }
                            sb.append(start.id); // Usar el ID del Depot
                            lastPrintedLocation = start;
                        }
                    } else {
                        // Ruta inválida sin inicio? Marcar o ignorar
                        if (lastPrintedLocation == null) sb.append("InvalidStart");
                        else sb.append(" -> InvalidStart");
                        lastPrintedLocation=null; // Resetear para evitar problemas
                    }


                    // 2. Añadir la secuencia de clientes (CustomerPart)
                    if (route.sequence != null) {
                        for (CustomerPart part : route.sequence) {
                            sb.append(" -> ").append(part.partId).append("(").append(part.originalClientId).append(")"); // Usar el ID de la parte
                            lastPrintedLocation = part; // Actualizar la última ubicación visitada
                        }
                    }

                    // 3. Añadir el punto final de la ruta
                    Depot end = route.endDepot;
                    if (end != null) {
                        // Siempre añadimos el final de la ruta actual.
                        // La lógica al inicio del bucle manejará si se imprime o no
                        // el inicio de la *siguiente* ruta si coincide con este final.
                        sb.append(" -> ").append(end.id);
                        lastPrintedLocation = end;
                    } else {
                        // Ruta inválida sin final? Marcar o ignorar
                        sb.append(" -> InvalidEnd");
                        lastPrintedLocation = null; // Resetear
                    }
                } // Fin del bucle de rutas para un camión
            }
            // Imprimir la línea completa para este camión
            System.out.println(sb.toString());
        } // Fin del bucle de camiones
    }

}
