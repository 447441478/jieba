package com.huaban.analysis.jieba;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;


public class WordDictionary {
    private static final String MAIN_DICT = "/dict.txt";
    private static WordDictionary singleton;
    private static String USER_DICT_SUFFIX = ".dict";

    public final Map<String, Double> freqs = new HashMap<String, Double>();
    public final Map<String, Long> loadedPath = new HashMap<String, Long>();
    private Double minFreq = Double.MAX_VALUE;
    private Double total = 0.0;
    private DictSegment _dict;

    private WordDictionary() {
        this.loadDict();
    }

    public static WordDictionary getInstance() {
        if (singleton == null) {
            synchronized (WordDictionary.class) {
                if (singleton == null) {
                    singleton = new WordDictionary();
                    return singleton;
                }
            }
        }
        return singleton;
    }

    private boolean checkModify(File configFile){
        WordDictionary instance = getInstance();
        int count = 0;
        for (File userDict : configFile.listFiles()) {
            if (userDict.getPath().endsWith(USER_DICT_SUFFIX)) {
                Long historyLastModify = instance.loadedPath.getOrDefault(userDict.getAbsolutePath(), 0L);
                if(Objects.equals(historyLastModify, userDict.lastModified())){
                    count++;
                    continue;
                }
                return true;
            }
        }
        return instance.loadedPath.size() != count;
    }

    public static synchronized void reload(File configFile){
        if(Objects.isNull(configFile)){
            return;
        }
        if(!getInstance().checkModify(configFile)){
            System.out.println("user dic not modify");
            return;
        }
        WordDictionary wordDictionary = new WordDictionary();
        wordDictionary.init(configFile, wordDictionary);
        WordDictionary.singleton = wordDictionary;
    }


    /**
     * for ES to initialize the user dictionary.
     * You can call this method periodly for dynamic load new word
     *
     * @param configFile
     */
    public void init(File configFile, WordDictionary wordDictionary) {
        String path = configFile.getAbsolutePath();
        System.out.println("initialize user dictionary:" + path);
        synchronized (WordDictionary.class) {
            for (File userDict : configFile.listFiles()) {
                if (userDict.getPath().endsWith(USER_DICT_SUFFIX)) {
                    wordDictionary.loadUserDict(userDict);
                    loadedPath.put(userDict.getAbsolutePath(), userDict.lastModified());
                }
            }
        }
    }


    public void loadDict() {
        _dict = new DictSegment((char) 0, new HashMap<>());
        InputStream is = this.getClass().getResourceAsStream(MAIN_DICT);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            long s = System.currentTimeMillis();
            while (br.ready()) {
                String line = br.readLine();
                String[] tokens = line.split("[\t ]+");

                if (tokens.length < 2)
                    continue;

                String word = tokens[0];
                double freq = Double.valueOf(tokens[1]);
                total += freq;
                word = addWord(word);
                freqs.put(word, freq);
            }
            // normalize
            for (Entry<String, Double> entry : freqs.entrySet()) {
                entry.setValue((Math.log(entry.getValue() / total)));
                minFreq = Math.min(entry.getValue(), minFreq);
            }
            System.out.println(String.format("main dict load finished, time elapsed %d ms",
                    System.currentTimeMillis() - s));
        } catch (IOException e) {
            System.err.println(String.format("%s load failure!", MAIN_DICT));
        } finally {
            try {
                if (null != is)
                    is.close();
            } catch (IOException e) {
                System.err.println(String.format("%s close failure!", MAIN_DICT));
            }
        }
    }


    private String addWord(String word) {
        if (null != word && !"".equals(word.trim())) {
            String key = word.trim().toLowerCase();
            _dict.fillSegment(key.toCharArray());
            return key;
        } else
            return null;
    }


    public void loadUserDict(File userDict) {
        loadUserDict(userDict, Charset.forName("UTF-8"));
    }


    public void loadUserDict(File userDict, Charset charset) {
        InputStream is;
        try {
            is = new FileInputStream(userDict);
        } catch (FileNotFoundException e) {
            System.err.println(String.format("could not find %s", userDict.getAbsolutePath()));
            return;
        }
        try {
            @SuppressWarnings("resource")
            BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
            long s = System.currentTimeMillis();
            int count = 0;
            while (br.ready()) {
                String line = br.readLine();
                String[] tokens = line.split("[\t ]+");

                if (tokens.length < 2)
                    continue;

                String word = tokens[0];
                double freq = Double.valueOf(tokens[1]);
                word = addWord(word);
                freqs.put(word, Math.log(freq / total));
                count++;
            }
            System.out.println(String.format("user dict %s load finished, tot words:%d, time elapsed:%dms",
                    userDict.getAbsolutePath(), count, System.currentTimeMillis() - s));
        } catch (IOException e) {
            System.err.println(String.format("%s: load user dict failure!", userDict.getAbsolutePath()));
        } finally {
            try {
                if (null != is)
                    is.close();
            } catch (IOException e) {
                System.err.println(String.format("%s close failure!", userDict.getAbsolutePath()));
            }
        }
    }


    public DictSegment getTrie() {
        return this._dict;
    }


    public boolean containsWord(String word) {
        return freqs.containsKey(word);
    }


    public Double getFreq(String key) {
        if (containsWord(key))
            return freqs.get(key);
        else
            return minFreq;
    }
}
