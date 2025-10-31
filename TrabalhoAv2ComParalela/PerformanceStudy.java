import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * PerformanceStudy.java
 *
 * Projeto pronto para rodar: mede desempenho de quatro algoritmos de ordenação
 * (Bubble, Insertion, Quick, Merge) em versões sequencial e "paralela por chunk".
 *
 * Produz um arquivo CSV "results.csv" com colunas:
 * algorithm,mode,threads,inputType,inputSize,sample,durationNs,timestamp
 *
 * Compile: javac PerformanceStudy.java
 * Rode:     java PerformanceStudy
 */
public class PerformanceStudy {
    static final int SAMPLES = 5;
    static final long RANDOM_SEED = 42L;
    static final String OUTPUT_CSV = "results.csv";

    static final int[] THREAD_COUNTS_TO_TEST = {1, 2, 4, 8};
    static final int[] SIZES_SMALL = {1000, 5000, 10000};
    static final int[] SIZES_LARGE = {10000, 50000, 100000, 200000};
    static final String[] INPUT_TYPES = {"RANDOM", "SORTED", "REVERSE", "NEARLY_SORTED"};

    enum Alg { BUBBLE, INSERTION, QUICK, MERGE }

    public static void main(String[] args) throws Exception {
        System.out.println("Iniciando estudo de desempenho - " + new Date());
        Random rng = new Random(RANDOM_SEED);

        try (PrintWriter csv = new PrintWriter(new FileWriter(OUTPUT_CSV))) {
            csv.println("algorithm,mode,threads,inputType,inputSize,sample,durationNs,timestamp");

            for (Alg alg : Alg.values()) {
                System.out.println("\n=================================================================");
                System.out.println("Algoritmo: " + alg);

                int[] sizes = (alg == Alg.BUBBLE || alg == Alg.INSERTION) ? SIZES_SMALL : SIZES_LARGE;

                for (int size : sizes) {
                    for (String inputType : INPUT_TYPES) {
                        for (int threads : THREAD_COUNTS_TO_TEST) {

                            boolean isParallel = threads > 1;
                            String mode = isParallel ? "PARALLEL" : "SEQUENTIAL";

                            for (int s = 1; s <= SAMPLES; s++) {
                                int[] base = generateArray(size, inputType, rng);
                                int[] arr = Arrays.copyOf(base, base.length);

                                long start = System.nanoTime();
                                if (!isParallel) {
                                    runSequentialSort(arr, alg);
                                } else {
                                    arr = runParallelChunkSort(base, alg, threads);
                                }
                                long end = System.nanoTime();
                                long dur = end - start;

                                if (!isSorted(arr)) {
                                    System.err.println("ERRO: array não ordenado! alg=" + alg + " mode=" + mode + " size=" + size);
                                }

                                String timestamp = String.valueOf(System.currentTimeMillis());
                                csv.printf("%s,%s,%d,%s,%d,%d,%d,%s\n",
                                        alg.name(), mode, threads, inputType, size, s, dur, timestamp);
                                csv.flush();

                                System.out.printf("alg=%s mode=%s threads=%d size=%d type=%s sample=%d time=%.3f ms\n",
                                        alg, mode, threads, size, inputType, s, dur / 1e6);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("\nEstudo completo. Arquivo gerado: " + OUTPUT_CSV);
        System.out.println("Fim - " + new Date());
    }

    /* ----------------------- Geração de arrays ----------------------- */

    static int[] generateArray(int size, String type, Random rng) {
        int[] a = new int[size];
        for (int i = 0; i < size; i++) a[i] = rng.nextInt(size * 10 + 1);

        switch (type) {
            case "SORTED" -> Arrays.sort(a);
            case "REVERSE" -> { Arrays.sort(a); reverseInPlace(a); }
            case "NEARLY_SORTED" -> {
                Arrays.sort(a);
                int swaps = Math.max(1, size / 100);
                for (int i = 0; i < swaps; i++) {
                    int x = rng.nextInt(size);
                    int y = rng.nextInt(size);
                    int t = a[x]; a[x] = a[y]; a[y] = t;
                }
            }
        }
        return a;
    }

    static void reverseInPlace(int[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) if (a[i-1] > a[i]) return false;
        return true;
    }

    /* ----------------------- Execucao sequencial ----------------------- */

    static void runSequentialSort(int[] a, Alg alg) {
        switch (alg) {
            case BUBBLE -> bubbleSort(a);
            case INSERTION -> insertionSort(a);
            case QUICK -> quickSort(a, 0, a.length - 1);
            case MERGE -> mergeSort(a, 0, a.length);
        }
    }

    /* ----------------------- Execucao paralela ----------------------- */

    static int[] runParallelChunkSort(int[] base, Alg alg, int threads) throws Exception {
        if (threads <= 1) {
            int[] arr = Arrays.copyOf(base, base.length);
            runSequentialSort(arr, alg);
            return arr;
        }

        int n = base.length;
        int chunks = Math.min(threads, n);
        int chunkSize = (n + chunks - 1) / chunks;

        ExecutorService ex = Executors.newFixedThreadPool(chunks);
        List<Future<int[]>> futures = new ArrayList<>();

        for (int i = 0; i < chunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(n, start + chunkSize);
            if (start >= end) break;
            int[] sub = Arrays.copyOfRange(base, start, end);
            futures.add(ex.submit(() -> { runSequentialSort(sub, alg); return sub; }));
        }

        List<int[]> segments = new ArrayList<>();
        for (Future<int[]> f : futures) segments.add(f.get());
        ex.shutdown();

        return kWayMerge(segments);
    }

    static int[] kWayMerge(List<int[]> segments) {
        if (segments.isEmpty()) return new int[0];
        if (segments.size() == 1) return segments.get(0);

        int total = segments.stream().mapToInt(s -> s.length).sum();
        int[] out = new int[total];

        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.value));
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).length > 0)
                pq.add(new Node(segments.get(i)[0], i, 0));
        }

        int idx = 0;
        while (!pq.isEmpty()) {
            Node cur = pq.poll();
            out[idx++] = cur.value;
            int next = cur.pos + 1;
            if (next < segments.get(cur.seg).length)
                pq.add(new Node(segments.get(cur.seg)[next], cur.seg, next));
        }
        return out;
    }

    static class Node {
        int value, seg, pos;
        Node(int v, int s, int p) { value = v; seg = s; pos = p; }
    }

    /* ----------------------- Bubble Sort ----------------------- */
    static void bubbleSort(int[] a) {
        int n = a.length;
        boolean swapped;
        for (int i = 0; i < n - 1; i++) {
            swapped = false;
            for (int j = 0; j < n - 1 - i; j++) {
                if (a[j] > a[j+1]) {
                    int t = a[j]; a[j] = a[j+1]; a[j+1] = t;
                    swapped = true;
                }
            }
            if (!swapped) break;
        }
    }

    /* ----------------------- Insertion Sort ----------------------- */
    static void insertionSort(int[] a) {
        int n = a.length;
        for (int i = 1; i < n; i++) {
            int key = a[i], j = i - 1;
            while (j >= 0 && a[j] > key) { a[j+1] = a[j]; j--; }
            a[j+1] = key;
        }
    }

    static void insertionSortRange(int[] a, int lo, int hi) {
        for (int i = lo+1; i <= hi; i++) {
            int key = a[i], j = i - 1;
            while (j >= lo && a[j] > key) { a[j+1] = a[j]; j--; }
            a[j+1] = key;
        }
    }

    /* ----------------------- QuickSort corrigido ----------------------- */

    static final int INSERTION_CUTOFF = 16;

    static void quickSort(int[] a, int lo, int hi) {
        while (lo < hi) {

            if (hi - lo + 1 <= INSERTION_CUTOFF) {
                insertionSortRange(a, lo, hi);
                return;
            }

            int pivotIndex = medianOfThree(a, lo, hi);
            swap(a, pivotIndex, hi);

            int p = partition(a, lo, hi);

            if (p - 1 - lo < hi - (p + 1)) {
                quickSort(a, lo, p - 1);
                lo = p + 1;
            } else {
                quickSort(a, p + 1, hi);
                hi = p - 1;
            }
        }
    }

    static int partition(int[] a, int lo, int hi) {
        int pivot = a[hi];
        int i = lo - 1;
        for (int j = lo; j < hi; j++) {
            if (a[j] <= pivot) { i++; swap(a, i, j); }
        }
        swap(a, i + 1, hi);
        return i + 1;
    }

    static int medianOfThree(int[] a, int lo, int hi) {
        int mid = lo + ((hi - lo) >> 1);
        int x = a[lo], y = a[mid], z = a[hi];
        if (x <= y) {
            if (y <= z) return mid;
            else if (x <= z) return hi;
            else return lo;
        } else {
            if (x <= z) return lo;
            else if (y <= z) return hi;
            else return mid;
        }
    }

    static void swap(int[] a, int i, int j) {
        int t = a[i]; a[i] = a[j]; a[j] = t;
    }

    /* ----------------------- Merge Sort ----------------------- */

    static void mergeSort(int[] a, int left, int right) {
        int len = right - left;
        if (len <= 1) return;
        int mid = left + len/2;
        mergeSort(a, left, mid);
        mergeSort(a, mid, right);
        int[] tmp = new int[len];
        int i = left, j = mid, k = 0;
        while (i < mid && j < right) {
            tmp[k++] = (a[i] <= a[j]) ? a[i++] : a[j++];
        }
        while (i < mid) tmp[k++] = a[i++];
        while (j < right) tmp[k++] = a[j++];
        System.arraycopy(tmp, 0, a, left, len);
    }
}
