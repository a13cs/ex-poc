package ch.algotrader.ema.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EmaRest {

    @RequestMapping(method = RequestMethod.GET, path = "/bars/{index}")
    public List<HashMap<String, String>> latestBars(@PathVariable(value = "index") String index) throws IOException {
        // todo: read csv
        String fileName = "bnc_trades_" + 5 + "s.csv";
        FileSystem fs = FileSystems.newFileSystem(Paths.get(fileName).toUri(), Collections.singletonMap("create", "true"));
        Path path = fs.getPath(fileName);
        if(Files.exists(path)) {
            BufferedReader reader = Files.newBufferedReader(path);
            int size = Integer.getInteger(String.valueOf(Files.size(path)));
            char[] buffer = new char[size];

            int skip = Integer.getInteger(String.valueOf(reader.skip(Integer.parseInt(index))));
            int read = reader.read(buffer, skip, size);
            if (read > 0) {
                BufferedReader bufferedReader =
                        new BufferedReader(
                                new InputStreamReader(
                                        new ByteArrayInputStream(
                                                String.copyValueOf(buffer).getBytes(StandardCharsets.UTF_8))));

                return bufferedReader.lines().map(line -> {
                                try {
                                    return new ObjectMapper()
                                                .readValue(line, new TypeReference<HashMap<String, String>>() {});
                                } catch (JsonProcessingException e) {
                                    return new HashMap<String, String>();
                                }
                }).collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

}
