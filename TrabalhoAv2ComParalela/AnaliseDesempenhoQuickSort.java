import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class AnaliseDesempenhoQuickSort {

    public static void main(String[] args) throws IOException {
        int[] tamanhos = {10000, 50000, 100000, 200000};
        int[] threads = {1, 2, 4, 8};

        FileWriter csv = new FileWriter("resultados_quicksort.csv");
        csv.write("Tipo,Tamanho,Threads,Tempo(ms)\n");

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (int n : tamanhos) {
            int[] base = gerarArray(n);

            // --- QuickSort Serial ---
            int[] copiaSerial = base.clone();
            long inicio = System.currentTimeMillis();
            QuickSortSerial.sort(copiaSerial, 0, copiaSerial.length - 1);
            long tempoSerial = System.currentTimeMillis() - inicio;

            csv.write(String.format("Serial,%d,1,%d\n", n, tempoSerial));
            dataset.addValue(tempoSerial, "QuickSort Serial", String.valueOf(n));

            // --- QuickSort Paralelo ---
            for (int t : threads) {
                int[] copiaParalela = base.clone();
                inicio = System.currentTimeMillis();
                QuickSortParallel.sort(copiaParalela, t);
                long tempoParalelo = System.currentTimeMillis() - inicio;

                csv.write(String.format("Paralelo,%d,%d,%d\n", n, t, tempoParalelo));
                dataset.addValue(tempoParalelo, "QuickSort Paralelo (" + t + " threads)", String.valueOf(n));

                System.out.printf("Tamanho: %d | Threads: %d | Tempo: %d ms%n", n, t, tempoParalelo);
            }
            System.out.println("----------------------------------------------");
        }

        csv.close();
        gerarGrafico(dataset);
        System.out.println("\n✅ Análise concluída. Gráfico exibido e arquivo resultados_quicksort.csv gerado!");
    }

    private static void gerarGrafico(DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createLineChart(
                "Desempenho: QuickSort Serial vs Paralelo",
                "Tamanho do Array",
                "Tempo (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        ChartFrame frame = new ChartFrame("Desempenho QuickSort", chart);
        frame.pack();
        frame.setVisible(true);
    }

    private static int[] gerarArray(int n) {
        Random rand = new Random();
        int[] array = new int[n];
        for (int i = 0; i < n; i++) array[i] = rand.nextInt(100000);
        return array;
    }
}

// ========================= QUICKSORT SERIAL =========================
class QuickSortSerial {
    public static void sort(int[] array, int low, int high) {
        if (low < high) {
            int pi = partition(array, low, high);
            sort(array, low, pi - 1);
            sort(array, pi + 1, high);
        }
    }

    private static int partition(int[] array, int low, int high) {
        int pivot = array[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (array[j] <= pivot) {
                i++;
                swap(array, i, j);
            }
        }
        swap(array, i + 1, high);
        return i + 1;
    }

    private static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }
}

// ========================= QUICKSORT PARALELO =========================
class QuickSortParallel {
    private static final int LIMITE = 10000;

    public static void sort(int[] array, int numThreads) {
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        pool.invoke(new QuickTask(array, 0, array.length - 1));
    }

    private static class QuickTask extends RecursiveAction {
        private int[] array;
        private int low, high;

        QuickTask(int[] array, int low, int high) {
            this.array = array;
            this.low = low;
            this.high = high;
        }

        @Override
        protected void compute() {
            if (high - low < LIMITE) {
                QuickSortSerial.sort(array, low, high);
                return;
            }
            int pi = partition(array, low, high);
            invokeAll(new QuickTask(array, low, pi - 1), new QuickTask(array, pi + 1, high));
        }

        private int partition(int[] array, int low, int high) {
            int pivot = array[high];
            int i = low - 1;
            for (int j = low; j < high; j++) {
                if (array[j] <= pivot) {
                    i++;
                    swap(array, i, j);
                }
            }
            swap(array, i + 1, high);
            return i + 1;
        }

        private void swap(int[] array, int i, int j) {
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
}
