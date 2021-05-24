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
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.num.Num;
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
import java.util.HashMap;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class StrategyLogic implements InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(StrategyLogic.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.findAndRegisterModules();
    }

    private final int barDuration = 5;

    @Value("${symbol}") private String symbol;
    @Value("${quantity}") private BigDecimal quantity;

    @Value("${emaPeriodShort}") private int emaPeriodShort;
    @Value("${emaPeriodLong}") private int emaPeriodLong;

    private final TradingService tradingService;
    private final BarSeries series;

    private DifferenceIndicator emaDifference;

    @Autowired
    public StrategyLogic(TradingService tradingService) {
        this.tradingService = tradingService;
        this.series = new BaseBarSeriesBuilder().withName("bnc_series").build();
    }

    @Override
    public void afterPropertiesSet() {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(this.series);
        EMAIndicator emaShort = new EMAIndicator(closePriceIndicator, this.emaPeriodShort);
        EMAIndicator emaLong = new EMAIndicator(closePriceIndicator, this.emaPeriodLong);
        this.emaDifference = new DifferenceIndicator(emaShort, emaLong);
    }

    public void handleTradeEvent(AggTradeEvent event) {
        if (this.series.getEndIndex() >= 0) {
            synchronized (series) {
                if (isNotBlank(event.getQuantity()) && isNotBlank(event.getPrice()))
                    series.addTrade(
                            Math.abs(Double.parseDouble(event.getQuantity())),
                            Math.abs(Double.parseDouble(event.getPrice()))
                    );
            }
        }
    }

    @Scheduled(cron = "*/" + barDuration + " * * * * *")
    public void onTime() {
        synchronized (series) {
            try {
                logBar();
                saveBarToCSV();
                evaluateLogic();
                createNewBar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveBarToCSV() {
        String fileName = "bnc_trades_" + barDuration + "s.csv";
        int i = series.getEndIndex();
        if(i < 1) return;

//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        boolean exists = Files.exists(Paths.get(fileName));
        try (OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName), CREATE, APPEND))) {

            HashMap<String, String> barFields =
                    mapper.readValue(
                        mapper.writeValueAsString(BarModel.fromBar(series.getLastBar())),
                        new TypeReference<HashMap<String, String>>() { }
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
//                baos.write(keys.getBytes(UTF_8));
//                baos.write(lineSeparator().getBytes(UTF_8));
//                baos.writeTo(out);
            }
            out.write(values.getBytes(UTF_8), 0, values.getBytes(UTF_8).length);
            out.write(newLine, 0, newLine.length);
//            baos.write(values.getBytes(UTF_8));
//            out.write(newLine, 0, newLine.length);
//            baos.writeTo(out);
        } catch (IOException e) {
            logger.error("Could not write to CSV.", e);
        }
    }

    private void logBar() {
        int i = this.series.getEndIndex();
        if(i <= 0) return;

        Bar bar = this.series.getBar(i);
        if (i < emaPeriodLong) {
            logger.info("open {} high {} low {} close {} vol {} trades {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades());
            
        } else if (i > emaPeriodLong) {
            Num emaDiff = this.emaDifference.getValue(i);
            Num emaDiffPrev = this.emaDifference.getValue(i - 1);

            logger.info("open {} high {} low {} close {} vol {} trades {} emaDiffPrev {} emaDiff {}",
                    bar.getOpenPrice(),
                    bar.getHighPrice(),
                    bar.getLowPrice(),
                    bar.getClosePrice(),
                    bar.getVolume(),
                    bar.getTrades(),
                    emaDiffPrev,
                    emaDiff);
        }
        try {
            logger.info(mapper.writeValueAsString(bar));
        } catch (JsonProcessingException e) {
            logger.info("err", e);
        }

    }

    private void evaluateLogic() {

        Strategy s = buildStrategy(series);

        int i = this.series.getEndIndex();
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
            }*/

            if(s.shouldEnter(i)) {
                // buy
                logger.info("!!!!!!!! BUY !!!!!!!!!)");
//                tradingService.sendOrder("buy", quantity, symbol);
            } else if(s.shouldExit(i)) {
                //sell or close
                logger.info("!!!!!!!! SELL !!!!!!!!!");
//                tradingService.sendOrder("sell", quantity, symbol);
            }
        }
    }

    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // The bias is bullish when the shorter-moving average moves above the longer
        // moving average.
        // The bias is bearish when the shorter-moving average moves below the longer
        // moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);

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

        return new BaseStrategy(new OverIndicatorRule(shortEma, longEma), new UnderIndicatorRule(shortEma, longEma));
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

}
