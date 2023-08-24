package org.elasticsearch.index.analysis;


import com.huaban.analysis.jieba.WordDictionary;
import org.elasticsearch.env.Environment;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class JiebaDict {

    private static JiebaDict singleton;

    public static JiebaDict init(Environment environment) {
        if (singleton == null) {
            synchronized (JiebaDict.class) {
                if (singleton == null) {

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            System.out.println("start to load remote dict");
                            loadRemoteDic(environment);
                            System.out.println("start to load local dict");
                            WordDictionary.reload(environment.pluginsFile().resolve("jieba/dic").toFile());
//                            WordDictionary.getInstance()
//                                    .init(environment.pluginsFile().resolve("jieba/dic").toFile());
                        }
                    };

                    new Timer().scheduleAtFixedRate(task,1000, 60 * 1000);

                    singleton = new JiebaDict();
                    return singleton;
                }
            }
        }
        return singleton;
    }

    private static void loadRemoteDic(Environment environment) {
        Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(environment.pluginsFile().resolve("jieba/jieba.cfg.properties").toFile().toPath()));
            Object remoteDic = properties.getOrDefault("remote.ext.dic", "");
            String remoteUrl = remoteDic.toString();
            if(!remoteUrl.startsWith("http")){
                System.out.println("remote dic url invalid remoteUrl:" + remoteUrl);
                return;
            }
            URL url = new URL(remoteUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2000);  // 设置连接超时时间为2秒
            connection.setReadTimeout(5000);   // 设置读取超时时间为5秒
            String lastModifiedHeader = connection.getHeaderField("Last-Modified");
            if(Objects.isNull(lastModifiedHeader)){
                System.out.println("remote dic header not Last-Modified");
                return;
            }
            long remoteLastModified = 0;
            try {
                String pattern = "EEE, dd MMM yyyy HH:mm:ss z";
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                Date lastModifiedDate = format.parse(lastModifiedHeader);
                remoteLastModified = lastModifiedDate.getTime();
            }catch (Exception ignore){}
            File file = new File(environment.pluginsFile().resolve("jieba/dic").toFile().getAbsolutePath() + "/remote.ext.dic");
            long localLastModified = file.lastModified();
            if(localLastModified == remoteLastModified){
                return;
            }
            InputStream inputStream = connection.getInputStream();
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                byte[] buf = new byte[4096];
                while (inputStream.read(buf) != -1) {
                    fileOutputStream.write(buf);
                }
                fileOutputStream.flush();
            }
            file.setLastModified(remoteLastModified);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void main(String[] args) throws IOException {
        String remoteUrl = "https://apifox.com/apidoc/shared-7c812aad-8a60-4c09-8863-6d32f3242d16/doc-1669347";
        URL url = new URL(remoteUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(2000);  // 设置连接超时时间为2秒
        connection.setReadTimeout(5000);   // 设置读取超时时间为5秒
        long remoteLastModified = connection.getLastModified();
        System.out.println(remoteLastModified);
    }
}
