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
        Set<Integer> uniquePartIdsInRoutes = new HashSet<>(); // Para evitar duplicar puntos de cliente

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
                        uniquePartIdsInRoutes.add(part.partId); // Registrar ID para la lista de clientes
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
        Map<Integer, CustomerPart> allPartsEver = new HashMap<>(); // Necesitaríamos poblar esto al crear las partes
        // Asumiendo que tenemos acceso a las partes:
        // cliVis_temp = uniquePartIdsInRoutes.stream()
        //           .map(partId -> findCustomerPartById(partId)) // Necesitaríamos esta función
        //           .filter(Objects::nonNull)
        //           .map(part -> new GridVisualizer_MDVRP.Punto(
        //                   part.x, part.y, GridVisualizer_MDVRP.PuntoTipo.CLIENTE, String.valueOf(part.partId)))
        //           .collect(Collectors.toList());

        // Alternativa más simple (puede duplicar localizaciones si varios partId están en el mismo x,y):
        cliVis_temp = rutVis_temp.stream()
                .flatMap(rutaVisual -> rutaVisual.secuenciaCompleta.stream()) // Obtener todos los puntos de todas las rutas
                .filter(punto -> punto.tipo == GridVisualizer.PuntoTipo.CLIENTE) // Filtrar solo los clientes
                .distinct() // Eliminar duplicados exactos (mismo objeto Punto)
                .collect(Collectors.toList());


        // Depósitos (sin cambios)
        List<GridVisualizer.Punto> depVis_temp = depots.stream()
                .map(d -> new GridVisualizer.Punto(d.x, d.y, GridVisualizer.PuntoTipo.DEPOSITO, d.id))
                .collect(Collectors.toList());

        final List<GridVisualizer.Punto> finalDepVis = depVis_temp;
        final List<GridVisualizer.Punto> finalCliVis = cliVis_temp; // Usar la lista derivada
        final List<GridVisualizer.RutaVisual> finalRutVis = rutVis_temp; // Usar la lista construida

        if (finalRutVis.isEmpty()) {
            System.out.println("Visualizador: No hay rutas asignadas/activas en los camiones para mostrar.");
            // Decidir si mostrar el panel vacío o no hacer nada
            // return; // Salir si no hay nada que mostrar?
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

        /*if (solution == null) {
            System.out.println("Se mostrará ");
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
        });*/
    }
}
