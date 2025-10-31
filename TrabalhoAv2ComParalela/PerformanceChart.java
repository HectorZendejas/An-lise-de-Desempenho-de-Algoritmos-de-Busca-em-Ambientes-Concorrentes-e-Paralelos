import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.*;
import java.util.*;

public class PerformanceChart {
    public static void main(String[] args) throws Exception {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        BufferedReader br = new BufferedReader(new FileReader("results.csv"));
        String line = br.readLine(); // pular header

        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            String algorithm = parts[0];
            String inputType = parts[3];
            int size = Integer.parseInt(parts[4]);
            long duration = Long.parseLong(parts[6]);

            dataset.addValue(duration / 1_000_000.0, algorithm + " - " + inputType, Integer.toString(size));
        }
        br.close();

        JFreeChart chart = ChartFactory.createLineChart(
                "Desempenho dos Algoritmos",
                "Tamanho do Array",
                "Tempo (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        ChartFrame frame = new ChartFrame("Gráfico de Desempenho", chart);
        frame.pack();
        frame.setVisible(true);
    }
}
