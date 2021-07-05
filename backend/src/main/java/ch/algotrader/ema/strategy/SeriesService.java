package ch.algotrader.ema.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;

@Service
public class SeriesService {

    private static final Logger logger = LoggerFactory.getLogger(SeriesService.class);

    @Value("${emaPeriodLong}")
    private Integer emaBarCountLong;

    @Value("${emaPeriodShort}")
    private Integer emaBarCountShort;

    @Value("${barDuration}")
    private Integer barDuration;

    @Autowired
    private StrategyLogic strategyLogic;

    public List<List<String>> getLatestCSVBars(String index, Path path) /*throws IOException*/ {
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
            logger.info("File {} has not been created yet.", path);
            logger.warn("NoSuchFileException", fileException);
        } catch (IOException e) {
            logger.warn("IOException", e);
        }

        return Collections.emptyList();
    }

    public List<List<String>> getIndicator(String indicatorName, String from, Path path) {
        List<List<String>> indicatorValues = new ArrayList<>();

        Integer emaCount = indicatorName.toLowerCase(Locale.ROOT).contains("long") ?
                emaBarCountLong : emaBarCountShort;

        //  or use series from subscribed trades channel: strategyLogic.series
        BaseBarSeries series = getCsvSeries(indicatorName, from, path);
//        BarSeries series = strategyLogic.series;
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(close, emaCount);

        List<Bar> barData = series.getBarData();
        for(int i = 1; i < barData.size() ; i++) {
            Bar b = barData.get(i);

            Instant micro = b.getEndTime().toInstant().truncatedTo(ChronoUnit.MICROS);
            String beginTime = String.valueOf(micro.getEpochSecond());

            Num value = close.getValue(i);
//            if(i > emaCount/2) {
                try {
                    value = ema.getValue(i);
                } catch (Exception e) {
                    logger.warn("EMAIndicator error at index " + i, e);
                }
//            }

            double val = value.doubleValue();
            long truncated = (long) val;
            long micros = Math.round((val - truncated) * 1_000_000);
            String emaValue  = truncated + "." + micros;

            indicatorValues.add(Arrays.asList(beginTime, emaValue));
        }
        return indicatorValues;
    }

    public List<String> getRuntimeIndicator(String indicatorName) {
        List<String> indicatorValues = new ArrayList<>();

//        Integer emaCount = indicatorName.toLowerCase(Locale.ROOT).contains("long") ?
//                emaBarCountLong : emaBarCountShort;

        BarSeries series = strategyLogic.series;
        EMAIndicator ema = indicatorName.toLowerCase(Locale.ROOT).contains("long") ? strategyLogic.lema : strategyLogic.sema;
        ClosePriceIndicator close = new ClosePriceIndicator(series);
//        EMAIndicator ema = new EMAIndicator(close, emaCount);

        List<Bar> barData = series.getBarData();
        for(int i = 1; i < barData.size() ; i++) {
            Num value = close.getValue(i);
            try {
                value = ema.getValue(i);
            } catch (Exception e) {
                logger.warn("EMAIndicator error at index " + i, e);
            }

            double val = value.doubleValue();
            long truncated = (long) val;
            long micros = Math.round((val - truncated) * 1_000_000);
            String emaValue  = truncated + "." + micros;

            indicatorValues.add(emaValue);
        }
        return indicatorValues;
    }

    public BaseBarSeries getCsvSeries(String indicatorName, String from, Path path) {
        List<List<String>> bars = getLatestCSVBars(from, path);
        if (!bars.isEmpty()) bars.remove(0); // header

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(indicatorName + "_series").build();

        bars.forEach(b -> {
            // closePrice
            double price = Math.abs(Double.parseDouble(b.get(5)));
            if (price > 0) {
                logger.debug("Price at {}: {}",b.get(1), price);

                // endTime
                BigDecimal decimalSeconds = new BigDecimal(b.get(1));  // 1622064025.004043001
                long seconds = decimalSeconds.longValue();
                long nanos = decimalSeconds.subtract(BigDecimal.valueOf(seconds))
                        .movePointRight(9)
                        .longValueExact();
                Instant inst = Instant.ofEpochSecond(seconds, nanos);
                logger.debug("Nanos for price {} {}",price, inst);  // 2021-05-26T21:20:25.004043001Z
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(inst, ZoneId.of("UTC"));

                Bar bar = new BaseBar(
                        Duration.ofSeconds(barDuration),
                        dateTime,
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
                    logger.warn("Recreating series from CSV. Could not add bar (replacing last): {}", bar);
                    series.addBar(bar,true);
                }
            }
        });
        return series;
    }

    public List<List<String>> getSignals(String from) {
        return strategyLogic.getSignals()
                .stream()/*.filter(s -> Long.parseLong(s[0]) > Long.parseLong(from))*/
                .map(Arrays::asList).collect(Collectors.toList());
    }

    // todo: get latest bars from in memory series

    // todo: agg bars from csv by timeframe

}
