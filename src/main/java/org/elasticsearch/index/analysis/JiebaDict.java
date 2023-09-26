package org.elasticsearch.index.analysis;


import com.huaban.analysis.jieba.WordDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.env.Environment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JiebaDict {
    private static final Logger logger = LogManager.getLogger(JiebaDict.class);

    private static JiebaDict singleton;
    private static long lastModified = 0;
    private static ByteArrayOutputStream remoteDict = null;

    public static JiebaDict init(Environment environment) {
        if (singleton == null) {
            synchronized (JiebaDict.class) {
                if (singleton == null) {

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            logger.info("start to load remote dict");
                            boolean remoteChange = loadRemoteDic(environment);
                            logger.info("start to load local dict");
                            WordDictionary.reload(environment.pluginsFile().resolve("jieba/dic").toFile(), remoteChange, remoteDict);
//                            WordDictionary.getInstance()
//                                    .init(environment.pluginsFile().resolve("jieba/dic").toFile());
                            logger.info("end load local dict");
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


    private static boolean loadRemoteDic(Environment environment) {
        SpecialPermission.check();
        return AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> loadRemoteDicUnprivileged(environment));
    }

    private static boolean loadRemoteDicUnprivileged(Environment environment) {
        Properties properties = new Properties();
        try {
            properties.load(Files.newInputStream(environment.pluginsFile().resolve("jieba/jieba.cfg.properties").toFile().toPath()));
            Object remoteDic = properties.getOrDefault("remote.ext.dic", "");
            String remoteUrl = remoteDic.toString();
            if(!remoteUrl.startsWith("http")){
                logger.info("remote dic url invalid remoteUrl:{}", remoteUrl);
                System.out.println("remote dic url invalid remoteUrl:" + remoteUrl);
                return false;
            }
            URL url = new URL(remoteUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);  // 设置连接超时时间为2秒
            connection.setReadTimeout(5000);   // 设置读取超时时间为5秒

            String lastModifiedHeader = connection.getHeaderField("Last-Modified");
            if(Objects.isNull(lastModifiedHeader)){
                logger.info("remote dic header not Last-Modified");
                System.out.println("remote dic header not Last-Modified");
                return false;
            }
            long remoteLastModified = 0;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(lastModifiedHeader, formatter);
                remoteLastModified = zonedDateTime.toInstant().getEpochSecond()*1000;
            }catch (Exception ignore){}

            if(lastModified == remoteLastModified){
                return false;
            }
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = inputStream.read(buf)) != -1) {
                byteArrayOutputStream.write(buf,0, len);
            }
            byteArrayOutputStream.flush();
            synchronized (JiebaDict.class){
                lastModified = remoteLastModified;
                remoteDict = byteArrayOutputStream;
            }
            return true;
        } catch (Exception e) {
            logger.error("load remote dict err", e);
            System.err.println(e);
        }
        return false;
    }
}
