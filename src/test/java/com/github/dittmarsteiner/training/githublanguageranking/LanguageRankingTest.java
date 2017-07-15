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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author <a href="mailto:dittmar.steiner@gmail.com">Dittmar Steiner</a>
 */
public class LanguageRankingTest {

    private static final org.slf4j.Logger LOG =
            LoggerFactory.getLogger(LanguageRankingTest.class);

    static String JSON;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        JSON = new String(
                Files.readAllBytes(
                        Paths.get("src/test/resources/oneline-Java.json")),
                StandardCharsets.UTF_8);
    }

    @Test
    public void testFindLanguageInLine() throws Exception {
        LanguageRanking languageRanking = new LanguageRanking();
        String language = languageRanking.findLanguage(JSON).get();
        assertEquals("Java", language);
    }

    @Test
    public void testCollectFromSampleGzipFile() throws Exception {
        LanguageRanking languageRanking = new LanguageRanking();
        Map<String, Integer> languages = languageRanking.collect(Paths.get("src/test/resources/sample.json.gz"));
        assertEquals(new Integer(4), languages.get("Python"));
        assertEquals(new Integer(3), languages.get("Java"));
    }

    /**
     * Test method for
     * {@link com.github.dittmarsteiner.training.githublanguageranking.LanguageRanking#download(String)}.
     * @throws Exception
     */
    @Test
    public void testDownload() throws Exception {
        if (!Boolean.getBoolean("test.download")) {
            LOG.info("No option \"-Dtest.download\" found -> skip MainTest.testDownload()");
            return;
        }

        String dateTime = "2017-04-01-14";
        Path path = Paths.get(String.format("%s.json.gz", dateTime));
        Files.deleteIfExists(path);
        LanguageRanking languageRanking = new LanguageRanking();
        Path downloaded = languageRanking.download("2017-04-01-14");

        // the size will never change!
        // $ curl -sI http://data.githubarchive.org/2017-04-01-14.json.gz | grep 'Content-Length' | cut -d ' ' -f 2
        // vs
        // $ ls -l 2017-04-01-14.json.gz | cut -d ' ' -f 5
        assertEquals(20_428_417, Files.size(downloaded));
    }

    /**
     * Test method for
     * {@link com.github.dittmarsteiner.training.githublanguageranking.LanguageRanking#rank(java.util.Map)}.
     */
    @Test
    public void testRank() {
        LanguageRanking languageRanking = new LanguageRanking();

        Map<String, Integer> languages = new HashMap<>();
        languages.put("Java", 501);
        languages.put("PHP", 304);
        languages.put("Prolog", 1);
        languages.put("Assembly", 1);
        languages.put("Apex", 1);

        List<Entry<String, Integer>> rankings = languageRanking.rank(languages);

        assertEquals("Java", rankings.get(0).getKey());
        assertEquals("PHP", rankings.get(1).getKey());
        assertEquals("Apex", rankings.get(2).getKey());
        assertEquals("Assembly", rankings.get(3).getKey());
        assertEquals("Prolog", rankings.get(4).getKey());
    }

    @Test
    public void testBuildBar() {
        LanguageRanking languageRanking = new LanguageRanking();

        double[] values = {
                2.000, 2.062,
                2.063, 2.125, 2.187,
                2.188, 2.250, 2.312,
                2.313, 2.375, 2.437,
                2.438, 2.500, 2.562,
                2.563, 2.625, 2.687,
                2.688, 2.750, 2.812,
                2.813, 2.875, 2.937,
                2.938, 2.999,
                3.0,
                3.1
        };

        for (double v : values) {
            String s = languageRanking.buildBar(v);

            if (v < 2.063) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[0]));
            }
            else if (v < 2.188) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[1]));
            }
            else if (v < 2.313) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[2]));
            }
            else if (v < 2.438) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[3]));
            }
            else if (v < 2.563) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[4]));
            }
            else if (v < 2.688) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[5]));
            }
            else if (v < 2.813) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[6]));
            }
            else if (v < 2.938) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[7]));
            }
            else if (v < 3.000) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[8]));
            }
            else if (v >= 3.000) {
                assertTrue(s.endsWith(LanguageRanking.BLOCKS[0]));
            }
        }
    }

    /**
     * Test method for
     * {@link com.github.dittmarsteiner.training.githublanguageranking.LanguageRanking#export(java.util.List, java.io.PrintStream)}
     * .
     * @throws IOException
     */
    @Test
    public void testExport() throws IOException {
        LanguageRanking languageRanking = new LanguageRanking();

        List<Entry<String, Integer>> rankings = new ArrayList<>();
        rankings.add(new AbstractMap.SimpleEntry<String, Integer>("A", 3));
        rankings.add(new AbstractMap.SimpleEntry<String, Integer>("B", 2));
        rankings.add(new AbstractMap.SimpleEntry<String, Integer>("C", 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        languageRanking.export(rankings, new PrintStream(out));

        BufferedReader br = new BufferedReader(new StringReader(out.toString()));
        assertEquals(LanguageRanking.CSV_HEADER, br.readLine());
        assertTrue(br.readLine().startsWith("1,\"A\",3,\"50.00 %\""));
        assertTrue(br.readLine().startsWith("2,\"B\",2,\"33.33 %\""));
        assertTrue(br.readLine().startsWith("3,\"C\",1,\"16.67 %\""));
    }
}
