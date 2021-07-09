package ch.algotrader.ema.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.NotSupportedException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

public class BarUtils {

    private static final Logger logger = LoggerFactory.getLogger(BarUtils.class);
    private static final byte[] NEW_LINE = lineSeparator().getBytes(UTF_8);


    public BarUtils() throws NotSupportedException {
        throw new NotSupportedException("Bar Utils");
    }

    //  with header
    public static void writeToFile(String fileName, Map<String, String> fields) {

        try (OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName), CREATE, APPEND))) {
//            fields.forEach(logger::info);
            String keys = String.join(",", fields.keySet());
            String values = String.join(",", fields.values());

            InputStream inputStream = Files.newInputStream(Paths.get(fileName), READ);
            int inputLength = inputStream.readAllBytes().length;
            int keysLength = keys.getBytes(UTF_8).length;
            if (/*!exists && */inputLength < keysLength ) {
                byte[] buffer = new byte[keysLength];  // keep new

                System.arraycopy(keys.getBytes(UTF_8), 0, buffer, 0, keysLength);
//                out.write(keys.getBytes(UTF_8));
                out.write(buffer);
                out.write(NEW_LINE, 0, NEW_LINE.length);
            }
            out.write(values.getBytes(UTF_8), 0, values.getBytes(UTF_8).length);
            out.write(NEW_LINE, 0, NEW_LINE.length);

        } catch (IOException e) {
            logger.error("Could not write to CSV.", e);
        }
    }

    // TODO
    public static void writeValuesToFile(String fileName, List<String> fields) {
//        fileName = fileName.split("\\.")[0] + "_" + Instant.now().getEpochSecond() + ".csv";

        try (OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName), CREATE, APPEND))) {
            String values = String.join(",", fields);
            out.write(values.getBytes(UTF_8), 0, values.getBytes(UTF_8).length);
            out.write(NEW_LINE, 0, NEW_LINE.length);

        } catch (IOException e) {
            logger.error("Could not write bars to CSV.", e);
        }
    }

}
