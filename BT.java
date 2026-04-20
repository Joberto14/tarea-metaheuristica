import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import java.util.Comparator;

// clase avion para guardar datos
class Avion {
	int Ei, Pi, Li, dom, i;
	double Ci, Ck;
	int[] tau;

	Avion(int n) {
		tau = new int[n];
	}

	public int getDom(){
		return dom;
	}
	public void setDom(){
		dom = Li - Ei;
	} 
}
public class BT {
	static double mejorCosto = Double.MAX_VALUE;

	static long tiempoDeSolucionEncontrada;
	static int solucionesEncontradas = 0;
	static int nodos = 0;
	static int n;
	static Avion[] aviones;
	static long inicio;
    public static void main(String[] args) throws IOException {
		
		// lectura de standar input
        try (BufferedReader br = Files.newBufferedReader(Path.of("case1.txt"))) {
			
			StreamTokenizer st = new StreamTokenizer(br);
            st.nextToken();
			// D aviones
			n = (int) st.nval;
			// arreglo de aviones
			aviones = new Avion[n];
			
			for (int i = 0; i < n; i++) {
				aviones[i] = new Avion(n);
				aviones[i].i = i;
				st.nextToken(); aviones[i].Ei  = (int) st.nval;
				st.nextToken(); aviones[i].Pi  = (int) st.nval;
				st.nextToken(); aviones[i].Li  = (int) st.nval;
				aviones[i].setDom();
				st.nextToken(); aviones[i].Ci  = st.nval;
				st.nextToken(); aviones[i].Ck  = st.nval;
				
				// Tiempos de separación con cada avión j
				for (int j = 0; j < n; j++) {
					st.nextToken();
					aviones[i].tau[j] = (int) st.nval;
				}
			}
        }
		// fin lectura standar input
		
		
		// heuristica de seleccion de variable 
		// se usa Dom -> la que tiene el dominio menor, es decir, Li - Ei < a las demas
		
		Arrays.sort(aviones, Comparator.comparingInt(Avion::getDom));
		
		// fin heuristica, aviones ordenados

		//System.out.println(aviones[0].i);
		ArrayList<ArrayList<Integer>> Soluciones = new ArrayList<>();
		ArrayList<Integer> solparcial= new ArrayList<>();

		
		
		//tiempo inicio
		inicio = System.nanoTime();
		long inicioEjecucion = System.nanoTime();

		// ------------------------------------------- hasta acamisma implementacion para FC pero cambiar las funciones ---------------------------------
		
		// algoritmo principal
		asignarTiempoAterrizaje(0,Soluciones, solparcial, aviones);

		// tiempo fin
		long fin = System.nanoTime();
		long tiempo = fin - inicioEjecucion;

		System.out.println("cantidad de nodos creados:");
		System.out.println(nodos);
		System.out.println("Tiempo de Ejecucion:");
		System.out.println(tiempo);
		

    }

	public static void asignarTiempoAterrizaje(int index, ArrayList<ArrayList<Integer>> Soluciones, ArrayList<Integer> actual, Avion[] listaAviones){
		nodos = nodos + 1;
		//System.out.println(nodos);
		
		// caso base
		if(n == index){
			
			double costoDeLaSolucion = calcularCosto(actual);
			// solo metemos la solucion si mejora el costo actual para no llenar el heap
			if(costoDeLaSolucion < mejorCosto){
				mejorCosto = costoDeLaSolucion;
				
				Soluciones.add(new ArrayList<>(actual));
				double segundos = (System.nanoTime() - inicio) / 1_000_000.0;
	
				System.out.printf("Solución encontrada en %.4f milisegundos%n", segundos);
				return;
			}
			return;
		}
		
		Avion curr = listaAviones[index]; 
		
		// debug
		//System.out.println("entre a asignarTiempoAterrizaje");
		//isValid(index, 2, listaAviones);
		
		for (int t = curr.Ei; t <= curr.Li; t++) {
			if (isValid(index, actual, t ,listaAviones)) {
				actual.add(t); // elegir
            	asignarTiempoAterrizaje(index + 1, Soluciones, actual, listaAviones); 
				actual.remove(actual.size() - 1);
            }
		}
	}

	static boolean isValid(int idx, ArrayList<Integer> actual, int tiempoAterrizaje, Avion[] listaAviones) {
		//System.out.println("entre a isValid y mi idx es:");
		//System.out.println(idx);

        for (int j = 0; j < idx; j++) {
			int tj = actual.get(j);
			Avion prev = listaAviones[j];   
			Avion curr = listaAviones[idx]; 

			if (tiempoAterrizaje >= tj) {
				
				if (tiempoAterrizaje - tj < prev.tau[curr.i]) return false; 
			} else {
				
				if (tj - tiempoAterrizaje < curr.tau[prev.i]) return false;
			}
		}
    	return true;
	}

	static double calcularCosto(ArrayList<Integer> solAEvaluar){
		double costo = 0;
		for (int k = 0; k < n; k++) {
			int t = solAEvaluar.get(k);   // tiempo asignado al avión k
			Avion a = aviones[k];

			costo = Math.max(a.Ci * (a.Pi - t), 0) + Math.max(a.Ck * (t - a.Pi), 0);
		}
		return costo;
	}
}