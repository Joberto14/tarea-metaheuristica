import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public class BT {
    public static void main(String[] args) throws IOException {

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

		
		Avion[] aviones;
		int n;
		
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
		
		// solucion es un arreglo, pos[0] -> tiempo de aterrizaje avion 1
		int[] solucion = new int[n];
		
		// heuristica de seleccion de variable 
		// se usa Dom -> la que tiene el dominio menor, es decir, Li - Ei < a las demas
		
		Arrays.sort(aviones, Comparator.comparingInt(Avion::getDom));
		
		// fin heuristica, aviones ordenados
		
		
		
		
		
		
		//System.out.println(aviones[0].i);



    }
}