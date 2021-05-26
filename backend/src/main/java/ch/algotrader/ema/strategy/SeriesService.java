package ch.algotrader.ema.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;

@Service
public class SeriesService {

    private static final Logger logger = LoggerFactory.getLogger(SeriesService.class);

    @Value("${emaBarCount}")
    private Integer emaBarCount = 10;

    @Autowired
    private StrategyLogic strategyLogic;

    public List<List<String>> getLatestBars(String index, Path path) throws IOException {
        long from = Long.parseLong(index);
        if (from <= 0 ) from = 1;

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
                                .skip(from - 1)
//                        .sorted(Comparator.reverseOrder())
                                .map(line -> Arrays.asList(line.split(","))).collect(Collectors.toList());

                latestBars.add(0, header);
                return latestBars;
            }
        }
        return Collections.emptyList();
    }

    public List<List<String>> getIndicator(String indicator, String index, Path path) throws IOException {
        long from = Long.parseLong(index);
        if (from <= 0 ) from = 1;

        List<List<String>> bars = getLatestBars(index, path);
        bars.remove(0);

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(indicator + "_series").build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(close, emaBarCount);

        bars.forEach(b -> {
            // closePrice
            double price = Math.abs(Double.parseDouble(b.get(5)));
            if (price > 0) {
//                logger.info("ema price at {}: {}",b.get(1), price);
                String[] split = b.get(1).split("\\.");  // 1622064025.004043000
                long begin = Math.abs(Long.parseLong(split[0]));
                ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), ZoneId.of("UTC"));
                series.addBar(
                        zonedDateTime,
                        b.get(3),  // open
                        b.get(5),  // high
                        b.get(6),  // low
                        b.get(4),  // close
                        b.get(8),
                        b.get(7)
                );
            }
        });
        List<List<String>> indicatorValues = new ArrayList<>();

//        ClosePriceIndicator close = new ClosePriceIndicator(strategyLogic.series);
//        EMAIndicator ema = new EMAIndicator(close, emaBarCount);
//        List<Bar> barData = strategyLogic.series.getBarData();
        List<Bar> barData = series.getBarData();
        int i = emaBarCount;
        for(Bar b: barData) {
            if (i >= barData.size()-emaBarCount) break;
            String beginTime = String.valueOf( b.getBeginTime().toLocalDateTime().toEpochSecond(ZoneOffset.UTC) );
            String emaValue  = String.valueOf(ema.getValue(i++).doubleValue());

            indicatorValues.add(Arrays.asList(beginTime, emaValue));
        }
        return indicatorValues;
    }
}