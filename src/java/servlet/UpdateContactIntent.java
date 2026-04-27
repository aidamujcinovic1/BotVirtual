package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Base64;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
@WebServlet(name = "UpdateContactIntent", urlPatterns = {"/UpdateContactIntent"})
public class UpdateContactIntent extends HttpServlet {

    static {
        System.setProperty("log4j2.configurationFile", UpdateContactIntent.class.getClassLoader().getResource("log4j2.xml").toString());
    }

    private static final Logger logger = LogManager.getLogger(UpdateContactIntent.class);
    private static final String POST_URL = ConfigReader.getProperty("POST_URL");
    private static final String USERNAME = ConfigReader.getProperty("USERNAME");
    private static final String PASSWORD = ConfigReader.getProperty("PASSWORD");
    //private static final String API_KEY = ConfigReader.getProperty("API_KEY");
    
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
        logger.info("servlet pokrenut");
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
        String botContactId = jsonObject.optString("voiceBotContactId", null);
        String intent = jsonObject.optString("intent", null);

        logger.info("Pristigao novi zahtjev iu UpdateContactIntent!");
        logger.info("Pristigao voiceBotContactId " + botContactId);
        logger.info("Pristigao intent " + intent);

	logger.info("POST_URL: {}", POST_URL);
        logger.info("Proxy: {}:{}", PROXY, PROXY_PORT);
        logger.info("TrustStore path: {}", certificatesTrustStorePathCLOUD);

        if (botContactId == null || botContactId.isEmpty() || intent == null || intent.isEmpty()) {
            logger.info("{\"code\":1, \"description\":\"NOK - Nedostaju parametri\"}");
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - Nedostaju parametri\"}");
            return;
        }

        try {
            String updateResponse = update_contact_intent(botContactId, intent);
            response.getWriter().write(updateResponse);
            logger.info("Uspješan odgovor API-ja: {}", updateResponse);
        } catch (Exception e) {
            logger.info("Greska:",e);
            response.getWriter().write("{\"code\":1, \"description\":\"NOK - " + e.getMessage() + "\"}");
        }
    }

    private String update_contact_intent(String botContactId, String intent) {

    logger.info("UPDATE_CONTACT_INTENT (update_contact_intent)");

    if (!isEmpty(certificatesTrustStorePathCLOUD)) {
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
    }

    logger.info("Truststore postavljen: {}", System.getProperty("javax.net.ssl.trustStore"));

    String queryUrl = POST_URL + "queryResults/?query=select%20id%20from%20incidents%20where%20CustomFields.CO.bot_contact_id%3D%27" + botContactId + "%27";

    logger.info("Pozivam Oracle API URL: {}", queryUrl);

    HttpHost target = new HttpHost(TARGET.trim(), 443, "https");
    HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

    RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();

    logger.info("Proxy kreiran: {}:{}", PROXY, PROXY_PORT);

    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        HttpGet get = new HttpGet(queryUrl);

        get.addHeader("Accept", "application/json");

        String auth = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes("UTF-8"));

        get.addHeader("Authorization", "Basic " + auth);

        logger.info("Authorization header postavljen");

        try (CloseableHttpResponse response = httpClient.execute(target, get)) {

            int status = response.getStatusLine().getStatusCode();

            String body = EntityUtils.toString(response.getEntity());

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

            return send_update_request(incidentId, intent);

        }

    } catch (Exception e) {

        logger.error("Greška u update_contact_intent", e);

        return "{\"code\":1,\"description\":\"NOK - " + e.getMessage() + "\"}";
    }
}

    private String send_update_request(String incidentId, String intent) {

    logger.info("UPDATE_CONTACT_INTENT (send_update_request)");

    if (!isEmpty(certificatesTrustStorePathCLOUD)) {
        System.setProperty("javax.net.ssl.trustStore", certificatesTrustStorePathCLOUD.trim());
    }

    logger.info("Truststore postavljen: {}", System.getProperty("javax.net.ssl.trustStore"));

    String updateUrl = POST_URL + "incidents/" + incidentId;

    logger.info("Oracle update URL: {}", updateUrl);

    HttpHost target = new HttpHost(TARGET.trim(), 443, "https");
    HttpHost proxy = new HttpHost(PROXY.trim(), Integer.parseInt(PROXY_PORT.trim()));

    RequestConfig config = RequestConfig.custom()
            .setProxy(proxy)
            .build();

    logger.info("Proxy kreiran: {}:{}", PROXY, PROXY_PORT);

    try (CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .build()) {

        HttpPost post = new HttpPost(updateUrl);

        post.addHeader("X-HTTP-Method-Override", "PATCH");
        post.addHeader("Accept", "application/json");
        post.addHeader("Content-Type", "application/json");

        String auth = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes("UTF-8"));

        post.addHeader("Authorization", "Basic " + auth);

        logger.info("Authorization header postavljen");

        JSONObject jsonPayload = new JSONObject();

        JSONObject product = new JSONObject();
        product.put("lookupName", intent);

        jsonPayload.put("product", product);
        jsonPayload.put("subject", intent);

        StringEntity entity = new StringEntity(jsonPayload.toString(), "UTF-8");

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(target, post)) {

            int status = response.getStatusLine().getStatusCode();

            String body = EntityUtils.toString(response.getEntity());

            logger.info("Oracle PATCH response code: {}", status);
            logger.info("Oracle PATCH response body: {}", body);

            if (status == 200 || status == 204) {

                return "{\"code\":0,\"description\":\"OK - Uspješno ažuriranje incidenta\",\"incidentId\":\"" + incidentId + "\"}";

            } else {

                return "{\"code\":1,\"description\":\"NOK\",\"Error\":" + body + "}";

            }

        }

    } catch (Exception e) {

        logger.error("Greška u send_update_request", e);

        return "{\"code\":1,\"description\":\"NOK - " + e.getMessage() + "\"}";
    }
}
}
