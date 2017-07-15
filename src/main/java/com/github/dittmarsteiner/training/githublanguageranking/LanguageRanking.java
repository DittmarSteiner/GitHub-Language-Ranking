/*
 * ------------------------------------------------------------------------------
 * ISC License http://opensource.org/licenses/isc-license.txt
 * ------------------------------------------------------------------------------
 * Copyright (c) 2017, Dittmar Steiner <dittmar.steiner@gmail.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package com.github.dittmarsteiner.training.githublanguageranking;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Analyzes the rankings of programming languages of one hour of one day on
 * GitHub.
 * 
 * <ol>
 * <li>Downloads a file like
 * <a href="http://data.githubarchive.org/2016-03-14-15.json.gz">http://data.githubarchive.org/2016-03-14-15.json.gz</a>
 * <li>Collects ativities for each occurring language
 * <li>Sorts languages by number of activities
 * <li>Exports the result as CSV like <code>2016-03-14-15.csv</code>
 * </ol>
 * 
 * @author <a href="mailto:dittmar.steiner@gmail.com">Dittmar Steiner</a>
 */
public class LanguageRanking {

    public static final String BASE_URL = "http://data.githubarchive.org";

    private static final String defaultDateTime = "2016-03-14-15";

    /**
     * {@code "RANK,LANGUAGE,ACTIVITIES,PROPORTION,"}
     */
    static final String CSV_HEADER = "RANK,LANGUAGE,ACTIVITIES,PROPORTION,";

    private final Gson GSON = new Gson();

    /**
     * Default is {@code true} in case it was used as a library.
     */
    private boolean silent = true;

    /**
     * Downloads {@code http://data.githubarchive.org/<yyyy-MM-dd-HH>.json.gz}
     * if not yet done, collects the languages, evaluates the rankings and
     * prints out as CSV.
     * 
     * @param dateTime
     *            in the form of {@code "yyyy-MM-dd-HH"}
     * @param out
     *            auto-closes ({@link System#out} does not closed)
     * @throws Exception
     */
    public void analyze(final String dateTime, PrintStream out) throws Exception {
        try (PrintStream ps = out) {
            String dt;
            try {
                DateTimeFormatter dtf =
                        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
                dt = LocalDateTime.parse(dateTime, dtf).format(dtf);
            }
            catch (DateTimeException e) {
                String msg = String.format(
                        "Wrong date format: %s\n"
                        + "Use 'yyyy-MM-dd-HH' like '%s'",
                        dateTime, defaultDateTime);

                throw new RuntimeException(msg);
            }

            // .json.gz
            Path path = download(dt);

            // by language
            Map<String, Integer> languages = collect(path);

            // this is the model – a sorted list of entries
            List<Entry<String, Integer>> rankings = rank(languages);

            // print CSV
            export(rankings, ps);
        }
    }

    /**
     * Download if not yet present.
     * 
     * @param dateTime
     * @return the downloded file
     * @throws IOException
     */
    Path download(String dateTime) throws IOException {
        String fileNameGz = String.format("%s.json.gz", dateTime);

        Path path = Paths.get(fileNameGz);
        if (Files.exists(path)) {
            if (!silent) {
                System.err.println(String.format(
                        "File %s already downloaded...", path.getFileName()));
            }

            return path;
        }

        if (!silent) {
            System.err.print(String.format(
                    "Downloading file %s ... ", path.getFileName()));
        }

        String url = String.format("%s/%s", BASE_URL, fileNameGz);

        HttpRequest req = HttpRequest.get(url).receive(
                Files.newOutputStream(path)
            );
        int code = req.code();
        if (!req.ok()) {
            throw new IOException(
                    String.format("Received HTTP %s %s", code, req.message()));
        }

        if (!silent) {
            System.err.println(String.format("done.", path.getFileName()));
        }

        return path;
    }

