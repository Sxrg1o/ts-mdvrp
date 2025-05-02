package mdvrp.state;

import mdvrp.model.*;
import mdvrp.simulation.IOUtils;
import mdvrp.simulation.TruckState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalState {

    // Constantes del problema principal
    public static final int GRID_WIDTH = 70;
    public static final int GRID_HEIGHT = 50;
    public static final double VELOCIDAD_KMH = 50.0;
    public static final int PRE_TRIP_CHECK_MINUTES = 15;
    public static final int DISCHARGE_TIME_MINUTES = 15;
    public static final int RELOAD_GLP_MINUTES = 15;
    public static final double MAX_FUEL_GAL = 25.0;
    public static final double MAX_TRUCK_CAPACITY_M3 = 25.0;
    public static final int TS_MAX_ITERATIONS = 400;
    public static final int TS_TABU_TENURE = 15;
    public static final double RELOAD_PENALTY_COST_GAL = 0.1;

    // Estado global de la simulación
    public static boolean[][] blockedNodes = new boolean[GRID_WIDTH][GRID_HEIGHT];
    public static List<Depot> depots = new ArrayList<>();
    public static List<Truck> fleet = new ArrayList<>();
    public static Map<String, TruckState> truckStates = new HashMap<>();
    public static List<CustomerPart> activeCustomerParts = new ArrayList<>();
    public static List<Pedido> pendingPedidos = new ArrayList<>();
    public static List<Bloqueo> definedBloqueos = new ArrayList<>();
    public static int currentSimTime = 0;

    public static void initialize(String pedidosFile, String bloqueosFile) throws Exception {
        System.out.println("Inicializando estado global...");

        // Limpiar estado previo si es necesario
        depots.clear();
        fleet.clear();
        truckStates.clear();
        activeCustomerParts.clear();
        pendingPedidos.clear();
        definedBloqueos.clear();
        blockedNodes = new boolean[GRID_WIDTH][GRID_HEIGHT];
        currentSimTime = 0;
        CustomerPart.nextPartId = 0;

        // Cargar datos de archivos
        pendingPedidos = IOUtils.cargarPedidos(pedidosFile);
        definedBloqueos = IOUtils.cargarBloqueos(bloqueosFile);

        // Crear Depósitos
        depots.add(new Depot("Planta", 12, 8, Double.POSITIVE_INFINITY));
        depots.add(new Depot("Norte", 42, 42, 160.0));
        depots.add(new Depot("Este", 63, 3, 160.0));
        System.out.println("Depósitos creados: " + depots.size());

        // Crear Flota y Estados de Camión
        Depot mainDepot = depots.get(0);
        int count = 0;
        String[] types = {"TA", "TB", "TC", "TD"};
        int[] counts = {2, 4, 4, 10};
        TruckType[] enumTypes = TruckType.values();

        if (types.length != enumTypes.length || types.length != counts.length) {
            throw new IllegalStateException("Configuración de tipos/conteos de camiones inconsistente.");
        }

        for (int typeIdx = 0; typeIdx < types.length; typeIdx++) {
            TruckType currentType = enumTypes[typeIdx];
            // Validar que el nombre coincida si es necesario: assert types[typeIdx].equals(currentType.name());
            for (int i = 1; i <= counts[typeIdx]; i++) {
                String truckId = String.format("%s%02d", types[typeIdx], i);
                Truck truck = new Truck(truckId, currentType, mainDepot);
                fleet.add(truck);
                truckStates.put(truckId, new TruckState(truck));
                count++;
            }
        }
        System.out.println("Flota creada: " + count + " camiones.");
        System.out.println("Inicialización completa.");
    }

    // Prevenir instanciación si solo contiene estáticos
    private GlobalState() {}

}
