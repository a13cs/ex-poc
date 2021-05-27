package ch.algotrader.ema.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;

@Service
public class SeriesService {

    private static final Logger logger = LoggerFactory.getLogger(SeriesService.class);

    @Value("${emaRestBarCount}")
    private Integer emaBarCount = 10;

    @Autowired
    private StrategyLogic strategyLogic;

    public List<List<String>> getLatestCSVBars(String index, Path path) throws IOException {
        long from = Long.parseLong(index);
        if (from <= 0 ) from = 1;

        try(BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path, READ))) {
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
        } catch (NoSuchFileException fileException) {
            logger.info("NoSuchFileException", fileException);
        }

        return Collections.emptyList();
    }

    public List<List<String>> getIndicator(String indicatorName, String from, Path path) throws IOException {
        List<List<String>> indicatorValues = new ArrayList<>();

        //  or use series from subscribed trades channel: strategyLogic.series
        BaseBarSeries series = getCsvSeries(indicatorName, from, path);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(close, emaBarCount);

        List<Bar> barData = series.getBarData();
        int i = emaBarCount;
        for(Bar b: barData) {
            if (i >= barData.size()-emaBarCount) break;
            long seconds = b.getBeginTime().toLocalDateTime().toEpochSecond(ZoneOffset.UTC);
            String beginTime = String.valueOf(seconds);
            String emaValue  = String.valueOf(ema.getValue(i++).doubleValue());

            indicatorValues.add(Arrays.asList(beginTime, emaValue));
        }
        return indicatorValues;
    }

    public BaseBarSeries getCsvSeries(String indicatorName, String from, Path path) throws IOException {
        List<List<String>> bars = getLatestCSVBars(from, path);
        if (!bars.isEmpty()) bars.remove(0); // header

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(indicatorName + "_series").build();

        bars.forEach(b -> {
            // closePrice
            double price = Math.abs(Double.parseDouble(b.get(5)));
            if (price > 0) {
//                logger.info("Price at {}: {}",b.get(1), price);
                // TODO: ceil
                String[] split = b.get(1).split("\\."); // 1622064025.004043000
                long barEndTime = Math.abs(Long.parseLong(split[0]));
                ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(barEndTime * 1000), ZoneId.of("UTC"));
                Bar bar = new BaseBar(
                        Duration.ofSeconds(5),
                        zonedDateTime,
                        b.get(3),  // open
                        b.get(5),  // high
                        b.get(6),  // low
                        b.get(4),  // close
                        b.get(8),  // volume
                        b.get(7)   // amount
                );
                try{
                    series.addBar(bar);
                } catch (Exception e) {
                    logger.info("Could not add bar: {}", bar);
                    series.addBar(bar,true);
                }
            }
        });
        return series;
    }

    public List<List<String>> getPositions(String from, Path path) {
        return Collections.emptyList();
    }
}
