package cc.carretera;

import org.jcsp.lang.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class CarreteraCSP implements Carretera {
    private static final int SEGMENTOS = 5;
    private static final int CARRILES = 3;

    // Canales para comunicación
    private final One2OneChannel[] entraChans;
    private final One2OneChannel[] avanzaChans;
    private final One2OneChannel[] circulaChans;
    private final One2OneChannel[] saleChans;
    private final One2OneChannel tickChan;

    // Proceso de la carretera
    private final CSProcess procesoCarretera;

    public CarreteraCSP() {
        // Crear canales
        entraChans = Channel.one2oneArray(10);
        avanzaChans = Channel.one2oneArray(10);
        circulaChans = Channel.one2oneArray(10);
        saleChans = Channel.one2oneArray(10);
        tickChan = Channel.one2one();

        // Proceso principal
        procesoCarretera = new CSProcess() {
            public void run() {
                // Estructuras de estado
                Map<String, Pos> posiciones = new HashMap<>();
                Map<String, Integer> ticksRestantes = new HashMap<>();
                Map<Integer, Set<Integer>> ocupados = new HashMap<>();

                // Inicializar ocupación
                for (int seg = 1; seg <= SEGMENTOS; seg++) {
                    ocupados.put(seg, new HashSet<>());
                }

                final Alternative alt = new Alternative(new AltingChannelInput[] {
                        tickChan.in(),
                        entraChans[0].in(),
                        avanzaChans[0].in(),
                        circulaChans[0].in(),
                        saleChans[0].in()
                });

                while (true) {
                    int index = alt.select();

                    if (index == 0) {
                        // Tick
                        tickChan.in().read();

                        for (String id : new ArrayList<>(ticksRestantes.keySet())) {
                            int ticks = ticksRestantes.get(id);
                            if (ticks > 0) {
                                ticksRestantes.put(id, ticks - 1);
                            }
                        }
                    } else if (index == 1) {
                        // Entrar
                        Object[] datos = (Object[]) entraChans[0].in().read();
                        String id = (String) datos[0];
                        int tks = (Integer) datos[1];

                        int carrilLibre = -1;
                        for (int c = 1; c <= CARRILES; c++) {
                            if (!ocupados.get(1).contains(c)) {
                                carrilLibre = c;
                                break;
                            }
                        }

                        if (carrilLibre != -1) {
                            Pos pos = new Pos(1, carrilLibre);
                            posiciones.put(id, pos);
                            ticksRestantes.put(id, tks);
                            ocupados.get(1).add(carrilLibre);
                            entraChans[0].out().write(pos);
                        }
                    } else if (index == 2) {
                        // Avanzar
                        Object[] datos = (Object[]) avanzaChans[0].in().read();
                        String id = (String) datos[0];
                        int tks = (Integer) datos[1];

                        Pos posActual = posiciones.get(id);
                        int segActual = posActual.getSegmento();
                        int carrilActual = posActual.getCarril();

                        if (segActual < SEGMENTOS && ticksRestantes.get(id) == 0) {
                            int siguienteSeg = segActual + 1;
                            int nuevoCarril = -1;

                            for (int c = 1; c <= CARRILES; c++) {
                                if (!ocupados.get(siguienteSeg).contains(c)) {
                                    nuevoCarril = c;
                                    break;
                                }
                            }

                            if (nuevoCarril != -1) {
                                ocupados.get(segActual).remove(carrilActual);
                                Pos nuevaPos = new Pos(siguienteSeg, nuevoCarril);
                                posiciones.put(id, nuevaPos);
                                ticksRestantes.put(id, tks);
                                ocupados.get(siguienteSeg).add(nuevoCarril);
                                avanzaChans[0].out().write(nuevaPos);
                            }
                        }
                    } else if (index == 3) {
                        // Circulando
                        String id = (String) circulaChans[0].in().read();
                        // No necesita respuesta
                    } else if (index == 4) {
                        // Salir
                        String id = (String) saleChans[0].in().read();
                        Pos pos = posiciones.get(id);

                        if (pos != null && pos.getSegmento() == SEGMENTOS && ticksRestantes.get(id) == 0) {
                            ocupados.get(SEGMENTOS).remove(pos.getCarril());
                            posiciones.remove(id);
                            ticksRestantes.remove(id);
                        }
                    }
                }
            }
        };
    }

    @Override
    public Pos entrar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        entraChans[0].out().write(new Object[] { id, tks });
        return (Pos) resp.in().read();
    }

    @Override
    public Pos avanzar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        avanzaChans[0].out().write(new Object[] { id, tks });
        return (Pos) resp.in().read();
    }

    @Override
    public void circulando(String id) {
        circulaChans[0].out().write(id);
    }

    @Override
    public void salir(String id) {
        saleChans[0].out().write(id);
    }

    @Override
    public void tick() {
        tickChan.out().write(null);
    }

    public void iniciar() {
        new Parallel(new CSProcess[] { procesoCarretera }).run();
    }
}