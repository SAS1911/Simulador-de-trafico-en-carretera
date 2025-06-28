// Nunca cambia la declaracion del package!
package cc.carretera;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;import es.upm.aedlib.Pair;
import es.upm.babel.cclib.Monitor;
import es.upm.babel.cclib.Monitor.Cond;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
	private final int SEGMENTOS;
	private final int CARRILES;
	//Mapa que contiene los coches que estan circulando en la carretera
	private final Map<String, Pair<Pos, Integer>> coches;
	//Monitores
	private Monitor mutex;
	//Un condiional para cada segmento para organizar entrar y avanzar y poder comprobar facilmente si hay carriles libres por segmento
	private final Monitor.Cond condSegmentos[];
	//Un condicional para cada coche para poder comprobar los ticks y hacer el signal al coche que queremos que deje de circular
	private final Map<String, Monitor.Cond> condCoches;


	public CarreteraMonitor(int segmentos, int carriles) {
		//Inicializamos los atributos
		this.SEGMENTOS = segmentos;
		this.CARRILES = carriles;
		mutex = new Monitor();
		//Coches y ConCoches empiezan vacios y se les añaden elementos cuando un coche entra
		coches = new HashMap<>();
		condCoches = new HashMap<>();
		condSegmentos = new Monitor.Cond[SEGMENTOS];
		for(int i = 0; i < SEGMENTOS; i++) {
			condSegmentos[i]= mutex.newCond();
		}
	}

	public Pos entrar(String id, int tks) {
		//Comprobamos PRE
		if(coches.containsKey(id)) {
			throw new RuntimeException (new Exception ("El coche ya se encuentra en la carretera"));
		}
		mutex.enter();
		//Comprobamos CPRE si no se cumple hacemos await del primer segmento porque esta lleno
		if(carrilesLibres(1).isEmpty()) {
			condSegmentos[0].await();
		}
		//Nuevo carril al que va a entrar el coche
		int nuevoCarril = carrilesLibres(1).iterator().next();
		//Creamos la posicion y la añadimos al mapa de coches con su id para identificarlo
		Pos nuevaPosicion = new Pos(1,nuevoCarril);
		coches.put(id,new Pair<>(nuevaPosicion,tks));
		//Creamos el condicional del coche y lo añadimos al mapa de condicionales de coches
		Cond condicional = mutex.newCond();
		condCoches.put(id, condicional);
		//desbloqueo para los metodos que estan esperando un signal
		desbloqueo();
		mutex.leave();
		//Devolvemos la posicion (POST)
		return nuevaPosicion;
	}


	public Pos avanzar(String id, int tks) {
		//Las tres condiciones del PRE
		if(!coches.containsKey(id)){
			throw new RuntimeException (new Exception ("El coche no existe"));
		}
		int segmentoActual=coches.get(id).getLeft().getSegmento();
		if(segmentoActual>=SEGMENTOS){
			throw new RuntimeException (new Exception ("El coche no puede avanzar más"));
		}
		if(coches.get(id).getRight()!=0){
			throw new RuntimeException (new Exception ("El coche sigue circulando"));
		}
		mutex.enter();
		//Comprobamos CPRE y sino se cumple hacemos await
		if(carrilesLibres(segmentoActual+1).isEmpty()) {
			condSegmentos[segmentoActual].await();
		}
		//Nuevo carril al que va a avanzar
		int nuevoCarril = carrilesLibres(segmentoActual+1).iterator().next();
		//Creamos su nueva posicion y la añadimos al mapa de coches
		Pos nuevaPosicion = new Pos(segmentoActual+1,nuevoCarril);
		coches.put(id,new Pair<>(nuevaPosicion,tks));
		//desbloqueo de awaits
		desbloqueo();
		mutex.leave();
		//Devuelve la nueva posicion (POST)
		return nuevaPosicion;
	}

	public void circulando(String id) {
		//Comprobamos PRE
		if(!coches.containsKey(id)){
			throw new RuntimeException (new Exception ("El coche no existe"));
		}
		mutex.enter();
		//Comprobamos CPRE y si no se cumple await
		if(coches.get(id).getRight()!=0) {
			condCoches.get(id).await();
		}
		//desbloqueamos awaits
		desbloqueo();
		mutex.leave();
	}

	public void salir(String id) {
		//Comprobamos las tres condiciones del PRE
		if(!coches.containsKey(id)){
			throw new RuntimeException (new Exception ("El coche no existe"));
		}
		int segmentoActual=coches.get(id).getLeft().getSegmento();
		if(segmentoActual!=SEGMENTOS){
			throw new RuntimeException (new Exception ("El coche no puede salir"));
		}
		if(coches.get(id).getRight()!=0){
			throw new RuntimeException (new Exception ("El coche sigue circulando"));
		}
		//No tiene CPRE un coche siempre puede salir
		mutex.enter();
		//Lo eliminamos de ambos mapas
		coches.remove(id);
		condCoches.remove(id);
		//desbloqueamos
		desbloqueo();
		mutex.leave();
	}

	public void tick() {
		//No tiene PRE ni CPRE
		mutex.enter();
		//Vamos coche por coche cambiando sus ticks por uno menos siempre que sean mayores que 0
		for(Entry<String, Pair<Pos, Integer>> coche:coches.entrySet()) {
			int nuevosTicks = Math.max(coche.getValue().getRight()-1,0);
			coche.getValue().setRight(nuevosTicks);
		}
		//desbloqueamos
		desbloqueo();
		mutex.leave();
	}
	//Metodo auxiliar para ver los carriles libres en un segmento
	private Set<Integer> carrilesLibres(int segmento) {
		//creamos un set de enteros los carriles ocupados (cada entero hace referencia a un carril)
		Set<Integer> ocupados = new HashSet<>();
		//Vamos coche por coche
		for(Entry<String, Pair<Pos, Integer>> coche : coches.entrySet()) {
			//Si el segmento es el mismo que el pedido añadimos el numero del carril a ocupados
			if (coche.getValue().getLeft().getSegmento() == segmento) {
				ocupados.add(coche.getValue().getLeft().getCarril());
			}
		}
		//creamos set libres
		Set<Integer> libres = new HashSet<>();
		//comprobamos carril por carril que no este en ocupados
		for (int c = 1; c <= CARRILES; c++) {
			if (!ocupados.contains(c)) {
				//si no esta en ocupados lo añadimos a libres
				libres.add(c);
			}
		}
		//devolvemos los carriles libres
		return libres;
	}
	//Metodo auxiliar para desbloquear los awaits mas facil
	public void desbloqueo() {
		//booleano para solo hacer un signal por vez
		boolean desbloqueado=false;
		//recorremos segmento por segmento (sirve para entrar (condSegmentos[0]) y los demás para avanzar)
		//El ultimo no se comprueba porque no pueden avanzar desde ahi solo pueden salir y no tiene cpre
		for(int i = 1; i < SEGMENTOS && !desbloqueado; i++) {
			//Si el segmento tiene carriles libres lo desbloqueamos para que un coche pueda avanzar a ese segmento
			//como carriles libres empieza en 1 y condSegmentos en 0 ponemos i-1
			if(!carrilesLibres(i).isEmpty()&&condSegmentos[i-1].waiting()>0&&!desbloqueado) {
				//Desbloqueamos y ponemos desbloqueado en true para salir del bucle
				condSegmentos[i-1].signal();
				desbloqueado=true;
			}
		}
		//SALIR CUANDO ENCUENTRA? 
		//Vamos coche por coche viendo si sus ticks estan en 0 se usa para desbloquear circulando
		for(Entry<String, Pair<Pos, Integer>> coche : coches.entrySet()) {
			if(coche.getValue().getRight() == 0 && condCoches.get(coche.getKey()).waiting() > 0 && !desbloqueado) {
				//desbloqueamos y ponemos desbloqueado a true
				condCoches.get(coche.getKey()).signal();
				desbloqueado=true;
			}
		}
	}
}
