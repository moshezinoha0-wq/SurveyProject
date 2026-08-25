package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChatGPTService {
    public static List<AppState.Question> generateSurvey(String topic) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String prompt = "Create a survey about '" + topic + "'. Return ONLY a valid JSON array. The array must contain 1 to 3 objects. Each object must have a 'question' string, and an 'options' array containing 2 to 4 strings. Do not return any other text.";

        // ניקוי תווים נסתרים (שקורים כשמעתיקים טקסט) ובניית הכתובת בצורה בטוחה
        String cleanBaseUrl = "https://shaitest-production-3066.up.railway.app/api-request".replaceAll("[^\\x20-\\x7E]", "");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(cleanBaseUrl).newBuilder();
        urlBuilder.addQueryParameter("token", AppState.SHAI_API_TOKEN != null ? AppState.SHAI_API_TOKEN.trim() : "");
        urlBuilder.addQueryParameter("text", prompt); // הספרייה מקודדת את הרווחים אוטומטית

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String responseBody = response.body().string();

            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            if (!jsonResponse.has("value")) {
                throw new IOException("לא נמצא שדה value בתשובת השרת.");
            }

            String jsonContent = jsonResponse.get("value").getAsString();

            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.replace("```json", "").replace("```", "").trim();
            } else if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.replace("```", "").trim();
            }

            JsonArray questionsArray = gson.fromJson(jsonContent, JsonArray.class);
            List<AppState.Question> questions = new ArrayList<>();

            for (int i = 0; i < questionsArray.size(); i++) {
                JsonObject qObj = questionsArray.get(i).getAsJsonObject();
                String text = qObj.get("question").getAsString();
                JsonArray optsArray = qObj.getAsJsonArray("options");
                List<String> options = new ArrayList<>();
                for (int j = 0; j < optsArray.size(); j++) {
                    options.add(optsArray.get(j).getAsString());
                }
                questions.add(new AppState.Question(text, options));
            }
            return questions;
        } catch (Exception e) {
            throw new IOException("שגיאה בפענוח: " + e.getMessage());
        }
    }
}