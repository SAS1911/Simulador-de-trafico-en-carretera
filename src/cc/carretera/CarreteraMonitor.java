package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarreteraMonitor implements Carretera {
  private final int SEGMENTOS;
  private final int CARRILES;

  private final Map<String, Pair<Pos, Integer>> coches = new HashMap<>();

  private final Monitor monitor = new Monitor();
  private final Monitor.Cond esperaEntrar = monitor.newCond();
  private final Monitor.Cond esperaAvanzar = monitor.newCond();
  private final Monitor.Cond esperaCircular = monitor.newCond();

  public CarreteraMonitor(int segmentos, int carriles) {
    this.SEGMENTOS = segmentos;
    this.CARRILES = carriles;
  }

  public Pos entrar(String id, int tks) {
    monitor.enter();
    try {
      while (carrilesLibres(1).isEmpty()) {
        esperaEntrar.await();
      }
      int carril = carrilesLibres(1).iterator().next();
      Pos pos = new Pos(1, carril);
      coches.put(id, new Pair<>(pos, tks));
      return pos;
    } finally {
      monitor.leave();
    }
  }

  public Pos avanzar(String id, int tks) {
    monitor.enter();
    try {
      Pair<Pos, Integer> info = coches.get(id);
      if (info == null)
        throw new IllegalArgumentException("Coche no encontrado: " + id);

      Pos pos = info.getFirst();
      int seg = pos.getSegmento();
      int carril = pos.getCarril();

      if (seg + 1 > SEGMENTOS)
        return pos;

      while (!carrilesLibres(seg + 1).contains(carril)) {
        esperaAvanzar.await();
      }

      Pos nuevaPos = new Pos(seg + 1, carril);
      coches.put(id, new Pair<>(nuevaPos, tks));

      esperaEntrar.signal(); // puede liberar hueco en el primer segmento
      esperaAvanzar.signal(); // otro coche puede avanzar
      return nuevaPos;
    } finally {
      monitor.leave();
    }
  }

  public void circulando(String id) {
    monitor.enter();
    try {
      while (coches.containsKey(id) && coches.get(id).getSecond() > 0) {
        esperaCircular.await();
      }
    } finally {
      monitor.leave();
    }
  }

  public void salir(String id) {
    monitor.enter();
    try {
      coches.remove(id);
      esperaEntrar.signal();
      esperaAvanzar.signal();
    } finally {
      monitor.leave();
    }
  }

  public void tick() {
    monitor.enter();
    try {
      boolean notificar = false;

      for (Map.Entry<String, Pair<Pos, Integer>> entry : coches.entrySet()) {
        String id = entry.getKey();
        Pair<Pos, Integer> info = entry.getValue();
        int tks = info.getSecond();
        if (tks > 0) {
          coches.put(id, new Pair<>(info.getFirst(), tks - 1));
          if (tks - 1 == 0) {
            notificar = true;
          }
        }
      }

      if (notificar) {
        esperaCircular.signal(); // despertar a un coche que pueda avanzar
      }
    } finally {
      monitor.leave();
    }
  }

  private Set<Integer> carrilesLibres(int segmento) {
    Set<Integer> ocupados = new HashSet<>();
    for (Pair<Pos, Integer> info : coches.values()) {
      if (info.getFirst().getSegmento() == segmento) {
        ocupados.add(info.getFirst().getCarril());
      }
    }

    Set<Integer> libres = new HashSet<>();
    for (int c = 1; c <= CARRILES; c++) {
      if (!ocupados.contains(c))
        libres.add(c);
    }
    return libres;
  }

  private static class Pair<F, S> {
    private final F first;
    private final S second;

    public Pair(F first, S second) {
      this.first = first;
      this.second = second;
    }

    public F getFirst() {
      return first;
    }

    public S getSecond() {
      return second;
    }
  }
}
