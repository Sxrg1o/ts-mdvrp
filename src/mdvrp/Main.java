package mdvrp;

import mdvrp.model.CustomerPart;
import mdvrp.planner.TabuSearchPlanner;
import mdvrp.simulation.Simulator;
import mdvrp.state.GlobalState;
import mdvrp.ui.SimulationVisualizer;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Inicializar el Estado Global
            GlobalState.initialize("pedidos.txt", "bloqueos.txt");

            // 2. Crear instancias de los componentes principales
            TabuSearchPlanner planner = new TabuSearchPlanner();
            Simulator simulator = new Simulator(planner); // Pasar el planner al simulador

            // 3. Configurar y correr la simulación
            int simulationDuration = 40 * 24 * 60; // 1 semana (ejemplo)
            boolean enableReplanning = true;
            Simulator.runSimulation(simulationDuration, enableReplanning);

            // 4. Planificación Final (Opcional, si quedan partes no servidas)
            // Nótese que getUnservedCustomerParts fue movido a Simulator, ¿quizás debería estar en GlobalState o Utils?
            // Accedamos a través de GlobalState por simplicidad ahora.
            List<CustomerPart> finalUnserved = GlobalState.activeCustomerParts; // O una copia si se modifica

            // La replanificación ya se hizo durante la simulación.
            // ¿Realmente necesitamos una planificación final separada?
            // Si la simulación se detuvo con partes activas, podría tener sentido.
            // Comentemos la planificación final por ahora, ya que la replanificación está activa.
        /*
        PlanningSolution finalSolution = null;
        if (!finalUnserved.isEmpty()) {
            System.out.println("\n🚀 Planificación Final de partes restantes... (" + finalUnserved.size() + ")");
            // Usar el tiempo final de la simulación como referencia?
            finalSolution = planner.planRoutes(finalUnserved, GlobalState.currentSimTime);
            // ¿Aplicar esta solución final? No se simulará más.
             if (finalSolution != null) {
                System.out.println("  Solución Final | Costo: " + SimulationUtils.formatCost(finalSolution.totalCost) + " | Sin Asignar: " + finalSolution.unassignedParts.size());
                // Podríamos fusionar esta solución con el historial para visualización? Complejo.
            }
        } else {
            System.out.println("✅ Todas las partes fueron atendidas o asignadas durante la simulación.");
        }
        */

            if (!finalUnserved.isEmpty()) {
                System.out.println(" T_T Quedaron " + finalUnserved.size() + " partes sin servir al final de la simulación.");
                // Imprimir detalles si se desea
            } else {
                System.out.println("✅ Todas las partes activas fueron atendidas.");
            }


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
}
