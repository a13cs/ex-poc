package ch.algotrader.ema.rest;

import ch.algotrader.ema.services.SeriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
public class EmaRest {

    @Autowired
    BarSeries series;

    @Autowired
    SeriesService seriesService;

    private static final String FILE_NAME = "bnc_trades_" + 5 + "s.csv";

    private static final Logger logger = LoggerFactory.getLogger(EmaRest.class);

    @RequestMapping(method = RequestMethod.GET, path = "/bars/{index}")
    public List<List<String>> latestBars(@PathVariable(value = "index") String index) throws IOException {
        return seriesService.getLatestBars(index, Paths.get(FILE_NAME));
    }

    @RequestMapping(method = RequestMethod.GET, path = "/bars/{indicator}/{index}")
    public List<List<String>> latestIndicators(@PathVariable(value = "indicator") String indicator, @PathVariable(value = "index") String index) throws IOException {
        return seriesService.getIndicator(indicator, index, Paths.get(FILE_NAME));
    }

}
