package ch.algotrader.ema.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;

@Service
public class SeriesService {

    @Autowired BarSeries series;

    @Value("${emaBarCount}")
    private Integer emaBarCount = 14;

    public List<List<String>> getLatestBars(String index, Path path) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path, READ));
        if(Files.exists(path)) {
            int size = bis.available();
            byte[] buffer = new byte[size];

            if (bis.read(buffer) > 0) {
                BufferedReader bufferedReader =
                        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));

                List<String> header = Arrays.asList(bufferedReader.readLine().split(","));
                List<List<String>> latestBars =
                        bufferedReader.lines()
                                .skip(Long.parseLong(index) - 1)
//                        .sorted(Comparator.reverseOrder())
                                .map(line -> Arrays.asList(line.split(","))).collect(Collectors.toList());

                latestBars.add(0, header);
                return latestBars;
            }
        }
        return Collections.emptyList();
    }

    public List<List<String>> getIndicator(String indicator, String index, Path path) throws IOException {
        List<List<String>> bars = getLatestBars(index, path);
        bars.remove(0);

        // beginTime, closePrice
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(close, emaBarCount);

        bars.forEach(b -> series.addPrice(b.get(0)));
        List<List<String>> indicatorValues = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            List<String> bar = bars.get(i);
            indicatorValues.add(Arrays.asList(bar.get(0), String.valueOf(ema.getValue(i).doubleValue())));
        }
        return indicatorValues;
    }
}
