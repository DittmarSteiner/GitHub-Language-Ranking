/*
 * ------------------------------------------------------------------------------
 * ISC License http://opensource.org/licenses/isc-license.txt
 * ------------------------------------------------------------------------------
 * Copyright (c) 2016, Dittmar Steiner <dittmar.steiner@gmail.com>
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

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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

    /**
     * <code>http://data.githubarchive.org</code>
     */
    public static final String BASE_URL = "http://data.githubarchive.org";
    public static final String SUFFIX_JSON = "json";
    public static final String SUFFIX_GZ = "gz";
    public static final String SUFFIX_CSV = "csv";
    
    protected static final Gson GSON = new Gson();
    
    /**
     * <code>&quot;RANK,LANGUAGE,ACTIVITIES,PROPORTION,&quot;</code>
     */
    protected static final String CSV_HEADER = "RANK,LANGUAGE,ACTIVITIES,PROPORTION,";
    
    /**
     * Default is <code>false</code> in case it was used as a library.  
     */
    private boolean silent = true;
    
    /**
     * This method (and class) is stateless.
     * 
     * @param dateTime
     * @throws Exception
     */
    public void analyze(final String dateTime) throws Exception {
        String dt = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH");
        try {
            Date date = sdf.parse(dateTime);
            // Oh, it suvived!
            dt = sdf.format(date);
        }
        catch (ParseException e) {
            String msg = String.format(
                    "Wrong date format: %s\n"
                    + "Use 'yyyy-MM-dd-HH' like '2016-03-14-15'", 
                    dateTime);
            
            throw new Exception(msg);
        }
        
        // .gz
        Path path = download(dt);
        
        // by language
        Map<String, Integer> languages = collect(path);
        
        // this is the model – a sorted list of entries
        List<Entry<String, Integer>> rankings = rank(languages);
        
        // .csv
        // FIXME Path, not File
        File csv = new File(String.format("%s.%s", dt, SUFFIX_CSV));
        PrintStream printStream = new PrintStream(
                Files.newOutputStream(Paths.get(csv.toURI())));
        
        export(rankings, printStream);
        
        if (!silent) {
            System.err.println(String.format(
                    "Ranking report completed %s", csv.getName()));
        }
    }
    
    /**
     * Download if not yet present.
     * 
     * @param dateTime
     * @return the downloded file
     * @throws IOException
     */
    protected Path download(String dateTime) throws IOException {
        String fileNameGz = String.format("%s.%s.%s", dateTime, SUFFIX_JSON, SUFFIX_GZ);
        
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
                Files.newOutputStream(Paths.get(path.toUri()))
            );
        int code = req.code();
        if (!req.ok()) {
            if (!silent) {
                System.err.println("failed.");
            }
            
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
    protected Map<String, Integer> collect(Path path) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), 
                StandardCharsets.UTF_8))) {
            Map<String, Integer> languages = 
            br.lines()
            .parallel()
            .filter(l -> l.contains("\"language\""))
            .map(this::findLanguage)
            .filter(Objects::nonNull)
            .collect(toList())
            .stream()
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
    
    // XXX Javadoc
    protected String findLanguage(String line) {
        JsonObject json = GSON.fromJson(line, JsonObject.class);
        
        return findLanguage(json);
    }
    
    // XXX Javadoc
    protected String findLanguage(JsonObject json) {
        if (json.has("language") && !json.get("language").isJsonNull()) {
            return json.get("language").getAsString();
        }
        
        for (Entry<String, JsonElement> entry : json.entrySet()) {
            String language = null;
            if (entry.getValue().isJsonObject()) {
                language = findLanguage(entry.getValue().getAsJsonObject());
            }
            else if (entry.getValue().isJsonArray()) {
                language = findLanguage(entry.getValue().getAsJsonArray());
            }
            if (language != null) {
                return language;
            }
        }
        
        return null;
    }
    
    // XXX Javadoc
    private String findLanguage(JsonArray array) {
        for (JsonElement el : array) {
            if (el.isJsonObject()) {
                    return findLanguage(el.getAsJsonObject());
            }
            else if (el.isJsonArray()) {
                return findLanguage(el.getAsJsonArray());
            }
        }
        
        return null;
    }
    
    /**
     * Transforms the the model from a map of languages to a list of entries,
     * sorted by their values and then keys.
     * 
     * @param languages
     * @return sorted by their number of occurrences
     */
    protected List<Entry<String, Integer>> rank(Map<String, Integer> languages) {
        
        Comparator<Entry<String, Integer>> byOccurrences = 
                comparing(Entry::getValue);
        
        Comparator<Entry<String, Integer>> byName = 
                comparing(Entry::getKey);
        
        return languages
                // we work on the Entry<String,Integer>
                .entrySet()
                // not parallel
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
    protected void export(List<Entry<String, Integer>> rankings, 
            PrintStream out) {
        // reduce to the total number of occurrences
        int all = rankings.stream()
            .mapToInt(e -> e.getValue())
            .sum();
        
        // auto-close
        try (PrintStream ps = out) {
            ps.println(CSV_HEADER);
            for (int i=0; i < rankings.size(); i++) {
                double percent = (double)rankings.get(i).getValue() / all * 100.0;
                ps.println(String.format(
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
    protected static final String[] BLOCKS = {
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
    protected String buildBar(double percent) {
        int i = (int) percent;
        double r = percent % 1 / 1.25 * 10;
        int trail = new BigDecimal(r).setScale(0, RoundingMode.HALF_UP)
                .intValue();
        
        StringBuilder sb = new StringBuilder();
        for (int d=0; d < i; d++) {
            sb.append("█"); // \u2588
        }
        sb.append(BLOCKS[trail]);
        
        return sb.toString();
    }
   
    /**
     * @param args
     */
    public static void main(String[] args) {
        boolean silent = false;
        if (args != null) {
            for (String arg : args) {
                if ("-s".equals(arg) || "--silent".equals(arg)) {
                    silent = true;
                }
                else if ("-h".equals(arg) || "--help".equals(arg)) {
                    printUsage(System.err);
                    
                    System.exit(0);
                }
            }
        }
        
        // just a nice defaullt for demo reasons
        String dateTime = "2016-03-14-15";
        if (args != null && args.length > 0) {
            dateTime = args[args.length - 1];
        }
        
        LanguageRanking languageRanking = new LanguageRanking();
        languageRanking.silent = silent;
        try {
            languageRanking.analyze(dateTime);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            
            if (!silent) {
                e.printStackTrace();
            }
            
            System.exit(1);
        }
    }
    
    protected static void printUsage(PrintStream out) {
        out.println("Usage: java -jar github-language-ranking.jar [OPTION]... [DATE-TIME]");
        out.println("Options:");
        out.println("  -s, --silent Silent mode (don't output anything)");
        out.println("  -h, --help   This help");
        out.println("Where date-time requires the pattern 'yyyy-MM-dd-HH' like '2016-03-14-15'.");
    }
}
