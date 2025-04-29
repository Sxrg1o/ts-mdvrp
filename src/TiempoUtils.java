public class TiempoUtils {

    /**
     * Parsea una marca de tiempo en formato como "01d00h24m" a minutos totales.
     * @param marca String de tiempo.
     * @return Minutos totales.
     */
    public static int parsearMarcaDeTiempo(String marca) {
        try {
            String[] partesDia = marca.split("d");
            int dias = Integer.parseInt(partesDia[0]);
            String resto = partesDia[1];

            String[] partesHora = resto.split("h");
            int horas = Integer.parseInt(partesHora[0]);
            String restoMin = partesHora[1];

            String[] partesMin = restoMin.split("m");
            int minutos = Integer.parseInt(partesMin[0]);

            return dias * 24 * 60 + horas * 60 + minutos;
        } catch (Exception e) {
            System.err.println("Error parseando marca de tiempo: " + marca + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * Parsea una duración/límite en formato como "8h" a minutos totales.
     * @param limite String de hora límite.
     * @return Minutos totales.
     */
    public static int parsearHoras(String limite) {
        try {
            String horasStr = limite.replace("h", "").trim();
            int horas = Integer.parseInt(horasStr);
            return horas * 60;
        } catch (NumberFormatException e) {
            System.err.println("Error parseando hora límite: " + limite + " - " + e.getMessage());
            return Integer.MAX_VALUE;
        }
    }

}