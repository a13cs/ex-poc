package ch.algotrader.ema.strategy;

import ch.algotrader.ema.rest.model.BarModel;
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
    }

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

    public List<List<String>> getTrades(String fileNameSeconds) {
        int startSec;
        String file;
        try{
            startSec = Integer.parseInt(fileNameSeconds);
            file = "trades_start@s" + startSec + ".csv";
        } catch (NumberFormatException nfe) {
            file = StrategyLogic.TRADES_CSV;
        }
        Path path = Paths.get(file);
        try(BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path, READ))) {
            if(Files.exists(path)) {
                int size = bis.available();
                byte[] buffer = new byte[size];

                if (bis.read(buffer) > 0) {
                    BufferedReader bufferedReader =
                            new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));

                    List<String> header = Arrays.asList(bufferedReader.readLine().split(","));
                    List<List<String>> trades =
                            bufferedReader.lines()
                                    .map(line -> Arrays.asList(line.split(","))).collect(Collectors.toList());

//                    trades.add(0, header);


                    return trades;
                }
            }
        } catch (Exception e) {
            logger.error("ex ", e);
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
                    logger.debug("EMAIndicator error at index " + i, e);
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

        EMAIndicator ema = indicatorName.toLowerCase(Locale.ROOT).equals("long") ? strategyLogic.lema : strategyLogic.sema;
        ClosePriceIndicator close = new ClosePriceIndicator(ema.getBarSeries());

        for(int i = 1; i < ema.getBarSeries().getBarCount() ; i++) {
            Num value = close.getValue(i);
            if(i > emaBarCountLong) {
                try {
                    value = ema.getValue(i);
                } catch (Exception e) {
                    logger.debug("EMAIndicator error at index " + i, e);
                }
            }

            double val = value.doubleValue();
            long truncated = (long) val;
            long micros = Math.round((val - truncated) * 1_000_000);
            String emaValue  = truncated + "." + micros;

            indicatorValues.add(emaValue);
        }
        return indicatorValues;
    }

    public List<List<String>> getTradesSeries(String name) {
        List<List<String>> barsCSVTrades = getTrades(""); // p,q,T
        final BaseBarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Bar firstBar = new BaseBar(Duration.ofSeconds(barDuration), ZonedDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(barsCSVTrades.get(0).get(2)) + barDuration*1000), ZoneId.of("UTC")), series.function());
        series.addBar(firstBar);


        long startTime = Long.parseLong(barsCSVTrades.get(0).get(2)) ;
        for(int i = 0; i < barsCSVTrades.size(); i++) {
//        barsCSVTrades.forEach( trade ->
            {
                // dup
//                logger.info(Arrays.toString(barsCSVTrades.get(i).toArray(new String[0])));

                double amount = Math.abs(Double.parseDouble(barsCSVTrades.get(i).get(1)));
                double price = Math.abs(Double.parseDouble(barsCSVTrades.get(i).get(0)));

//            if (amount < 0.003 && price > 100_000 && price < 10_000) return;

                if (price > 0) {
                    int ind = series.getEndIndex();
                    logger.info("series.getEndIndex {}", ind);
                    series.addTrade(amount, price);

                    if (ind >= 0) {
//                        long startTime = series.getBar(ind).getBeginTime().toEpochSecond();
                        int seriesBarCount = series.getBarCount() > 0 ? series.getBarCount() : 1;
                        long nextBarTime = startTime + (long) barDuration * 1_000 * seriesBarCount;
                        long currentTradeTime = (long) Double.parseDouble(barsCSVTrades.get(i).get(2));
                        logger.info("startTime {} nextBarTime {} currentTradeTime {}", startTime, nextBarTime, currentTradeTime);
                        if (currentTradeTime/1_000 >= nextBarTime/1_000) {
                            logger.info("currentTradeTime >= nextBarTime  {}", currentTradeTime - nextBarTime);

                            ZonedDateTime now = ZonedDateTime.now();
                            ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(currentTradeTime + barDuration*1000), ZoneId.of("UTC"));
                            Bar newBar = new BaseBar(Duration.ofSeconds(barDuration), time, series.function());

                            // set price to closing price of previous bar
//                        int i = series.getEndIndex();
//                        if (i >= 0) {
                            Bar previousBar = series.getBar(ind);
                            newBar.addPrice(previousBar.getClosePrice());
//                        }

                            try{
                                series.addBar(newBar);
                            } catch (Exception e) {
                                series.addBar(newBar, true);
                            }
                        }
                    }
                }
            }
        }
//        series.getBarData().forEach(Object::toString);

        // todo: cache/save to file
        return series.getBarData().stream().map(bar ->
        {
            BarModel barModel = BarModel.fromBar(bar);

            Map<String, String> barFields = new HashMap<>();
            try {
                barFields = MAPPER.readValue(
                                MAPPER.writeValueAsString(barModel),
                                new TypeReference<LinkedHashMap<String, String>>() {
                            });
            } catch (JsonProcessingException e) {
                logger.error("Bars from trades ex. ", e);
            }
            return new ArrayList<>(barFields.values());
        }).collect(Collectors.toList());
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
