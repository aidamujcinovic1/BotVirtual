package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author aida.mujcinovic
 */

@WebServlet(name = "RegisterPolyAIChatContact", urlPatterns = {"/RegisterPolyAIChatContact"})
public class RegisterPolyAIChatContact extends HttpServlet {

    private static final String POST_URL = ConfigReader.getProperty("POST_URL");
    private static final String USERNAME = ConfigReader.getProperty("USERNAME");
    private static final String PASSWORD = ConfigReader.getProperty("PASSWORD");
    private static final String CONTACT_ID = ConfigReader.getProperty("CONTACT_ID");
    private static final String API_KEY = ConfigReader.getProperty("API_KEY");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        String apiKey = request.getHeader("API_KEY");

        if (apiKey == null || !apiKey.equals(API_KEY)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(
                    "{\"code\":1,\"description\":\"NOK - Neovlašten pristup - neispravan API ključ\"}");
            return;
        }

        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        try {

            JSONObject jsonObject = new JSONObject(sb.toString());

            String chatBotContactId = jsonObject.optString("chatBotContactId", null);
            String startTimeString = jsonObject.optString("startTime", null);
            String endTimeString = jsonObject.optString("endTime", null);
            String transcript = jsonObject.optString("transcript", null);
            String email = jsonObject.optString("email", null);

            if (chatBotContactId == null || chatBotContactId.isEmpty()
                    || startTimeString == null || startTimeString.isEmpty()
                    || endTimeString == null || endTimeString.isEmpty()
                    || transcript == null || transcript.isEmpty()) {

                response.getWriter().write(
                        "{\"code\":1,\"description\":\"NOK - Nedostaju parametri\"}");
                return;
            }

            LocalDateTime startTime = LocalDateTime.parse(startTimeString, FORMATTER);
            LocalDateTime endTime = LocalDateTime.parse(endTimeString, FORMATTER);

            LocalDateTime now = LocalDateTime.now();

            if (startTime.isAfter(now)) {
                response.getWriter().write(
                        "{\"code\":1,\"description\":\"NOK - Start time ne može biti u budućnosti\"}");
                return;
            }

            if (endTime.isAfter(now)) {
                response.getWriter().write(
                        "{\"code\":1,\"description\":\"NOK - End time ne može biti u budućnosti\"}");
                return;
            }

            if (endTime.isBefore(startTime)) {
                response.getWriter().write(
                        "{\"code\":1,\"description\":\"NOK - End time ne može biti prije start time\"}");
                return;
            }

            String result = registerPolyAIChatContact(
                    chatBotContactId,
                    startTime,
                    endTime,
                    transcript, 
                    email);

            response.getWriter().write(result);

        } catch (Exception e) {
            response.getWriter().write(
                    "{\"code\":1,\"description\":\"NOK - " + e.getMessage() + "\"}");
        }
    }

    private String registerPolyAIChatContact(
            String chatBotContactId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String transcript,
            String email) throws IOException {

        String queryUrl = POST_URL + "incidents/";

        HttpURLConnection conn = null;

        try {

            URL url = new URL(queryUrl);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String auth = USERNAME + ":" + PASSWORD;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setDoOutput(true);

            JSONObject root = new JSONObject();

            JSONObject primaryContact = new JSONObject();
            primaryContact.put("id", Integer.parseInt(CONTACT_ID));
            root.put("primaryContact", primaryContact);

            JSONObject channel = new JSONObject();
            channel.put("lookupName", "Chat");
            root.put("channel", channel);

            JSONObject assignedTo = new JSONObject();

            JSONObject account = new JSONObject();
            account.put("lookupName", "Virtualni Agent");
            assignedTo.put("account", account);

            JSONObject staffGroup = new JSONObject();
            staffGroup.put("lookupName", "Agenti");
            assignedTo.put("staffGroup", staffGroup);

            root.put("assignedTo", assignedTo);

            JSONObject severity = new JSONObject();
            severity.put("id", 2);
            root.put("severity", severity);

            JSONObject customFields = new JSONObject();
            JSONObject coFields = new JSONObject();

            coFields.put("bot_contact_id", chatBotContactId);
            coFields.put("contact_start_time", startTime.format(FORMATTER));
            coFields.put("contact_end_time", endTime.format(FORMATTER));
            coFields.put("Dolazni_email", email);
            coFields.put("virtualbot_init", true);

            customFields.put("CO", coFields);
            root.put("customFields", customFields);

            JSONObject statusWithType = new JSONObject();
            JSONObject status = new JSONObject();
            status.put("lookupName", "Razriješeno");
            statusWithType.put("status", status);
            root.put("statusWithType", statusWithType);

            JSONObject thread = new JSONObject();

            JSONObject threadChannel = new JSONObject();
            threadChannel.put("id", 3);

            JSONObject entryType = new JSONObject();
            entryType.put("id", 4);

            thread.put("channel", threadChannel);
            thread.put("entryType", entryType);
            thread.put("text", transcript);

            JSONArray threads = new JSONArray();
            threads.put(thread);

            root.put("threads", threads);

            OutputStream os = conn.getOutputStream();
            os.write(root.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == 200 || responseCode == 201) {

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));

                StringBuilder responseContent = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    responseContent.append(inputLine);
                }

                in.close();

                JSONObject jsonResponse = new JSONObject(responseContent.toString());

                int incidentId = jsonResponse.getInt("id");

                return String.format(
                        "{\"code\":0,\"description\":\"OK\",\"incidentId\":%d}",
                        incidentId);

            } else {

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()));

                StringBuilder error = new StringBuilder();
                String lineErr;

                while ((lineErr = br.readLine()) != null) {
                    error.append(lineErr);
                }

                return "{\"code\":1,\"description\":\"NOK\",\"error\":"
                        + JSONObject.quote(error.toString()) + "}";

            }

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
