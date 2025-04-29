import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Visualizador para soluciones MDVRP (Multi-Depot Vehicle Routing Problem).
 * Muestra depósitos, clientes, múltiples rutas de camiones con colores distintos
 * y las celdas bloqueadas.
 */
public class GridVisualizer_MDVRP extends JPanel {

    // --- Constantes de Dibujo ---
    // Usar las mismas dimensiones que el planificador
    static final int GRID_WIDTH = TabuSearchPlanner_MDVRP.GRID_WIDTH;
    static final int GRID_HEIGHT = TabuSearchPlanner_MDVRP.GRID_HEIGHT;
    static final int CELL_SIZE = 12; // Tamaño de celda en píxeles (ajustable)
    static final int NODE_SIZE = CELL_SIZE / 2; // Tamaño para dibujar nodos
    static final int NODE_OFFSET = CELL_SIZE / 4; // Offset para centrar nodos

    // --- Tipos de Puntos ---
    enum PuntoTipo {
        DEPOSITO,
        CLIENTE
    }

    // --- Estructuras de Datos para Visualización ---

    /**
     * Representa un punto en el grid (Depósito o Cliente).
     */
    public static class Punto { // Pública para ser usada externamente
        int x, y;
        PuntoTipo tipo;
        String label; // ID del Depósito o Cliente

        public Punto(int x, int y, PuntoTipo tipo, String label) {
            this.x = x;
            this.y = y;
            this.tipo = tipo;
            this.label = label;
        }

