package com.ibbe.util;

import java.io.IOException;
import java.util.Properties;

/**
 * populates properties into map.
 */
public class PropertiesUtil {

    private static Properties prop = new Properties();

    static {
        // Read properties file.
        try {
            prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(
                "application.properties"));
            //extra props not in file
            prop.setProperty("extra_property", "extra prop value");
        } catch (IOException e) {
            System.out.println("could not read properties from file itsybitso." + System.getProperty("env") + ".properties");
        }
    }

    public static String getProperty(String name) {
        return (String) prop.get(name);
    }

    public static void setProperty(String key, String value) {
        prop.setProperty(key, value);
    }

}
