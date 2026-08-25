package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class AppState {
    public static String TELEGRAM_BOT_TOKEN;
    public static String TELEGRAM_BOT_USERNAME;
    public static String SHAI_API_TOKEN;

    // טעינת ההגדרות מקובץ config.properties
    static {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
            TELEGRAM_BOT_TOKEN = properties.getProperty("TELEGRAM_BOT_TOKEN");
            TELEGRAM_BOT_USERNAME = properties.getProperty("TELEGRAM_BOT_USERNAME");
            SHAI_API_TOKEN = properties.getProperty("SHAI_API_TOKEN");
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
        }
    }

    public static List<CommunityUser> community = new ArrayList<>();
    public static ActiveSurvey currentSurvey = null;

    public static class CommunityUser {
        public long chatId;
        public String firstName;
        public String username;
        public String joinTime;

        public CommunityUser(long chatId, String firstName, String username, String joinTime) {
            this.chatId = chatId;
            this.firstName = firstName;
            this.username = username;
            this.joinTime = joinTime;
        }
    }

    public static class Question {
        public String text;
        public List<String> options;
        public Question(String text, List<String> options) {
            this.text = text;
            this.options = options;
        }
    }

    public static class ActiveSurvey {
        public List<Question> questions = new ArrayList<>();
        public List<CommunityUser> participants = new ArrayList<>();
        public Map<Long, Integer> userProgress = new HashMap<>();
        public Map<Long, Integer> userMessageIds = new HashMap<>();
        public List<Map<String, Integer>> results = new ArrayList<>();
        public boolean isPending = true;
        public boolean isFinished = false;
        public boolean reminderSent = false;
        public long startTimeMillis;
    }
}