package mdvrp.ui;

import mdvrp.model.CustomerPart;
import mdvrp.planner.PlannedRoute;
import mdvrp.planner.PlanningSolution;
import mdvrp.simulation.TruckState;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static mdvrp.state.GlobalState.*;

public class SimulationVisualizer {

    public static void visualizeSolution() {
        System.out.println("Preparando visualización...");

        System.out.println("Preparando visualización final del estado de los camiones...");

        List<GridVisualizer.Punto> cliVis_temp = new ArrayList<>();
        List<GridVisualizer.RutaVisual> rutVis_temp = new ArrayList<>();
        Set<Integer> uniquePartIdsInRoutes = new HashSet<>();

        Color[] C = {Color.BLUE, Color.RED, Color.ORANGE, Color.MAGENTA, Color.PINK, Color.YELLOW.darker(), Color.CYAN, Color.GRAY, Color.GREEN.darker().darker(), Color.BLUE.darker(), Color.RED.darker(), Color.ORANGE.darker()};
        int ci = 0;

        // Iterar por TODOS los estados de camión para obtener su última ruta
        for (TruckState ts : truckStates.values()) {
            for(PlannedRoute route : ts.routes) {
                // Solo visualizar si la ruta existe, tiene secuencia y no está vacía
                if (route != null && route.sequence != null && !route.sequence.isEmpty() && route.startDepot != null && route.endDepot != null) {
                    List<GridVisualizer.Punto> seq = new ArrayList<>();
                    // Punto inicial (Depot)
                    seq.add(new GridVisualizer.Punto(route.startDepot.x, route.startDepot.y, GridVisualizer.PuntoTipo.DEPOSITO, route.startDepot.id));
                    // Puntos intermedios (Clientes)
                    route.sequence.forEach(part -> {
                        seq.add(new GridVisualizer.Punto(part.x, part.y, GridVisualizer.PuntoTipo.CLIENTE, String.valueOf(part.partId)));
                        uniquePartIdsInRoutes.add(part.partId);
                    });
                    // Punto final (Depot)
                    seq.add(new GridVisualizer.Punto(route.endDepot.x, route.endDepot.y, GridVisualizer.PuntoTipo.DEPOSITO, route.endDepot.id));

                    Color clr = C[ci % C.length]; ci++;
                    rutVis_temp.add(new GridVisualizer.RutaVisual(route.truck.id, seq, clr));
                } else {
                    // Opcional: Podrías querer mostrar camiones IDLE en su depot
                    // System.out.println("INFO: Camión " + ts.truck.id + " sin ruta activa/asignada para visualizar.");
                }
            }
        }

        // Crear la lista de puntos de cliente únicos a partir de los IDs recolectados
        // Necesitamos encontrar los objetos CustomerPart correspondientes (asumiendo que siguen en alguna lista o podemos buscarlos)
        // Si `activeCustomerParts` se vacía al servir, necesitaremos otra fuente.
        // Asumamos por ahora que podemos reconstruir/encontrar los CustomerPart por ID si es necesario,
        // o que la `solution` final (si existe) tiene los `unassignedParts`.
        // UNA FORMA MÁS ROBUSTA: Buscar en la lista original de pedidos o una copia.
        // O MÁS SIMPLE: Crear los puntos directamente de las rutas (puede haber redundancia si no usamos Set<Punto>)

        // Vamos a construir cliVis_temp directamente de las partes en las rutas visualizadas
        Map<Integer, CustomerPart> allPartsEver = new HashMap<>();
        // Asumiendo que tenemos acceso a las partes:
        // cliVis_temp = uniquePartIdsInRoutes.stream()
        //           .map(partId -> findCustomerPartById(partId))
        //           .filter(Objects::nonNull)
        //           .map(part -> new GridVisualizer_MDVRP.Punto(
        //                   part.x, part.y, GridVisualizer_MDVRP.PuntoTipo.CLIENTE, String.valueOf(part.partId)))
        //           .collect(Collectors.toList());

        // Alternativa más simple (puede duplicar localizaciones si varios partId están en el mismo x,y):
        cliVis_temp = rutVis_temp.stream()
                .flatMap(rutaVisual -> rutaVisual.secuenciaCompleta.stream())
                .filter(punto -> punto.tipo == GridVisualizer.PuntoTipo.CLIENTE)
                .distinct()
                .collect(Collectors.toList());


        List<GridVisualizer.Punto> depVis_temp = depots.stream()
                .map(d -> new GridVisualizer.Punto(d.x, d.y, GridVisualizer.PuntoTipo.DEPOSITO, d.id))
                .collect(Collectors.toList());

        final List<GridVisualizer.Punto> finalDepVis = depVis_temp;
        final List<GridVisualizer.Punto> finalCliVis = cliVis_temp;
        final List<GridVisualizer.RutaVisual> finalRutVis = rutVis_temp;

        if (finalRutVis.isEmpty()) {
            System.out.println("Visualizador: No hay rutas asignadas/activas en los camiones para mostrar.");
            // return; // Salir si no hay nada que mostrar
        } else {
            System.out.println("Visualizador: Mostrando " + finalRutVis.size() + " rutas activas/últimas asignadas.");
        }


        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Visualización GLP - Rutas MDVRP (TS) - Estado Final Camiones");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            // Asegurarse que blockedNodes está actualizado al estado final de la simulación
            GridVisualizer p = new GridVisualizer(finalDepVis, finalCliVis, finalRutVis, blockedNodes);
            p.setPreferredSize(new Dimension(GRID_WIDTH * 12 + 50, GRID_HEIGHT * 12 + 50));
            JScrollPane sp = new JScrollPane(p);
            f.add(sp);
            f.setSize(900, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
