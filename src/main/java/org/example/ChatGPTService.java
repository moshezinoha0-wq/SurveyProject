package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChatGPTService {
    public static List<AppState.Question> generateSurvey(String topic) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        String prompt = "Create a survey about '" + topic + "'. Return ONLY a valid JSON array. The array must contain 1 to 3 objects. Each object must have a 'question' string, and an 'options' array containing 2 to 4 strings. Do not return any other text.";

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-3.5-turbo");
        requestBody.add("messages", messages);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + AppState.CHATGPT_API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            String jsonContent = jsonResponse.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

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
        }
    }
}
