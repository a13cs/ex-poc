package ch.algotrader.ema.strategy;

import ch.algotrader.ema.rest.model.BarModel;
import ch.algotrader.ema.utils.BarUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.ta4j.core.num.Num;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.findAndRegisterModules();
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        MAPPER.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);

        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        MAPPER.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true);
        MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private final ZoneId utc = ZoneId.of("UTC");

    @Value("${emaPeriodLong}")
    private Integer emaBarCountLong;

    @Value("${emaPeriodShort}")
    private Integer emaBarCountShort;

    @Value("${barDuration}")
    private Integer barDuration;

    @Value("${saveBarsToCsv}")
    private boolean saveToCsv;

    @Autowired
    private StrategyLogic strategyLogic;

    @Value("${skippedFirstBars}")
    private int skippedBars;


    public List<List<String>> getLatestCSVBars(String index, Path path) {
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

    public List<List<String>> getTrades(String fileNameSeconds) {
        String file;
        int startSec;
        try{
            startSec = Integer.parseInt(fileNameSeconds);
            file = "trades_start@s" + startSec + ".csv";
        } catch (NumberFormatException nfe) {
            file = StrategyLogic.TRADES_CSV;
//            file = "trades_start@s1625679938.csv";
        }
        Path path = Paths.get(file);
        try(BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path, READ))) {
            if(Files.exists(path)) {
                int size = bis.available();
                byte[] buffer = new byte[size];

                if (bis.read(buffer) > 0) {
                    BufferedReader bufferedReader =
                            new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));

                    return bufferedReader.lines()
                            .skip(1) // header
                            .map(line -> Arrays.asList(line.split(",")))
                            .collect(Collectors.toList());
                }
            }
        } catch (NoSuchFileException e) {
            logger.info("File {} has not been created yet.", path);
        } catch (IOException e) {
            logger.warn("IOException", e);
        }
        return Collections.emptyList();

    }

    public List<List<String>> getIndicator(String indicatorName, String from, Path path) {
        List<List<String>> indicatorValues = new ArrayList<>();

        Integer emaCount = indicatorName.contains("long") ? emaBarCountLong : emaBarCountShort;

        BaseBarSeries series = getCsvSeries(indicatorName, from, path);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(close, emaCount);

        List<Bar> barData = series.getBarData();
        for(int i = 1; i < barData.size() ; i++) {
            Bar b = barData.get(i);

            Instant micro = b.getEndTime().toInstant().truncatedTo(ChronoUnit.MICROS);
            String beginTime = String.valueOf(micro.getEpochSecond());

            Num value = close.getValue(i);
                try {
                    value = ema.getValue(i);
                } catch (Exception e) {
                    logger.debug("EMAIndicator error at index " + i, e);
                }

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

        //  todo
        EMAIndicator ema = indicatorName.equals("long") ? strategyLogic.lema : strategyLogic.sema;
        ClosePriceIndicator close = new ClosePriceIndicator(ema.getBarSeries());

        for(int i = 1; i < ema.getBarSeries().getBarCount() ; i++) {
            Num value = close.getValue(i);
            try {
                value = ema.getValue(i);
            } catch (Exception e) {
                logger.debug("EMAIndicator error at index " + i, e);
            }

            double val = value.doubleValue();
            long truncated = (long) val;
            long micros = Math.round((val - truncated) * 1_000_000);
            String emaValue  = truncated + "." + micros;

            indicatorValues.add(emaValue);
        }
        return indicatorValues;
    }

    public List<List<String>> getTradesSeries(String seriesName) {
        final Duration duration = Duration.ofSeconds(barDuration);
        final BaseBarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
        List<List<String>> csvTrades = getTrades(""); // p,q,T

        long startTime = Long.parseLong(csvTrades.get(0).get(2)) ;

        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), utc);
        long clean = dateTime.toEpochSecond()/100;
        logger.info("Initial start ZonedDateTime {} from timestamp {}", dateTime, startTime);
        dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clean*100_000), utc);

        long firstBarEndTimeSeconds = dateTime.plusSeconds(barDuration).toEpochSecond();
        ZonedDateTime firstBarEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(firstBarEndTimeSeconds * 100 /*todo*/), utc);
        logger.info("firstBarEndTimeSeconds * 100 {}", firstBarEndTime);

        Bar firstBar = new BaseBar(duration, firstBarEndTime, series.function());
        series.addBar(firstBar);

        startTime = dateTime.toEpochSecond()*1_000;
        logger.info("start startTime {}", startTime);

        for (List<String> trade : csvTrades) {
            {
                double amount = Math.abs(Double.parseDouble(trade.get(1)));
                double price = Math.abs(Double.parseDouble(trade.get(0)));

                // TODO: filter trades
                if (amount < 0.005 || price > 80_000 || price < 20_000) continue;

                if (price > 0) {
                    int index = series.getEndIndex();
                    series.addTrade(amount, price);

                    if (index >= 0) {
                        long seriesBarCount = series.getBarCount() > 0 ? series.getBarCount() : 1;
                        long nextBarTime = dateTime.plusSeconds((long) barDuration * seriesBarCount).toEpochSecond() * 1_000;
                        long currentTradeTime = (long) Double.parseDouble(trade.get(2));

                        logger.info("startTime {} nextBarTime {} currentTradeTime {}", startTime, nextBarTime, currentTradeTime);
                        ZonedDateTime startDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), utc);
                        ZonedDateTime nextDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextBarTime), utc);
                        ZonedDateTime currentDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTradeTime), utc);
//                        logger.info("startTime {} nextBarTime {} currentTradeTime {}", startDateTime, nextDateTime, currentDateTime);

                        if (currentTradeTime / 1_000 >= nextBarTime / 1_000) {
                            logger.info("currentTradeTime >= nextBarTime  {} ms", currentTradeTime - nextBarTime);

                            ZonedDateTime barEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli((nextBarTime)), utc);
                            Bar newBar = new BaseBar(duration, barEndTime, series.function());

                            // set price to closing price of previous bar
                            Bar previousBar = series.getBar(index);
                            newBar.addPrice(previousBar.getClosePrice());

                            // may save trade quantities around main price levels, per bar.
                            try {
                                series.addBar(newBar);
                            } catch (Exception e) {
//                                series.addBar(newBar, true);  // replace failed
                            }
                        }
                    }
                }
            }
        }
//        logger.info("Series bar data: ");
//        series.getBarData().forEach(b -> logger.info(b.toString()));

        String fileName = "bnc_trades_" + barDuration + "s.csv";

        return series.getBarData().stream()
                .skip(skippedBars)  // first bar is x10 the duration.
                .map(SeriesService::serializeBar)
                .peek(b -> { if(saveToCsv) BarUtils.writeValuesToFile(fileName, b); } )
                .collect(Collectors.toList());
    }

    private static ArrayList<String> serializeBar(Bar bar) {
        Map<String, String> barFields = new HashMap<>();
        BarModel barModel = BarModel.fromBar(bar);
        try {
            barFields = MAPPER.readValue(
                    MAPPER.writeValueAsString(barModel),
                    new TypeReference<LinkedHashMap<String, String>>() {
                    });
        } catch (JsonProcessingException e) {
            logger.error("Bars from trades serialization exception. ", e);
        }

        return new ArrayList<>(barFields.values());
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
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(inst, utc);

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
                    // Cannot add bar with end time <= previous bar end time
//                    logger.error("Recreating series from CSV", e);
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
