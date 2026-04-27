/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author aida.mujcinovic
 */
@WebServlet(name = "RegisterPolyAIContactEnd", urlPatterns = {"/RegisterPolyAIContactEnd"})

public class RegisterPolyAIContactEnd extends HttpServlet {

    static {
        System.setProperty("log4j2.configurationFile", RegisterPolyAIContactEnd.class.getClassLoader().getResource("log4j2.xml").toString());
    }

    private static final Logger logger = LogManager.getLogger(RegisterPolyAIContactEnd.class);

    private static final String POST_URL = ConfigReader.getProperty("POST_URL");
    private static final String USERNAME = ConfigReader.getProperty("USERNAME");
    private static final String PASSWORD = ConfigReader.getProperty("PASSWORD");
    private static final String API_KEY = ConfigReader.getProperty("API_KEY");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private static final String PROXY = ConfigReader.getProperty("PROXY");
    private static final String PROXY_PORT = ConfigReader.getProperty("PROXY_PORT");
    private static final String TARGET= ConfigReader.getProperty("TARGET");
    private static final String certificatesTrustStorePathCLOUD = ConfigReader.getProperty("certificatesTrustStorePathCLOUD");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.info("registerpolyaicontactend servlet pokrenut");
        logger.info("POST_URL: {}", POST_URL);
        logger.info("Proxy: {}:{}", PROXY, PROXY_PORT);
        logger.info("TrustStore path: {}", certificatesTrustStorePathCLOUD);
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
        String botContactId = jsonObject.optString("vbotContactId", null);
        String contactEndTimeString = jsonObject.optString("vbotContactEndTime", null);
        String transcript = jsonObject.optString("conversationTranscript", null);
        String handoffReason = jsonObject.optString("handoffReason", null);
        String handoffNumber = jsonObject.optString("handoffNumber", null);
        LocalDateTime contactEndTime = LocalDateTime.parse(contactEndTimeString, FORMATTER);
        String conversationSummary = jsonObject.optString("conversationSummary", null);

