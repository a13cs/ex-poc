package ch.algotrader.ema.rest;

import ch.algotrader.ema.services.AccService;
import ch.algotrader.ema.strategy.SeriesService;
import ch.algotrader.ema.vo.AccInfoResponse;
import ch.algotrader.ema.vo.AccTradesResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

//@ConditionalOnExpression(value = "${initFromCsv:true}")
@RestController
public class EmaRest {

    @Value("${barDuration}")
    private int barDuration;

    @Value("${initFromCsv}")
    private boolean initFromCsv;

    @Autowired
    SeriesService seriesService;

    @Autowired
    AccService accService;

    private static final String FILE_NAME = "bnc_trades_%ss%s.csv";

    private static final Logger logger = LoggerFactory.getLogger(EmaRest.class);

    // TODO:
    //  use csv series only for back testing
    //  add saveToCsvParam
    @RequestMapping(method = RequestMethod.GET, path = "/bars/{from}")
    public List<List<String>> latestBars(@PathVariable(value = "from") String from) {
//        String fileName = String.format(FILE_NAME, barDuration,"");
//        return seriesService.getLatestCSVBars(from, Paths.get(fileName));

        List<List<String>> barsFromTrades = seriesService.getTradesSeries(""); // add filename 'seconds' param
        barsFromTrades.forEach(t -> logger.info(Arrays.toString(t.toArray(new String[0]))));

        return barsFromTrades;
    }

    //todo: add local run trading report endpoint

    // {indicator} = short / long (ema)
    @RequestMapping(method = RequestMethod.GET, path = "/indicator/{indicator}/{from}/{timestamp}")
    public List<String> latestIndicators(
            @PathVariable(value = "indicator") String indicator,
            @PathVariable(value = "from") String from,
            @PathVariable(value = "timestamp") String timestamp) {
        String fileName = String.format(FILE_NAME, barDuration, timestamp);
        return seriesService.getIndicator(indicator, from, Paths.get(fileName));
    }

    @RequestMapping(method = RequestMethod.GET, path = "/lastTrade")
    public String lastTrade() {
        return seriesService.getLastTradePrice();
    }

    @RequestMapping(method = RequestMethod.GET, path = "/barDuration")
    public String barDuration() {
        return Integer.toString(barDuration);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/indicator/{indicator}")
    public List<String> latestRuntimeIndicator(@PathVariable(value = "indicator") String indicatorName) {
        return seriesService.getRuntimeIndicator(indicatorName);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/signals/{from}")
    public List<List<String>> latestSignals(@PathVariable(value = "from") String from) {
        // todo: use from
        return seriesService.getSignals(from);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/acc")
    public AccInfoResponse accInfo() throws JsonProcessingException {
        return accService.getInfo();
    }

    @RequestMapping(method = RequestMethod.GET, path = "/myTrades")
    public List<AccTradesResponse> accTradesList() throws JsonProcessingException {
        return accService.getAccTradesList();
    }

}
