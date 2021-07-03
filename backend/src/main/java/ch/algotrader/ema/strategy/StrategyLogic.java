package ch.algotrader.ema.strategy;

import ch.algotrader.ema.rest.model.BarModel;
import ch.algotrader.ema.services.TradingService;
import ch.algotrader.ema.ws.model.AggTradeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
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
    }

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

    private final TradingService tradingService;
//    private DifferenceIndicator emaDifference;
    private Strategy strategy;

    private final BarSeries series;
    private ClosePriceIndicator closePriceIndicator;
    private Integer csvBarCount = 0;

    @Autowired
    public StrategyLogic(TradingService tradingService) {
        this.tradingService = tradingService;
        this.series = new BaseBarSeriesBuilder().withName("bnc_series").build();
    }

    @Override
    public void afterPropertiesSet() {
        closePriceIndicator = new ClosePriceIndicator(series);
        EMAIndicator sema = new EMAIndicator(closePriceIndicator, this.emaPeriodShort);
        EMAIndicator lema = new EMAIndicator(closePriceIndicator, this.emaPeriodLong);

        CrossedUpIndicatorRule entryRule = new CrossedUpIndicatorRule(sema, lema);
        CrossedDownIndicatorRule exitRule = new CrossedDownIndicatorRule(sema, lema);
        strategy = new BaseStrategy(entryRule, exitRule);
//        strategy = new BaseStrategy(
//                new OverIndicatorRule(sema, lema),
//                new UnderIndicatorRule(sema, lema)
//        );
    }

    public void handleTradeEvent(AggTradeEvent event) {
        if (this.series.getEndIndex() >= 0) {
            synchronized (series) {
                double amount = Math.abs(Double.parseDouble(event.getQuantity()));
                double price  = Math.abs(Double.parseDouble(event.getPrice()));
                if (price > 0) {
                    series.addTrade(amount, price);
                }
            }
        }
    }

    @Scheduled(cron = "*/" + "#{${barDuration}}" + " * * * * *")
    public void onTime() {
        synchronized (series) {
            try {
                if(offline) return;
                if ((series.isEmpty() || series.getBarCount() < csvBarCount) && csvBarCount > 0) return;

                logBar();
                evaluateLogic();

                if(saveToCsv) saveBarToCSV();
                createNewBar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void loadBarsFromCsv(BaseBarSeries series) {
        this.csvBarCount = series.getBarCount();

        for(int i = series.getBeginIndex(); i < series.getBarCount(); i++) {
            try {
                this.series.addBar(series.getBar(i));

                logBar();
                evaluateLogic();
            } catch (Exception e) {
                logger.warn("Error adding bar {} from CSV.", i);
            }
        }
    }

    private void logBar() {
        int i = series.getEndIndex();
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
//                    tradingService.sendOrder("buy", quantity, symbol);
                } else if (strategy.shouldExit(i)) {
                    //sell or close
                    logger.info("!!!!!!!! SELL !!!!!!!!!");
                    ZonedDateTime endTime = series.getBar(i).getEndTime();
                    signals.add(new String[]{Long.toString(endTime.toEpochSecond()), "S"});
//                    tradingService.sendOrder("sell", quantity, symbol);
                }
            }
        } catch(Exception e) {
            logger.error(e.getMessage(),e);
        }
    }

    private void saveBarToCSV() {
        String fileName = "bnc_trades_" + barDuration + "s.csv";
        int i = series.getEndIndex();
        if(i < 1) return;
        Bar bar = series.getBar(i);

//        if (bar.getTrades() == 0 ) return;

        try (OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName), CREATE, APPEND))) {

            BarModel barModel = BarModel.fromBar(bar);
            Map<String, String> barFields =
                    mapper.readValue(
                            mapper.writeValueAsString(barModel),
                            new TypeReference<LinkedHashMap<String, String>>() { }
                    );
//            values.forEach(logger::info);
            String keys = String.join(",", barFields.keySet());
            String values = String.join(",", barFields.values());

            InputStream inputStream = Files.newInputStream(Paths.get(fileName), READ);
            int inputLength = inputStream.readAllBytes().length;
            int keysLength = keys.getBytes(UTF_8).length;
            byte[] newLine = lineSeparator().getBytes(UTF_8);
            if (/*!exists && */inputLength < keysLength ) {
                byte[] buffer = new byte[keysLength];  // keep new

                System.arraycopy(keys.getBytes(UTF_8), 0, buffer, 0, keysLength);
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