        logger.info("Pristigao novi zahtjev iz RegisterPolyAIContactEnd");
        logger.info("Primljen vbotContactId: {}", botContactId);
        logger.info("Vrijeme završetka kontakta: {}", contactEndTime);
        logger.info("Primljen transcript: {}", transcript);
        logger.info("Primljen handoffReason: {}", handoffReason);
        logger.info("Primljen handoffNumber: {}", handoffNumber);
        logger.info("Primljen conversationSummary: {}", conversationSummary);
        
        
        if (botContactId == null || botContactId.isEmpty() || contactEndTime == null || transcript == null || transcript.isEmpty() || handoffReason.isEmpty() || handoffNumber.isEmpty() || conversationSummary.isEmpty()) {
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Nedostaju parametri\"}");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (contactEndTime.isAfter(now)) {
            logger.info("{\"code\":1, \"description\":\"NOK - Uneseni datum ne može biti u budućnosti\"}");
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Uneseni datum ne može biti u budućnosti\"}");
            return;
        }

        try {
            String updateResponse = register_polyAI_contact_end(botContactId, contactEndTime, transcript, handoffReason, handoffNumber, conversationSummary);
            logger.info("Uspješan odgovor API-ja: {}", updateResponse);
            response.getWriter().write(updateResponse);
        } catch (Exception e) {
            logger.error("Greška prilikom obrade", e);
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - " + e.toString() + "\"}");
        }
    }

    private String register_polyAI_contact_end(String botContactId, LocalDateTime contactEndTime, String transcript, String handoffReason, String handoffNumber, String conversationSummary) throws IOException {
        
        logger.info("register poly ai contact end");
        logger.info("Postavljam SSL truststore...");
        if(!isEmpty(certificatesTrustStorePathCLOUD)){
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
        }
        logger.info("Truststore postavljen: {}", System.getProperty("javax.net.ssl.trustStore"));
        
        String queryURL= POST_URL +  "queryResults/?query=select%20id%20from%20incidents%20where%20CustomFields.CO.bot_contact_id%3D%27" + botContactId + "%27";
        logger.info("Pozivam Oracle API URL: {}", queryURL);
        
        // target host
        HttpHost target = new HttpHost(TARGET.trim(), 443, "https");

        // proxy host
        HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

        RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();
        
        logger.info("Proxy kreiran: {}:{}", PROXY, PROXY_PORT);
        
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(config)
        .build();){
        
            
            HttpGet get = new HttpGet(queryURL);
            get.addHeader("Accept", "application/json");
            String auth = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            get.addHeader("Authorization", "Basic " + auth);
            logger.info("Authorization header postavljen za GET");

            try (CloseableHttpResponse response = httpClient.execute(target, get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.info("Oracle GET response code: {}", status);
                logger.info("Oracle GET response body: {}", body);

                if (status != 200 && status != 201) {
                    return "{\"code\":1,\"description\":\"NOK - Greška prilikom dohvatanja incidenta\"}";
                }

                JSONObject jsonResponse = new JSONObject(body);
                JSONArray rows = jsonResponse.getJSONArray("items").getJSONObject(0).getJSONArray("rows");

                if (rows.length() == 0) {
                    return "{\"code\":1,\"description\":\"NOK - Incident nije pronađen\"}";
                }

                String incidentId = rows.getJSONArray(0).getString(0);
                return send_update_request(incidentId, contactEndTime, transcript, handoffReason, handoffNumber, conversationSummary);

            }

        } catch (Exception e) {
            logger.error("Greška prilikom register_polyAI_contact_end", e);
            return "{\"code\":1,\"description\":\"NOK - " + e.getMessage() + "\"}";
        }

    }

    private String send_update_request(String incidentId, LocalDateTime contactEndTime, String transcript, String handoffReason, String handoffNumber,String conversationSummary) throws IOException {
        //String updateUrl = POST_URL + "incidents/" + incidentId;
        //HttpURLConnection connection = null;
        logger.info("register poly ai end - send update request {}", incidentId);
        logger.info("Postavljam SSL truststore...");
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
        logger.info("Truststore postavljen: {}", System.getProperty("javax.net.ssl.trustStore"));
        
        
        String updateUrl = POST_URL + "incidents/" + incidentId;
        logger.info("Update url {}", updateUrl);
        
        // target host
        HttpHost target = new HttpHost(TARGET.trim(), 443, "https");

        // proxy host
        HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

        RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();
        
        logger.info("Proxy kreiran: {}:{}", PROXY, PROXY_PORT);
        
    
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        // POST sa PATCH override
        HttpPost post = new HttpPost(updateUrl);
        post.addHeader("X-HTTP-Method-Override", "PATCH");
        post.addHeader("Accept", "application/json");
        post.addHeader("Content-Type", "application/json");
        String auth = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        post.addHeader("Authorization", "Basic " + auth);

        // JSON body
        JSONObject root = new JSONObject();

        // customFields
        JSONObject coFields = new JSONObject();
        coFields.put("handoffReason", handoffReason);
        coFields.put("handoffNumber", handoffNumber);
        coFields.put("contact_end_time", contactEndTime.format(FORMATTER));
        coFields.put("conversationSummary", conversationSummary);

        JSONObject customFields = new JSONObject();
        customFields.put("CO", coFields);
        root.put("customFields", customFields);

        // status
        JSONObject statusWithType = new JSONObject();
        JSONObject status1 = new JSONObject();
        status1.put("lookupName", "Razriješeno");
        statusWithType.put("status", status1);
        root.put("statusWithType", statusWithType);

        // threads
        JSONObject thread = new JSONObject();
        JSONObject channel = new JSONObject();
        channel.put("id", 3);
        JSONObject entryType = new JSONObject();
        entryType.put("id", 4);
        thread.put("channel", channel);
        thread.put("entryType", entryType);
        thread.put("text", transcript);
        JSONArray threadsArray = new JSONArray();
        threadsArray.put(thread);
        root.put("threads", threadsArray);

        // postavljanje entity
        post.setEntity(new StringEntity(root.toString(), StandardCharsets.UTF_8));
        logger.info("JSON za update incidenta:\n{}", root.toString(4));

        try (CloseableHttpResponse response = httpClient.execute(target, post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            logger.info("Oracle POST response code: {}", status);
            logger.info("Oracle POST response body: {}", body);

            if (status == 200 || status == 204) {
                return "{\"code\":0, \"description\":\"OK - Uspješno ažuriranje incidenta\", \"incidentId\":\"" + incidentId + "\"}";
            } else {
                return "{\"code\":1, \"description\":\"NOK\", \"Error\":" + body + "}";
            }
        }

    } catch (Exception e) {
        logger.error("Greška prilikom update incidenta", e);
        return "{\"code\":1, \"description\":\"NOK - " + e.getMessage() + "\"}";
    }}}
        
    


