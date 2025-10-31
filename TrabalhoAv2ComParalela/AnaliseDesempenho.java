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

public class AnaliseDesempenho {

    // ========================= MÉTODO PRINCIPAL =========================
    public static void main(String[] args) throws IOException {
        int[] tamanhos = {10000, 50000, 100000, 200000};
        int[] threads = {1, 2, 4, 8};

        FileWriter csv = new FileWriter("resultados.csv");
        csv.write("Tipo,Tamanho,Threads,Tempo(ms)\n");

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (int n : tamanhos) {
            int[] base = gerarArray(n);

            // --- MergeSort Serial ---
            int[] copiaSerial = base.clone();
            long inicio = System.currentTimeMillis();
            MergeSortSerial.sort(copiaSerial);
            long tempoSerial = System.currentTimeMillis() - inicio;

            csv.write(String.format("Serial,%d,1,%d\n", n, tempoSerial));
            dataset.addValue(tempoSerial, "MergeSort Serial", String.valueOf(n));

            // --- MergeSort Paralelo ---
            for (int t : threads) {
                int[] copiaParalela = base.clone();
                inicio = System.currentTimeMillis();
                MergeSortParallel.sort(copiaParalela, t);
                long tempoParalelo = System.currentTimeMillis() - inicio;

                csv.write(String.format("Paralelo,%d,%d,%d\n", n, t, tempoParalelo));
                dataset.addValue(tempoParalelo, "MergeSort Paralelo (" + t + " threads)", String.valueOf(n));

                System.out.printf("Tamanho: %d | Threads: %d | Tempo: %d ms%n", n, t, tempoParalelo);
            }
            System.out.println("----------------------------------------------");
        }

        csv.close();
        gerarGrafico(dataset);
        System.out.println("\n✅ Análise concluída. Gráfico exibido e arquivo resultados.csv gerado!");
    }

    // ========================= GERAR GRÁFICO =========================
    private static void gerarGrafico(DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createLineChart(
                "Análise de Desempenho de Algoritmos de Busca em Ambientes Concorrentes e Paralelos",
                "Tamanho do Array",
                "Tempo (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        ChartFrame frame = new ChartFrame("Desempenho: Serial vs Paralelo", chart);
        frame.pack();
        frame.setVisible(true);
    }

    // ========================= GERAÇÃO DO ARRAY =========================
    private static int[] gerarArray(int n) {
        Random rand = new Random();
        int[] array = new int[n];
        for (int i = 0; i < n; i++) array[i] = rand.nextInt(100000);
        return array;
    }
}

// ========================= MERGE SORT SERIAL =========================
class MergeSortSerial {
    public static void sort(int[] array) {
        mergeSort(array, 0, array.length - 1);
    }

    private static void mergeSort(int[] array, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(array, left, mid);
            mergeSort(array, mid + 1, right);
            merge(array, left, mid, right);
        }
    }

    private static void merge(int[] array, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int i = left, j = mid + 1, k = 0;

        while (i <= mid && j <= right)
            temp[k++] = (array[i] <= array[j]) ? array[i++] : array[j++];

        while (i <= mid) temp[k++] = array[i++];
        while (j <= right) temp[k++] = array[j++];

        System.arraycopy(temp, 0, array, left, temp.length);
    }
}

// ========================= MERGE SORT PARALELO =========================
class MergeSortParallel {
    private static final int LIMITE = 10000;

    public static void sort(int[] array, int numThreads) {
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        pool.invoke(new MergeTask(array, 0, array.length - 1));
    }

    private static class MergeTask extends RecursiveAction {
        private int[] array;
        private int left, right;

        MergeTask(int[] array, int left, int right) {
            this.array = array;
            this.left = left;
            this.right = right;
        }

        @Override
        protected void compute() {
            if (right - left < LIMITE) {
                MergeSortSerial.sort(array);
                return;
            }
            int mid = (left + right) / 2;
            MergeTask leftTask = new MergeTask(array, left, mid);
            MergeTask rightTask = new MergeTask(array, mid + 1, right);
            invokeAll(leftTask, rightTask);
            merge(array, left, mid, right);
        }

        private void merge(int[] array, int left, int mid, int right) {
            int[] temp = new int[right - left + 1];
            int i = left, j = mid + 1, k = 0;

            while (i <= mid && j <= right)
                temp[k++] = (array[i] <= array[j]) ? array[i++] : array[j++];

            while (i <= mid) temp[k++] = array[i++];
            while (j <= right) temp[k++] = array[j++];

            System.arraycopy(temp, 0, array, left, temp.length);
        }
    }
}
