public class TiempoUtils {

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