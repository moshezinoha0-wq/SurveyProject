package org.example;

import java.util.*;

public class AppState {
    // הכנס כאן את הטוקנים שלך!
    public static final String TELEGRAM_BOT_TOKEN = "8727080601:AAH3L2L0k0EeT9BmTbVyRu6E94R_Hebl3TI";
    public static final String TELEGRAM_BOT_USERNAME = "MosheZinoBot";
    public static final String CHATGPT_API_KEY = "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";

    // רשימת הקהילה הגלובלית
    public static List<CommunityUser> community = new ArrayList<>();

    // הסקר הפעיל כרגע (אם יש)
    public static ActiveSurvey currentSurvey = null;

    // מודלים של נתונים (Classes)
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
        public Map<Long, Integer> userMessageIds = new HashMap<>(); // לשמירת מזהה ההודעה של כל משתמש
        public List<Map<String, Integer>> results = new ArrayList<>();
        public boolean isPending = true;
        public boolean isFinished = false;
        public boolean reminderSent = false;
        public long startTimeMillis;
    }
}