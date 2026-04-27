/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 *
 * @author aida.mujcinovic
 */
public class ConfigReader {

    private static Properties properties = new Properties();

    static {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Nije pronađen config.properties fajl");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Greška pri učitavanju konfiguracije: " + e.getMessage());
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getApiKey() {
        return properties.getProperty("API_KEY");
    }

    public static String getProxy() {
        return properties.getProperty("PROXY");
    }
    
    public static String getTarget() {
        return properties.getProperty("TARGET");
    }
    
    public static String getCertificatesTrustStorePath() {
        return properties.getProperty("certificatesTrustStorePath");
    }
    
    public static String getCertificatesTrustStorePathCLOUD() {
        return properties.getProperty("certificatesTrustStorePathCLOUD");
    }
}
