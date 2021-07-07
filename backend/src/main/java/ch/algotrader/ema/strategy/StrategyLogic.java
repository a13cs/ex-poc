package ch.algotrader.ema.strategy;

import ch.algotrader.ema.rest.model.BarModel;
import ch.algotrader.ema.services.AccService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

@Service
public class StrategyLogic implements InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(StrategyLogic.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public static final String TRADES_CSV = "trades_start@s" + Instant.now().getEpochSecond() + ".csv";

    @Value("${barDuration}")
    private int barDuration;

    @Value("${saveToCsv}")
    private boolean saveToCsv;

    @Value("${offline}")
    private boolean offline;

    @Value("${symbol}") private String symbol;
    @Value("${quantity}") private BigDecimal quantity;

    @Value("${emaPeriodShort}") private int emaPeriodShort;
    @Value("${emaPeriodLong}") private int emaPeriodLong;

    private final List<String[]> signals = new ArrayList<>();

    private final AccService accService;
    private Strategy strategy;

    private final BarSeries series;

    // todo: private
    EMAIndicator sema;
    EMAIndicator lema;

    private long index = 1;

    private Integer csvBarCount = 0;


    @Autowired
    public StrategyLogic(AccService accService) {
        this.accService = accService;
        this.series = new BaseBarSeriesBuilder().withName("bnc_series").build();
    }

    @Override
    public void afterPropertiesSet() {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        sema = new EMAIndicator(closePriceIndicator, this.emaPeriodShort);
        lema = new EMAIndicator(closePriceIndicator, this.emaPeriodLong);

        CrossedUpIndicatorRule entryRule = new CrossedUpIndicatorRule(sema, lema);
        CrossedDownIndicatorRule exitRule = new CrossedDownIndicatorRule(sema, lema);
        strategy = new BaseStrategy(entryRule, exitRule);
//        strategy = new BaseStrategy(
//                new OverIndicatorRule(sema, lema),
//                new UnderIndicatorRule(sema, lema)
//        );
    }

    public void handleTradeEvent(Map<String, String> message) {
        if (this.series.getEndIndex() >= 0) {
            synchronized (series) {
                double amount = Math.abs(Double.parseDouble(message.get("q")));
                double price  = Math.abs(Double.parseDouble(message.get("p")));

                // todo:
                if (amount < 0.003 && price > 100_000 && price < 20_000) return;

                if (price > 0) {
                    series.addTrade(amount, price);

                    // use trade timestamp 'T' instead, to avoid delayed trades
                    // if T >= startTime + duration * barsCount -> new bar
//                    int i = this.series.getEndIndex();
//                    if (i >= 0) {
//                        Bar previousBar = series.getBar(i);
//                    }
                    long startTime = series.getFirstBar().getBeginTime().toEpochSecond();
                    long nextBarTime = startTime + (long) barDuration * series.getBarCount();
                    long currentTradeTime = new BigDecimal(message.get("T")).longValue();
                    logger.info("startTime {} nextBarTime {} currentTradeTime {}", startTime, nextBarTime, currentTradeTime);
                    if (currentTradeTime >= nextBarTime) {
                        logger.info("currentTradeTime >= nextBarTime");
                        createNewBar();
                    }
                }
            }
        }
        // save trades to csv (p,q,T) then T
        writeToFile(TRADES_CSV, message);

        // TODO alt: create range bar
    }


    @Scheduled(cron = "*/" + "#{${barDuration}}" + " * * * * *")
    public void onTime() {
        synchronized (series) {
            try {
                if(offline) return;
                if ((series.isEmpty() || series.getBarCount() < csvBarCount) && csvBarCount > 0) return;

                // todo: may evaluate twice, delayed trades
//                if (series.getEndIndex() <= index && series.getEndIndex()!=1) return;
                logBar();
                evaluateLogic();

//                if(saveToCsv) saveBarToCSV(null);
//                createNewBar();
            } catch (Exception e) {
                logger.error("onTime: ", e);
            }
        }
    }

    public void loadBarsFromCsv(BaseBarSeries series) {
        this.csvBarCount = series.getBarCount();

        for(int i = series.getBeginIndex(); i < series.getBarCount(); i++) {
            try {
                this.series.addBar(series.getBar(i));

                logBar();  // no trades count info in bars csv
                evaluateLogic();
            } catch (Exception e) {
                logger.warn("Error adding bar {} from CSV.", i);
            }
        }
    }

    private void logBar() {
        int i = series.getEndIndex();
        index = i;
        if(i <= 0) return;

        Bar bar = series.getBar(i);
        if (i < emaPeriodLong) {
            logger.info("open {} high {} low {} close {} vol {} trades {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades());

        } else if (i > emaPeriodLong) {

            logger.info("open {} high {} low {} close {} vol {} trades {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades()
//                    longEma.getValue(i).minus(shortEma.getValue(i))
            );
        }
        try {
            logger.info(mapper.writeValueAsString(bar));  // check time
        } catch (JsonProcessingException e) {
            logger.info("err", e);
        }
    }

    private void evaluateLogic() {
        try {
            int i = series.getEndIndex();
            if (i > emaPeriodLong) {
/*
            Num emaDiff = this.emaDifference.getValue(i);
            Num emaDiffPrev = this.emaDifference.getValue(i - 1);

            if (emaDiff.doubleValue() > 0 && emaDiffPrev.doubleValue() <= 0) {

                logger.info("!!!!!!!! BUY !!!!!!!!!)");
                tradingService.sendOrder("buy", quantity, symbol);

            } else if (emaDiff.doubleValue() < 0 && emaDiffPrev.doubleValue() >= 0) {

                logger.info("!!!!!!!! SELL !!!!!!!!!");
                tradingService.sendOrder("sell", quantity, symbol);
            }
*/
                if (strategy.shouldEnter(i)) {
                    // buy
                    logger.info("!!!!!!!! BUY !!!!!!!!!)");
                    ZonedDateTime endTime = series.getBar(i).getEndTime();
                    signals.add(new String[]{Long.toString(endTime.toEpochSecond()), "B"});
                    accService.sendOrder("buy", quantity, symbol);
                } else if (strategy.shouldExit(i)) {
                    //sell or close
                    logger.info("!!!!!!!! SELL !!!!!!!!!");
                    ZonedDateTime endTime = series.getBar(i).getEndTime();
                    signals.add(new String[]{Long.toString(endTime.toEpochSecond()), "S"});
                    accService.sendOrder("sell", quantity, symbol);
                }
            }
        } catch (NullPointerException npe) {
            //
        } catch(Exception e) {
            logger.error(e.getMessage(),e);
        }
    }

    private void saveBarToCSV(Bar b) throws JsonProcessingException {
        String fileName = "bnc_trades_" + barDuration + "s.csv";

        int i = series.getEndIndex();
        if(i < 1) return;
        Bar bar = b == null ? series.getBar(i) : b;

        BarModel barModel = BarModel.fromBar(bar);
        Map<String, String> barFields =
            mapper.readValue(
                    mapper.writeValueAsString(barModel),
                    new TypeReference<LinkedHashMap<String, String>>() { }
                );

        writeToFile(fileName, barFields);
    }

    private void writeToFile(String fileName, Map<String, String> fields) {

        try (OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName), CREATE, APPEND))) {

//            fields.forEach(logger::info);
            String keys = String.join(",", fields.keySet());
            String values = String.join(",", fields.values());

            InputStream inputStream = Files.newInputStream(Paths.get(fileName), READ);
            int inputLength = inputStream.readAllBytes().length;
            int keysLength = keys.getBytes(UTF_8).length;
            byte[] newLine = lineSeparator().getBytes(UTF_8);
            if (/*!exists && */inputLength < keysLength ) {
                byte[] buffer = new byte[keysLength];  // keep new

                System.arraycopy(keys.getBytes(UTF_8), 0, buffer, 0, keysLength);
//                out.write(keys.getBytes(UTF_8));
                out.write(buffer);
                out.write(newLine, 0, newLine.length);
            }
            out.write(values.getBytes(UTF_8), 0, values.getBytes(UTF_8).length);
            out.write(newLine, 0, newLine.length);

        } catch (IOException e) {
            logger.error("Could not write to CSV.", e);
        }
    }

    private void createNewBar() {

        // create new bar
        ZonedDateTime now = ZonedDateTime.now();
        Duration duration = Duration.ofSeconds(barDuration);
        Bar newBar = new BaseBar(duration, now, this.series.function());

        // set price to closing price of previous bar
        int i = this.series.getEndIndex();
        if (i >= 0) {
            Bar previousBar = series.getBar(i);
            newBar.addPrice(previousBar.getClosePrice());

            try{
                saveBarToCSV(previousBar);
            } catch (JsonProcessingException jpe) {
                logger.error("Could not saveBarToCSV.", jpe);
            }
        }

        series.addBar(newBar);

    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

//    public static Strategy buildStrategy(BarSeries series) {
//        if (series == null) {
//            throw new IllegalArgumentException("Series cannot be null");
//        }

//        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // The bias is bullish when the shorter-moving average moves above the longer
        // moving average.
        // The bias is bearish when the shorter-moving average moves below the longer
        // moving average.
//        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
//        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

//        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);
//
//        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
//        EMAIndicator emaMacd = new EMAIndicator(macd, 18);

//        // Entry rule
//        Rule entryRule = new OverIndicatorRule(shortEma, longEma) // Trend
//                .and(new CrossedDownIndicatorRule(stochasticOscillK, 20)) // Signal 1
//                .and(new OverIndicatorRule(macd, emaMacd)); // Signal 2
//
//        // Exit rule
//        Rule exitRule = new UnderIndicatorRule(shortEma, longEma) // Trend
//                .and(new CrossedUpIndicatorRule(stochasticOscillK, 20)) // Signal 1
//                .and(new UnderIndicatorRule(macd, emaMacd)); // Signal 2
//
//        return new BaseStrategy(entryRule, exitRule);

//        return new BaseStrategy(new OverIndicatorRule(shortEma, longEma), new UnderIndicatorRule(shortEma, longEma));
//    }


    public List<String[]> getSignals() {
        return signals;
    }
}
