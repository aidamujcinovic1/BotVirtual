/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import static org.apache.http.util.TextUtils.isEmpty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author aida.mujcinovic
 */
@WebServlet(name = "UpdateContactClient", urlPatterns = {"/UpdateContactClient"})
public class UpdateContactClient extends HttpServlet {
    
    static {
        System.setProperty("log4j2.configurationFile", UpdateContactClient.class.getClassLoader().getResource("log4j2.xml").toString());
    }

    private static final Logger logger = LogManager.getLogger(UpdateContactClient.class);
    private static final String POST_URL = ConfigReader.getProperty("POST_URL");
    private static final String USERNAME = ConfigReader.getProperty("USERNAME");
    private static final String PASSWORD = ConfigReader.getProperty("PASSWORD");
    private static final String API_KEY = ConfigReader.getProperty("API_KEY");
    
    private static final String PROXY = ConfigReader.getProperty("PROXY");
    private static final String TARGET= ConfigReader.getProperty("TARGET");
    private static final String PROXY_PORT = ConfigReader.getProperty("PROXY_PORT");
    private static final String certificatesTrustStorePathCLOUD = ConfigReader.getProperty("certificatesTrustStorePathCLOUD");
    

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.info("update contact client servlet pokrenut");
        response.setContentType("application/json;charset=UTF-8");

        /*String apiKey = request.getHeader("API_KEY");
        if (apiKey == null || !apiKey.equals(API_KEY)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Neovlašten pristup - neispravan API ključ\"}");
            logger.info("Error: Neovlašten pristup - neispravan API ključ");
            return;
        }*/
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Authorization header nedostaje\"}");
            return;
        }

        String base64Credentials = authHeader.substring("Basic ".length());
        String credentials = new String(Base64.getDecoder().decode(base64Credentials));
        String[] values = credentials.split(":", 2);

        if (values.length != 2
                || !USERNAME.equals(values[0])
                || !PASSWORD.equals(values[1])) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Pogrešni korisnički podaci\"}");
            logger.info("Neuspješna Basic autentifikacija");
            return;
        }

        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String jsonBody = sb.toString();

        JSONObject jsonObject = new JSONObject(jsonBody);
        String botContactId = jsonObject.optString("bot_contact_id", null);
        String clientId = jsonObject.optString("client_id", null);
        
       
        logger.info("Pristigao novi zahtjev iu UpdateContactClient!");
        logger.info("Pristigao bot_contact_id " + botContactId);
        logger.info("Pristigao client_id " + clientId);
        
        logger.info("POST_URL: {}", POST_URL);
        logger.info("Proxy: {}:{}", PROXY, PROXY_PORT);
        logger.info("TrustStore path: {}", certificatesTrustStorePathCLOUD);

        if (botContactId == null || botContactId.isEmpty() || clientId == null || clientId.isEmpty()) {
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Nedostaju parametri\"}");
            logger.info("{\"code\":1, \"description\":\"NOK - Nedostaju parametri\"}");
            return;
        }
        try {
            String updateResponse = update_contact_client(botContactId, clientId);
            response.getWriter().write(updateResponse);
            logger.info("Uspješan odgovor API-ja: {}", updateResponse);
        } catch (Exception e) {
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - " + e.getMessage() + "\"}");
        }
    }

    private String update_contact_client(String botContactId, String clientId) throws IOException {
        String contactId = get_contact_id(clientId);
        if (contactId == null) {
            return "{\"code\":1, \"description\":\"NOK - Klijent nije pronađen\"}";
        }
        System.out.println(contactId);
        String incidentId = get_incident_id(botContactId);
        if (incidentId == null) {
            return "{\"code\":1, \"description\":\"NOK - Incident nije pronađen\"}";
        }
        System.out.println(incidentId);
        return send_update_request(incidentId, contactId);
    }

    private String get_contact_id(String clientId) {

    logger.info("update contact client - get_contact_id");

    if (!isEmpty(certificatesTrustStorePathCLOUD)) {
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
    }

    String queryUrl = POST_URL + "queryResults/?query=select%20id%20from%20contacts%20where%20CustomFields.CO.core_id%3D" + clientId;

    HttpHost target = new HttpHost(TARGET.trim(), 443, "https");
    HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

    RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();

    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        HttpGet get = new HttpGet(queryUrl);

        String auth = Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        get.addHeader("Authorization", "Basic " + auth);
        get.addHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(target, get)) {

            int status = response.getStatusLine().getStatusCode();

            if (status != 200 && status != 201) {
                return null;
            }

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            JSONObject jsonResponse = new JSONObject(body);
            JSONArray items = jsonResponse.getJSONArray("items");

            if (items.length() == 0 || items.getJSONObject(0).getJSONArray("rows").length() == 0) {
                return null;
            }

            return items.getJSONObject(0).getJSONArray("rows").getJSONArray(0).getString(0);
        }

    } catch (Exception e) {
        logger.error("GRESKA u konekciji prema Oracle servisu", e);
        return null;
    }
}

    private String get_incident_id(String botContactId) {

    logger.info("update contact client - get_incident_id");

    if (!isEmpty(certificatesTrustStorePathCLOUD)) {
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
    }

    String queryUrl = POST_URL +
            "queryResults/?query=select%20id%20from%20incidents%20where%20CustomFields.CO.bot_contact_id%3D%27"
            + botContactId + "%27";

    HttpHost target = new HttpHost(TARGET.trim(), 443, "https");
    HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

    RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();

    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        HttpGet get = new HttpGet(queryUrl);

        String auth = Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        get.addHeader("Authorization", "Basic " + auth);
        get.addHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(target, get)) {

            int status = response.getStatusLine().getStatusCode();

            if (status != 200 && status != 201) {
                return null;
            }

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            JSONObject jsonResponse = new JSONObject(body);
            JSONArray items = jsonResponse.getJSONArray("items");

            if (items.length() == 0 || items.getJSONObject(0).getJSONArray("rows").length() == 0) {
                return null;
            }

            return items.getJSONObject(0).getJSONArray("rows").getJSONArray(0).getString(0);
        }

    } catch (Exception e) {
        logger.error("GRESKA u konekciji prema Oracle servisu", e);
        return null;
    }
}