    /**
     * Reads all lines from a gzip file.
     * 
     * @param path
     * @return the result grouped by language
     * @throws Exception
     */
    Map<String, Integer> collect(Path path) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8))) {
            Map<String, Integer> languages = br.lines()
                // not an advantage here because of unknown number of lines
                //.parallel()
                // pre-filter
                .filter(l -> l.contains("\"language\""))
                // map to Optional<String>
                .map(this::findLanguage)
                // post-filter
                .filter(Optional::isPresent)
                // map to language name
                .map(Optional::get)
                // as list of language names
                .collect(toList())
                .stream()
                // every found language with its number of occurrences
                .collect(groupingBy(String::toString, counting()));

            return languages;
        }
    }

    /**
     * <code>Integer</code> version of {@link Collectors#counting()}.
     * 
     * @param <T> the type of the input elements
     * @return  a {@code Collector} that counts the input elements
     * 
     * @see Collectors#counting()
     */
    private static <T> Collector<T, ?, Integer>  counting() {
        return reducing(0, e -> 1, Integer::sum);
    }

    /**
     * Transforms into an {@link JsonObject} and traverses it for
     * {@code "language": "..."}
     * 
     * @param line
     * @return containing the language name or empty
     */
    Optional<String> findLanguage(String line) {
        JsonObject json = GSON.fromJson(line, JsonObject.class);

        return findLanguage(json);
    }

    private Optional<String> findLanguage(JsonObject json) {
        // this is a hit
        if (json.has("language") && !json.get("language").isJsonNull()) {
            return Optional.of(json.get("language").getAsString());
        }

        return json.entrySet().stream()
                .map(Entry::getValue)
                .map(this::findLanguage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny();
    }

    private Optional<String> findLanguage(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(this::findLanguage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny(); // the hit (if any) or empty
    }

    private Optional<String> findLanguage(JsonElement entry) {
        if (entry.isJsonObject()) {
            return findLanguage(entry.getAsJsonObject());
        }
        else if (entry.isJsonArray()) {
            return findLanguage(entry.getAsJsonArray());
        }

        return Optional.empty();
    }

    /**
     * Transforms the the model from a map of languages to a list of entries,
     * sorted by their values and then keys.
     * 
     * @param languages
     * @return sorted by their number of occurrences
     */
    List<Entry<String, Integer>> rank(Map<String, Integer> languages) {

        Comparator<Entry<String, Integer>> byOccurrences =
                comparing(Entry::getValue);

        Comparator<Entry<String, Integer>> byName =
                comparing(Entry::getKey);

        return languages
                // we work on the Entry<String,Integer>
                .entrySet()
                .stream()
                // first by revered occurrences and then by language name
                .sorted(byOccurrences.reversed().thenComparing(byName))
                // add everything ordered into a list
                .collect(Collectors.toList());
    }

    /**
     * Prints the ranking list.
     * 
     * @param rankings
     * @param out
     */
    void export(List<Entry<String, Integer>> rankings, PrintStream out) {
        // reduce to the total number of occurrences
        int all = rankings.stream()
            .mapToInt(e -> e.getValue())
            .sum();

        out.println(CSV_HEADER);
        for (int i = 0; i < rankings.size(); i++) {
            double percent =
                    (double) rankings.get(i).getValue() / all * 100.0;
            out.println(String.format(
                    "%s,\"%s\",%s,\"%.2f %%\",\"%s\"",
                    i+1,
                    rankings.get(i).getKey(),
                    rankings.get(i).getValue(),
                    // complete with the inline calculated percentage
                    percent,
                    // eye candy bars ;-)
                    buildBar(percent)
                ));
        }
    }

    /** To complete a progress bar. Chars from
     * <table>
     * <tr><td>██ </td><td>2.000 is 2 block(s) and remainder #0 (&#92;u----)</td></tr>
     * <tr><td>██▏</td><td>2.125 is 2 block(s) and remainder #1 (\u258f)</td></tr>
     * <tr><td>██▎</td><td>2.250 is 2 block(s) and remainder #2 (\u258e)</td></tr>
     * <tr><td>██▍</td><td>2.375 is 2 block(s) and remainder #3 (\u258d)</td></tr>
     * <tr><td>██▌</td><td>2.500 is 2 block(s) and remainder #4 (\u258c)</td></tr>
     * <tr><td>██▋</td><td>2.625 is 2 block(s) and remainder #5 (\u258b)</td></tr>
     * <tr><td>██▊</td><td>2.750 is 2 block(s) and remainder #6 (\u258a)</td></tr>
     * <tr><td>██▉</td><td>2.875 is 2 block(s) and remainder #7 (\u2589)</td></tr>
     * <tr><td>███</td><td>3.000 is 3 block(s) and remainder #0 (&#92;u----)</td></tr>
     * </table>
    */
    static final String[] BLOCKS = {
            "", // empty
            "▏", // \u258f
            "▎", // \u258e
            "▍", // \u258d
            "▌", // \u258c
            "▋", // \u258b
            "▊", // \u258a
            "▉", // \u2589
            "█", // \u2588
    };

    /**
     * Builds a Unicode bar from {@link #BLOCKS}
     * @param percent
     * @return the bar made of Unicode blocks
     */
    String buildBar(double percent) {
        int i = (int) percent;
        double r = percent % 1 / 1.25 * 10;
        int trail = new BigDecimal(r).setScale(0, RoundingMode.HALF_UP)
                .intValue();

        StringBuilder sb = new StringBuilder(i +1);
        for (int d = 0; d < i; d++) {
            sb.append("█"); // \u2588
        }
        sb.append(BLOCKS[trail]);

        return sb.toString();
    }

    /**
     * @param args
     */
    public static void main(String... args) {
        List<String> argList = Arrays.asList(args);
        argList.stream().filter(arg -> "-h".equals(arg) || "--help".equals(arg))
                .findAny().ifPresent(arg -> {
                    printUsageAndExit();
                    System.exit(0);
                });

        LanguageRanking languageRanking = new LanguageRanking();
        languageRanking.silent = argList.stream()
                .anyMatch(arg -> "-s".equals(arg) || "--silent".equals(arg));

        String dateTime = argList.stream().filter(arg -> !arg.startsWith("-"))
                .findAny().orElse(defaultDateTime);
        try {
            languageRanking.analyze(dateTime, System.out);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());

            if (!languageRanking.silent) {
                e.printStackTrace();
            }

            System.exit(1);
        }
    }

    static void printUsageAndExit() {
        System.err.println("Usage: java -jar github-language-ranking.jar [OPTION]... [DATE-TIME]");
        System.err.println("Options:");
        System.err.println("  -s, --silent Silent mode (don't output anything)");
        System.err.println("  -h, --help   This help");
        System.err.println(String.format(
                "Where date-time requires the pattern 'yyyy-MM-dd-HH' like '%s'.",
                defaultDateTime));
    }
}