        // Métodos equals y hashCode para usar en Sets/Maps si es necesario
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Punto punto = (Punto) o;
            return x == punto.x && y == punto.y && tipo == punto.tipo && Objects.equals(label, punto.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, tipo, label);
        }
        @Override
        public String toString() { return label + "@(" + x + "," + y + ")"; }
    }

    /**
     * Representa una ruta completa para ser dibujada.
     */
    public static class RutaVisual {
        String truckId;
        List<Punto> secuenciaCompleta; // Incluye Depósito inicial y final
        Color color;

        public RutaVisual(String truckId, List<Punto> secuencia, Color color) {
            this.truckId = truckId;
            this.secuenciaCompleta = secuencia;
            this.color = color;
        }
    }

    // Clase interna para etiquetas de bloqueos (igual que antes)
    static class BloqueoEtiquetado {
        int x, y;
        String etiqueta; // Ahora podría ser el ID del camión/ruta

        BloqueoEtiquetado(int x, int y, String etiqueta) {
            this.x = x;
            this.y = y;
            this.etiqueta = etiqueta;
        }
    }


    // --- Atributos del Panel ---
    private final List<Punto> depots;
    private final List<Punto> customers;
    private final List<RutaVisual> routesToDraw;
    private final boolean[][] matrizBloqueado;
    private final List<Punto> puntosBloqueadosGrid; // Celdas exactas a pintar como bloqueadas
    private final List<BloqueoEtiquetado> bloqueosEnRutas; // Bloqueos encontrados en las rutas

    // --- Constructor ---
    public GridVisualizer_MDVRP(List<Punto> depots, List<Punto> customers, List<RutaVisual> routes, boolean[][] bloqueadoActual) {
        this.depots = (depots != null) ? depots : new ArrayList<>();
        this.customers = (customers != null) ? customers : new ArrayList<>();
        this.routesToDraw = (routes != null) ? routes : new ArrayList<>();
        this.matrizBloqueado = bloqueadoActual;

        this.puntosBloqueadosGrid = new ArrayList<>();
        this.bloqueosEnRutas = new ArrayList<>();

        // Pre-calcular las celdas bloqueadas para pintarlas
        if (this.matrizBloqueado != null) {
            for (int i = 0; i < GRID_WIDTH; i++) {
                for (int j = 0; j < GRID_HEIGHT; j++) {
                    if (this.matrizBloqueado[i][j]) {
                        // Añadir como Punto genérico para pintarlo
                        puntosBloqueadosGrid.add(new Punto(i, j, null, "Bloqueado"));
                    }
                }
            }
        }
        setBackground(Color.WHITE);
    }

    // --- Método Principal de Dibujo ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Limpiar etiquetas de la iteración anterior
        bloqueosEnRutas.clear();

        // 1. Dibujar Cuadrícula de fondo
        drawGrid(g2d);

        // 2. Dibujar Celdas Bloqueadas del grid
        drawBlockedCells(g2d);

        // 3. Dibujar Rutas (con BFS para camino real y detección de bloqueos)
        drawRoutes(g2d);

        // 4. Dibujar Nodos (Depósitos y Clientes) encima de las rutas
        drawNodes(g2d);

        // 5. Dibujar Etiquetas sobre los bloqueos encontrados en las rutas
        drawBlockedRouteLabels(g2d);
    }

    // --- Métodos Auxiliares de Dibujo ---

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(230, 230, 230)); // Gris muy claro
        int width = GRID_WIDTH * CELL_SIZE;
        int height = GRID_HEIGHT * CELL_SIZE;
        for (int i = 0; i <= GRID_WIDTH; i++) {
            g.drawLine(i * CELL_SIZE, 0, i * CELL_SIZE, height);
        }
        for (int j = 0; j <= GRID_HEIGHT; j++) {
            g.drawLine(0, j * CELL_SIZE, width, j * CELL_SIZE);
        }
    }

    private void drawBlockedCells(Graphics2D g) {
        g.setColor(Color.DARK_GRAY);
        for (Punto b : puntosBloqueadosGrid) {
            g.fillRect(b.x * CELL_SIZE, b.y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        }
    }

    private void drawRoutes(Graphics2D g) {
        // Usar un Set para evitar dibujar la misma etiqueta de bloqueo múltiples veces por celda
        Set<Point> labeledBlockedCells = new HashSet<>();

        for (RutaVisual ruta : routesToDraw) {
            g.setColor(ruta.color);
            g.setStroke(new BasicStroke(1.5f)); // Grosor de la línea de ruta

            List<Punto> secuencia = ruta.secuenciaCompleta;
            if (secuencia == null || secuencia.size() < 2) continue;

            // Dibujar segmento por segmento usando BFS
            for (int i = 0; i < secuencia.size() - 1; i++) {
                Punto origen = secuencia.get(i);
                Punto destino = secuencia.get(i + 1);

                List<Point> caminoReal = encontrarCaminoBFS(origen, destino);

                if (caminoReal != null && !caminoReal.isEmpty()) {
                    Point pPrev = new Point(origen.x, origen.y); // Iniciar desde el nodo origen
                    for (Point pActual : caminoReal) {
                        // Dibujar línea entre centros de celdas
                        g.drawLine(pPrev.x * CELL_SIZE + CELL_SIZE / 2, pPrev.y * CELL_SIZE + CELL_SIZE / 2,
                                pActual.x * CELL_SIZE + CELL_SIZE / 2, pActual.y * CELL_SIZE + CELL_SIZE / 2);

                        // Si esta celda del camino está bloqueada, etiquetarla
                        if (esBloqueado(pActual.x, pActual.y)) {
                            // Añadir etiqueta solo si no hemos puesto una en esta celda ya
                            if (!labeledBlockedCells.contains(pActual)) {
                                bloqueosEnRutas.add(new BloqueoEtiquetado(pActual.x, pActual.y, ruta.truckId));
                                labeledBlockedCells.add(pActual); // Marcar como etiquetada
                            }
                        }
                        pPrev = pActual; // Actualizar punto previo
                    }
                } else if (origen != null && destino != null) {
                    // Si no hay camino BFS (raro, pero posible si depot/cliente está aislado)
                    // Dibuja una línea recta punteada como indicación de problema
                    Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0);
                    g.setStroke(dashed);
                    g.setColor(Color.MAGENTA); // Color de advertencia
                    g.drawLine(origen.x * CELL_SIZE + CELL_SIZE / 2, origen.y * CELL_SIZE + CELL_SIZE / 2,
                            destino.x * CELL_SIZE + CELL_SIZE / 2, destino.y * CELL_SIZE + CELL_SIZE / 2);
                    // Restaurar stroke y color
                    g.setStroke(new BasicStroke(1.5f));
                    g.setColor(ruta.color);

                }
            }
        }
    }

    private void drawNodes(Graphics2D g) {
        Font nodeFont = new Font("Arial", Font.BOLD, 8);
        g.setFont(nodeFont);
        FontMetrics fm = g.getFontMetrics();

        // Dibujar Depósitos
        g.setColor(Color.GREEN.darker()); // Verde oscuro para depósitos
        for (Punto depot : depots) {
            g.fillRect(depot.x * CELL_SIZE + NODE_OFFSET / 2, depot.y * CELL_SIZE + NODE_OFFSET / 2,
                    CELL_SIZE - NODE_OFFSET, CELL_SIZE - NODE_OFFSET); // Cuadrado más grande
            // Etiqueta blanca dentro
            g.setColor(Color.WHITE);
            int labelWidth = fm.stringWidth(depot.label);
            g.drawString(depot.label, depot.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    depot.y * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 1);
            g.setColor(Color.GREEN.darker()); // Reset color
        }

        // Dibujar Clientes
        g.setColor(Color.RED); // Rojo para clientes
        for (Punto customer : customers) {
            g.fillOval(customer.x * CELL_SIZE + NODE_OFFSET, customer.y * CELL_SIZE + NODE_OFFSET,
                    NODE_SIZE, NODE_SIZE); // Círculo
            // Etiqueta negra al lado o encima (ajustar posición)
            g.setColor(Color.BLACK);
            // Dibujar ID del cliente ligeramente fuera del círculo para claridad
            g.drawString(customer.label, customer.x * CELL_SIZE + CELL_SIZE/2 , customer.y * CELL_SIZE );

            g.setColor(Color.RED); // Reset color
        }
    }

    private void drawBlockedRouteLabels(Graphics2D g) {
        g.setColor(Color.ORANGE); // Naranja para etiquetas de bloqueo en ruta
        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        Set<Point> drawnLabelLocations = new HashSet<>(); // Evitar sobreponer texto exacto

        for (BloqueoEtiquetado be : bloqueosEnRutas) {
            Point location = new Point(be.x, be.y);
            // Solo dibujar una etiqueta por celda bloqueada
            if (drawnLabelLocations.contains(location)) {
                continue;
            }

            int labelWidth = fm.stringWidth(be.etiqueta);
            // Centrar etiqueta en la celda bloqueada
            g.drawString(be.etiqueta, be.x * CELL_SIZE + (CELL_SIZE - labelWidth) / 2,
                    be.y * CELL_SIZE + fm.getAscent() + 1); // Un poco más abajo
            drawnLabelLocations.add(location);
        }
    }

    // --- Lógica Auxiliar ---

    /**
     * Verifica si una celda está marcada como bloqueada.
     */
    private boolean esBloqueado(int x, int y) {
        if (matrizBloqueado == null || x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT) {
            return false;
        }
        return matrizBloqueado[x][y];
    }

    /**
     * Encuentra el camino más corto (secuencia de celdas) usando BFS entre dos Puntos.
     * Adaptado para usar java.awt.Point internamente.
     *
     * @param from Punto de origen.
     * @param to   Punto de destino.
     * @return Lista de Points representando las celdas del camino (excluye 'from', incluye 'to'),
     * o null si no se encuentra camino.
     */
    private List<Point> encontrarCaminoBFS(Punto from, Punto to) {
        if (from == null || to == null || matrizBloqueado == null) return null;

        Point startPoint = new Point(from.x, from.y);
        Point endPoint = new Point(to.x, to.y);

        if (startPoint.equals(endPoint)) return new ArrayList<>(); // Camino de longitud 0

        Queue<Point> queue = new LinkedList<>();
        Map<Point, Point> predecesores = new HashMap<>(); // Almacena: Point -> Point_Previo
        Set<Point> visited = new HashSet<>();

        queue.add(startPoint);
        visited.add(startPoint);
        predecesores.put(startPoint, null); // Inicio no tiene predecesor

        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}; // 4 direcciones

        Point currentPoint = null;
        boolean found = false;

        while (!queue.isEmpty()) {
            currentPoint = queue.poll();

            if (currentPoint.equals(endPoint)) {
                found = true;
                break; // Llegamos al destino
            }

            for (int[] dir : dirs) {
                int nx = currentPoint.x + dir[0];
                int ny = currentPoint.y + dir[1];
                Point nextPoint = new Point(nx, ny);

                // Verificar límites y si no está visitado
                if (nx >= 0 && nx < GRID_WIDTH && ny >= 0 && ny < GRID_HEIGHT && !visited.contains(nextPoint)) {
                    // Si no está bloqueado, añadirlo
                    if (!matrizBloqueado[nx][ny]) {
                        visited.add(nextPoint);
                        predecesores.put(nextPoint, currentPoint); // Guardar de dónde vinimos
                        queue.add(nextPoint);
                    }
                }
            }
        }

        if (!found) {
            // System.err.println("Visualizer BFS: No se encontró ruta de " + startPoint + " a " + endPoint);
            return null; // No se encontró ruta
        }

        // Reconstruir el camino desde el final
        LinkedList<Point> path = new LinkedList<>(); // Usar LinkedList por addFirst
        Point step = endPoint;
        while (step != null && !step.equals(startPoint)) {
            path.addFirst(step);
            step = predecesores.get(step); // Ir al predecesor
        }

        return path;
    }

} // Fin clase GridVisualizer_MDVRP