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
            Simulator simulator = new Simulator(planner);

            // 3. Configurar y correr la simulación
            int simulationDuration = 40 * 24 * 60; // 1 semana (ejemplo)
            boolean enableReplanning = true;
            Simulator.runSimulation(simulationDuration, enableReplanning);

            // 4. Planificación Final (Opcional, si quedan partes no servidas)
            List<CustomerPart> finalUnserved = GlobalState.activeCustomerParts;

            if (!finalUnserved.isEmpty()) {
                System.out.println(" T_T Quedaron " + finalUnserved.size() + " partes sin servir al final de la simulación.");
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