private String send_update_request(String incidentId, String contactId) {

    logger.info("UPDATE_CONTACT_client (send_update_request)");

    if (!isEmpty(certificatesTrustStorePathCLOUD)) {
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
    }

    String updateUrl = POST_URL + "incidents/" + incidentId;

    HttpHost target = new HttpHost(TARGET.trim(), 443, "https");
    HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

    RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();

    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        HttpPost post = new HttpPost(updateUrl);

        post.addHeader("X-HTTP-Method-Override", "PATCH");
        post.addHeader("Accept", "application/json");
        post.addHeader("Content-Type", "application/json");

        String auth = Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        post.addHeader("Authorization", "Basic " + auth);

        JSONObject jsonPayload = new JSONObject();
        JSONObject primaryContact = new JSONObject();

        primaryContact.put("id", Integer.parseInt(contactId));

        jsonPayload.put("primaryContact", primaryContact);

        post.setEntity(new StringEntity(jsonPayload.toString(), StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(target, post)) {

            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (status == 200 || status == 204) {
                return "{\"code\":0, \"description\":\"OK - Uspješno ažuriranje incidenta\", \"incidentId\":\"" + incidentId + "\"}";
            } else {
                return "{\"code\":1, \"description\":\"NOK\", \"Error\": " + body + "}";
            }
        }

    } catch (Exception e) {
        logger.error("Greška prilikom update incidenta", e);
        return "{\"code\":1, \"description\":\"NOK - " + e.getMessage() + "\"}";
    }
}
}
