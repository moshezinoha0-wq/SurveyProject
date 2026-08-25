package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SurveyBot extends TelegramLongPollingBot {

    @Override
    public String getBotUsername() {
        return AppState.TELEGRAM_BOT_USERNAME;
    }

    @Override
    public void clearWebhook() {
        // עקיפת Timeout
    }

    @Override
    public String getBotToken() {
        return AppState.TELEGRAM_BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim().toLowerCase();
            long chatId = update.getMessage().getChatId();

            if (text.equals("/start") || text.equals("hi") || text.equals("היי")) {
                handleJoin(chatId, update.getMessage().getChat().getFirstName(), update.getMessage().getChat().getUserName());
            }
        }
        else if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            String queryId = update.getCallbackQuery().getId();

            handleAnswer(chatId, messageId, callData, queryId);
        }
    }

    private void handleJoin(long chatId, String firstName, String username) {
        for (AppState.CommunityUser u : AppState.community) {
            if (u.chatId == chatId) {
                sendMessage(chatId, "👋 היי " + firstName + ", אתה כבר רשום לקהילת הסקרים שלנו! כשיעלה סקר חדש נעדכן אותך כאן מיד.");
                return;
            }
        }

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        String displayUser = (username != null && !username.isBlank()) ? "@" + username : "ללא username";
        AppState.community.add(new AppState.CommunityUser(chatId, firstName, displayUser, time));

        // הודעת קבלת פנים מפורטת ומסבירה
        String welcomeMsg = "👋 **ברוך הבא לקהילת הסקרים, " + firstName + "!**\n\n" +
                "🤖 **מה הבוט הזה עושה?**\n" +
                "דרכנו תוכל להשתתף בסקרים קהילתיים בזמן אמת, להביע את דעתך ולהשפיע.\n\n" +
                "📌 **איך זה עובד?**\n" +
                "• כשיפתח סקר חדש, תבל לפה את השאלות עם כפתורי בחירה נוחים.\n" +
                "• הצבעתך נקלטת באופן מיידי ואוטומטי.\n" +
                "• לקראת סיום הסקר תשלח תזכורת במידה וטרם סיימת להשיב.\n\n" +
                "תודה שהצטרפת אלינו! 🚀";

        sendMessage(chatId, welcomeMsg);

        // הודעה מפורטת לכל חברי הקהילה
        for (AppState.CommunityUser u : AppState.community) {
            if (u.chatId != chatId) {
                sendMessage(u.chatId, "🎉 **חבר חדש הצטרף לקהילה!**\nתברכו את " + firstName + " (" + displayUser + ").\nאנחנו כעת " + AppState.community.size() + " חברים בקהילה.");
            }
        }
    }

    private void handleAnswer(long chatId, int messageId, String callData, String queryId) {
        if (AppState.currentSurvey == null || AppState.currentSurvey.isFinished || AppState.currentSurvey.isPending) {
            return;
        }

        String[] parts = callData.split("_");
        int qIndex = Integer.parseInt(parts[0]);
        int aIndex = Integer.parseInt(parts[1]);

        boolean inSurvey = AppState.currentSurvey.participants.stream().anyMatch(u -> u.chatId == chatId);
        if (!inSurvey) return;

        String answerText = AppState.currentSurvey.questions.get(qIndex).options.get(aIndex);
        Map<String, Integer> qResults = AppState.currentSurvey.results.get(qIndex);
        qResults.put(answerText, qResults.getOrDefault(answerText, 0) + 1);

        int currentProgress = AppState.currentSurvey.userProgress.getOrDefault(chatId, 0);
        int newProgress = currentProgress + 1;
        AppState.currentSurvey.userProgress.put(chatId, newProgress);

        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText("✅ **תשובתך נקלטה בהצלחה!**\nבחרת ב: \"" + answerText + "\" (שאלה " + (qIndex + 1) + " מתוך " + AppState.currentSurvey.questions.size() + ")");
        try {
            execute(edit);

            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallback = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answerCallback.setCallbackQueryId(queryId);
            execute(answerCallback);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        if (newProgress < AppState.currentSurvey.questions.size()) {
            int nextMsgId = sendQuestionToUser(chatId, newProgress, AppState.currentSurvey.questions.get(newProgress));
            AppState.currentSurvey.userMessageIds.put(chatId, nextMsgId); // עדכון מזהה ההודעה העדכנית
        }
    }

    public void sendMessage(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setParseMode("Markdown");
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendSurveyCanceledNotice(long chatId) {
        sendMessage(chatId, "⚠️ **הסקר הופסק!**\n\nהסקר שהיה פעיל במערכת הופסק על ידי המנהל ולא ניתן להמשיך לענות עליו.");
    }

    public void sendReminderToUser(long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("⏰ **תזכורת לסקר פעיל!**\n\n" +
                "נותרו 2 דקות בלבד לסיום הסקר.\n" +
                "אם טרם סיימת לענות על כל השאלות, אנא השלם אותן כעת כדי שקולך ייספר במדד הקהילתי! 📊");
        msg.setParseMode("Markdown");
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    // מתודה חדשה לעריכת הודעת הסקר הקיימת והחלפתה בהודעת ביטול
    public void cancelSurveyMessage(long chatId, int messageId) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText("⚠️ **הסקר הופסק!**\n\nהסקר שהיה פעיל במערכת הופסק על ידי המנהל ולא ניתן עוד לענות עליו.");
        edit.setParseMode("Markdown");
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // עדכון: החזרת מזהה ההודעה (int) בעת שליחת שאלה
    public int sendQuestionToUser(long chatId, int qIndex, AppState.Question q) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("📊 **סקר קהילתי פעיל** (שאלה " + (qIndex + 1) + " מתוך " + AppState.currentSurvey.questions.size() + ")\n\n" +
                "❓ *" + q.text + "*\n\n" +
                "👇 לחץ על אחת האפשרויות למטה כדי לרשום את הצבעתך:");
        msg.setParseMode("Markdown");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (int i = 0; i < q.options.size(); i++) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(q.options.get(i));
            btn.setCallbackData(qIndex + "_" + i);
            rowInline.add(btn);
            rowsInline.add(rowInline);
        }

        markupInline.setKeyboard(rowsInline);
        msg.setReplyMarkup(markupInline);

        try {
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = execute(msg);
            return sentMessage.getMessageId(); // החזרת מזהה ההודעה שנשלחה
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return -1;
        }
    }
}