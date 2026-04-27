package servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**
 *
 * @author aida.mujcinovic
 */

@WebServlet(name = "RegisterPolyAIContact", urlPatterns = {"/RegisterPolyAIContact"})
public class RegisterPolyAIContact extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(RegisterPolyAIContact.class);

    private static final String POST_URL = ConfigReader.getProperty("POST_URL");
    private static final String USERNAME = ConfigReader.getProperty("USERNAME");
    private static final String PASSWORD = ConfigReader.getProperty("PASSWORD");
    private static final String CONTACT_ID = ConfigReader.getProperty("CONTACT_ID");
    private static final String PROXY = ConfigReader.getProperty("PROXY");
    private static final String PROXY_PORT = ConfigReader.getProperty("PROXY_PORT");
    private static final String TARGET = ConfigReader.getProperty("TARGET");
    private static final String TRUSTSTORE_PATH = ConfigReader.getProperty("certificatesTrustStorePathCLOUD");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        logger.info("Servlet pokrenut");
        logger.info("POST_URL: {}", POST_URL);
        logger.info("Proxy: {}:{}", PROXY, PROXY_PORT);
        logger.info("TrustStore path: {}", TRUSTSTORE_PATH);

        response.setContentType("application/json;charset=UTF-8");

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"code\":1, \"description\":\"NOK - Authorization header nedostaje\"}");
                return;
            }

            String base64Credentials = authHeader.substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String[] values = credentials.split(":", 2);

            if (values.length != 2 || !USERNAME.equals(values[0]) || !PASSWORD.equals(values[1])) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"code\":1, \"description\":\"NOK - Pogrešni korisnički podaci\"}");
                logger.warn("Neuspješna autentifikacija");
                return;
            }

            
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (sb.length() == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"code\":1,\"description\":\"NOK - Prazan JSON body\"}");
                return;
            }

            JSONObject bodyJson = new JSONObject(sb.toString());

            String botContactId = bodyJson.optString("voiceBotContactId", null);
            String clientPhone = bodyJson.optString("clientPhoneNumber", null);
            String contactStart = bodyJson.optString("startTime", null);
            String cmwContactId = bodyJson.optString("cmwContactId", null);
            String ccServiceNumber = bodyJson.optString("ccServiceNumber", null);

            if (isEmpty(botContactId) || isEmpty(clientPhone) || isEmpty(contactStart)
                    || isEmpty(cmwContactId) || isEmpty(ccServiceNumber)) {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"code\":1,\"description\":\"NOK - Nedostaju parametri\"}");
                return;
            }

            LocalDateTime contactStartTime = LocalDateTime.parse(contactStart, FORMATTER);
            
            if (contactStartTime.isAfter(LocalDateTime.now())) {
            logger.warn("Uneseni datum je u budućnosti: {}", contactStartTime);

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
            "{\"code\":1,\"description\":\"NOK - Datum ne može biti u budućnosti\"}"
              );
              return;
            }

            String result = createIncident(
                    botContactId, clientPhone, cmwContactId, ccServiceNumber, contactStartTime
            );

            response.getWriter().write(result);

        } catch (Exception e) {
            logger.error("Greška u servisu", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"code\":1,\"description\":\"NOK - " + e.getMessage() + "\"}");
        }
    }

    private String createIncident(
            String botContactId,
            String clientPhone,
            String cmwContactId,
            String ccServiceNumber,
            LocalDateTime contactStartTime
    ) {

        logger.info("Create incident start");

        
        if (!isEmpty(TRUSTSTORE_PATH)) {
            System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH.trim());
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        }

        logger.info("Truststore aktivan: {}", System.getProperty("javax.net.ssl.trustStore"));

        String queryUrl = POST_URL + "incidents";

        // 🌐 Proxy config
        RequestConfig config;
        if (!isEmpty(PROXY) && !isEmpty(PROXY_PORT)) {
            HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));
            config = RequestConfig.custom().setProxy(proxy).build();
            logger.info("Koristim proxy: {}:{}", PROXY, PROXY_PORT);
        } else {
            config = RequestConfig.custom().build();
            logger.info("Proxy se ne koristi");
        }

        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .build()) {

            JSONObject root = new JSONObject();
            root.put("primaryContact", new JSONObject().put("id", Integer.parseInt(CONTACT_ID)));
            root.put("channel", new JSONObject().put("lookupName", "Telefon"));

            JSONObject customFields = new JSONObject();
            JSONObject co = new JSONObject();

            co.put("virtualbot_init", true);
            co.put("bot_contact_id", botContactId);
            co.put("Dolazni_poziv", clientPhone);
            co.put("cmwContactId", cmwContactId);
            co.put("ccServiceNumber", ccServiceNumber);
            co.put("contact_start_time", contactStartTime.format(FORMATTER));

            customFields.put("CO", co);
            root.put("customFields", customFields);

            HttpPost post = new HttpPost(queryUrl);
            post.setConfig(config);
            post.addHeader("Content-Type", "application/json");

            String auth = USERNAME + ":" + PASSWORD;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            post.addHeader("Authorization", "Basic " + encodedAuth);

            post.setEntity(new StringEntity(root.toString(), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {

                int code = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                logger.info("Oracle response code: {}", code);
                logger.info("Oracle response body: {}", body);

                if (code == 200 || code == 201) {
                    JSONObject json = new JSONObject(body);
                    return "{\"code\":0,\"incidentId\":" + json.getInt("id") + "}";
                } else {
                    return "{\"code\":1,\"description\":\"NOK: " + code + "\"}";
                }
            }

        } catch (javax.net.ssl.SSLHandshakeException e) {
            logger.error("SSLHandshakeException - certifikat problem", e);
            return "{\"code\":1,\"description\":\"SSL handshake failed\"}";

        } catch (javax.net.ssl.SSLException e) {
            logger.error("SSLException - SSL konfiguracija problem", e);
            return "{\"code\":1,\"description\":\"SSL error\"}";

        } catch (java.net.UnknownHostException e) {
            logger.error("UnknownHost - DNS problem", e);
            return "{\"code\":1,\"description\":\"Unknown host\"}";

        } catch (java.net.ConnectException e) {
            logger.error("Connection failed - proxy/firewall", e);
            return "{\"code\":1,\"description\":\"Connection failed\"}";

        } catch (Exception e) {
            logger.error("Opšta greška (cloud/API)", e);
            return "{\"code\":1,\"description\":\"General error\"}";
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}