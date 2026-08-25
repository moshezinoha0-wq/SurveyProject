package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainApp extends JFrame {

    private static final int MIN_COMMUNITY_MEMBERS = 3;

    private DefaultTableModel communityModel;
    private DefaultTableModel surveyModel;
    private JLabel lblCommunityCount;

    private JLabel lblTimerDisplay;
    private JLabel lblSurveyDetails;
    private JLabel lblPhaseBadge;
    private JProgressBar visualCountdownBar;

    private JTabbedPane mainTabs;
    private JPanel historyContainerPanel; // מיכל יחיד לכל הסקרים שהסתיימו
    private JLabel lblEmptyHistoryNotice;
    private int finishedSurveyCounter = 1;

    private JComboBox<Integer> comboQCount;
    private JSpinner spinManualDelay;
    private JPanel questionsContainer;
    private List<JTextField> manualQuestionFields = new ArrayList<>();
    private List<JTextField> manualOptionsFields = new ArrayList<>();

    private JTextField txtAITopic;
    private JSpinner spinAIDelay;

    private SurveyBot bot;
    private Timer workflowTimer;
    private long targetEndTimeMillis;
    private long totalPhaseDurationSecs;
    private boolean isDelayPhase = false;

    public MainApp() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        setTitle("מערכת ניהול סקרים בטלגרם");
        setSize(1150, 800);
        setMinimumSize(new Dimension(980, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(14, 14));
        getContentPane().setBackground(new Color(241, 245, 249));

        bot = new SurveyBot();

        // 1. כותרת עליונה
        RoundedPanel headerPanel = new RoundedPanel(16, new Color(30, 41, 59));
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JLabel lblTitle = new JLabel("מערכת לניהול סקרים בטלגרם");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(Color.WHITE);
        headerPanel.add(lblTitle, BorderLayout.WEST);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setOpaque(false);
        topContainer.setBorder(new EmptyBorder(12, 16, 0, 16));
        topContainer.add(headerPanel, BorderLayout.CENTER);
        add(topContainer, BorderLayout.NORTH);

        // 2. פאנל קהילה
        communityModel = new DefaultTableModel(new String[]{"שם", "Username", "הצטרפות"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable communityTable = createStyledTable(communityModel, false);

        lblCommunityCount = new JLabel("חברים בקהילה: 0");
        lblCommunityCount.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCommunityCount.setForeground(new Color(51, 65, 85));

        JPanel communityHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        communityHeader.setOpaque(false);
        communityHeader.add(lblCommunityCount);

        RoundedPanel communityPanel = createCardPanel("הקהילה הגלובלית");
        communityPanel.add(communityHeader, BorderLayout.NORTH);
        communityPanel.add(createStyledScrollPane(communityTable), BorderLayout.CENTER);

        // 3. לשוניות ראשיות (ארבע לשוניות קבועות)
        mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        mainTabs.addTab("מעקב סקר פעיל", createActiveSurveyPanel());
        mainTabs.addTab("היסטוריית סקרים", createHistorySurveyPanel()); // טאב קבוע יחיד לכל ההיסטוריה
        mainTabs.addTab("יצירת סקר ידני", createManualSurveyPanel());
        mainTabs.addTab("יצירת סקר עם AI", createAISurveyPanel());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainTabs, communityPanel);
        splitPane.setResizeWeight(0.64);
        splitPane.setDividerSize(8);
        splitPane.setOpaque(false);
        splitPane.setBorder(new EmptyBorder(0, 16, 16, 16));

        add(splitPane, BorderLayout.CENTER);

        Timer syncTimer = new Timer(1000, e -> updateCommunityData());
        syncTimer.start();
    }

    private JPanel createActiveSurveyPanel() {
        RoundedPanel card = createCardPanel("לוח מעקב בזמן אמת");

        surveyModel = new DefaultTableModel(new String[]{"שם", "התקדמות", "סטטוס"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable surveyTable = createStyledTable(surveyModel, true);

        RoundedPanel timerBadgePanel = new RoundedPanel(16, new Color(248, 250, 252));
        timerBadgePanel.setLayout(new BorderLayout(10, 10));
        timerBadgePanel.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel topTimerRow = new JPanel(new BorderLayout());
        topTimerRow.setOpaque(false);

        lblPhaseBadge = new JLabel("מצב: ממתין");
        lblPhaseBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPhaseBadge.setForeground(new Color(100, 116, 139));

        lblTimerDisplay = new JLabel("00:00");
        lblTimerDisplay.setFont(new Font("Consolas", Font.BOLD, 26));
        lblTimerDisplay.setForeground(new Color(100, 116, 139));
        lblTimerDisplay.setHorizontalAlignment(SwingConstants.CENTER);

        JButton btnCancel = new JButton("איפוס סקר");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancel.setFocusPainted(false);
        btnCancel.addActionListener(e -> stopAndResetSurvey(true));

        topTimerRow.add(lblPhaseBadge, BorderLayout.WEST);
        topTimerRow.add(lblTimerDisplay, BorderLayout.CENTER);
        topTimerRow.add(btnCancel, BorderLayout.EAST);

        visualCountdownBar = new JProgressBar(0, 100);
        visualCountdownBar.setValue(0);
        visualCountdownBar.setPreferredSize(new Dimension(100, 12));
        visualCountdownBar.setBackground(new Color(226, 232, 240));
        visualCountdownBar.setForeground(new Color(100, 116, 139));
        visualCountdownBar.setUI(new BasicProgressBarUI());

        lblSurveyDetails = new JLabel("אין סקר פעיל כרגע");
        lblSurveyDetails.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblSurveyDetails.setForeground(new Color(71, 85, 105));

        JPanel bottomTimerRow = new JPanel(new BorderLayout(0, 6));
        bottomTimerRow.setOpaque(false);
        bottomTimerRow.add(visualCountdownBar, BorderLayout.NORTH);
        bottomTimerRow.add(lblSurveyDetails, BorderLayout.SOUTH);

        timerBadgePanel.add(topTimerRow, BorderLayout.NORTH);
        timerBadgePanel.add(bottomTimerRow, BorderLayout.SOUTH);

        JPanel activeContentPanel = new JPanel(new BorderLayout(0, 12));
        activeContentPanel.setOpaque(false);
        activeContentPanel.add(timerBadgePanel, BorderLayout.NORTH);
        activeContentPanel.add(createStyledScrollPane(surveyTable), BorderLayout.CENTER);

        card.add(activeContentPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createHistorySurveyPanel() {
        RoundedPanel card = createCardPanel("היסטוריית סקרים שבוצעו");

        historyContainerPanel = new JPanel();
        historyContainerPanel.setLayout(new BoxLayout(historyContainerPanel, BoxLayout.Y_AXIS));
        historyContainerPanel.setOpaque(false);

        lblEmptyHistoryNotice = new JLabel("עדיין לא בוצעו סקרים במערכת.");
        lblEmptyHistoryNotice.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblEmptyHistoryNotice.setForeground(new Color(100, 116, 139));
        lblEmptyHistoryNotice.setAlignmentX(Component.CENTER_ALIGNMENT);

        historyContainerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        historyContainerPanel.add(lblEmptyHistoryNotice);

        JScrollPane scrollPane = new JScrollPane(historyContainerPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private void startSurveyWorkflow(AppState.ActiveSurvey newSurvey, int delayMinutes) {
        AppState.currentSurvey = newSurvey;
        mainTabs.setSelectedIndex(0);

        if (delayMinutes > 0) {
            isDelayPhase = true;
            totalPhaseDurationSecs = delayMinutes * 60L;
            targetEndTimeMillis = System.currentTimeMillis() + (totalPhaseDurationSecs * 1000L);

            lblPhaseBadge.setText("⏳ שלב 1: השהיית שליחה");
            lblPhaseBadge.setForeground(new Color(217, 119, 6));
            visualCountdownBar.setForeground(new Color(245, 158, 11));
            lblSurveyDetails.setText("הודעות טרם נשלחו. סופר לאחור עד לשידור בטלגרם...");

            runTimerLoop();
        } else {
            startActiveSurveyPhase();
        }
    }

    private void startActiveSurveyPhase() {
        isDelayPhase = false;
        totalPhaseDurationSecs = 5 * 60L;
        targetEndTimeMillis = System.currentTimeMillis() + (totalPhaseDurationSecs * 1000L);

        lblPhaseBadge.setText("🚀 שלב 2: סקר פעיל באוויר");
        lblPhaseBadge.setForeground(new Color(16, 185, 129));
        visualCountdownBar.setForeground(new Color(16, 185, 129));
        lblSurveyDetails.setText("הודעות נשלחו! סופר לאחור עד לסגירת הסקר...");

        if (AppState.currentSurvey != null) {
            AppState.currentSurvey.isPending = false;
            AppState.currentSurvey.startTimeMillis = System.currentTimeMillis();
        }

        sendSurveyToParticipants(AppState.currentSurvey);
        runTimerLoop();
    }

    private void runTimerLoop() {
        if (workflowTimer != null && workflowTimer.isRunning()) {
            workflowTimer.stop();
        }

        workflowTimer = new Timer(1000, e -> {
            long remainingSecs = Math.max(0, (targetEndTimeMillis - System.currentTimeMillis()) / 1000);

            long mins = remainingSecs / 60;
            long secs = remainingSecs % 60;
            lblTimerDisplay.setText(String.format("%02d:%02d", mins, secs));

            int progressPercent = (int) (((double) remainingSecs / totalPhaseDurationSecs) * 100);
            visualCountdownBar.setValue(progressPercent);

            if (isDelayPhase) {
                lblTimerDisplay.setForeground(new Color(217, 119, 6));
                if (remainingSecs <= 0) {
                    startActiveSurveyPhase();
                }
            } else {
                if (remainingSecs <= 60) {
                    lblTimerDisplay.setForeground(new Color(225, 29, 72));
                    visualCountdownBar.setForeground(new Color(225, 29, 72));
                } else {
                    lblTimerDisplay.setForeground(new Color(16, 185, 129));
                }

                if (AppState.currentSurvey != null && !AppState.currentSurvey.reminderSent && remainingSecs <= 120) {
                    sendRemindersToParticipants(AppState.currentSurvey);
                    AppState.currentSurvey.reminderSent = true;
                }

                updateSurveyProgressInTable();

                if (remainingSecs <= 0) {
                    ((Timer) e.getSource()).stop();
                    finishSurveyAutomatically();
                }
            }
        });

        workflowTimer.start();
    }

    private JPanel createManualSurveyPanel() {
        RoundedPanel card = createCardPanel("בניית סקר חדש");
        JPanel mainLayout = new JPanel(new BorderLayout(12, 12));
        mainLayout.setOpaque(false);

        RoundedPanel topBar = new RoundedPanel(12, new Color(248, 250, 252));
        topBar.setLayout(new FlowLayout(FlowLayout.RIGHT, 20, 10));

        JLabel lblQCount = new JLabel("כמות שאלות:");
        lblQCount.setFont(new Font("Segoe UI", Font.BOLD, 13));
        comboQCount = new JComboBox<>(new Integer[]{1, 2, 3});
        comboQCount.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel lblDelay = new JLabel("השהייה בשליחה (בדקות):");
        lblDelay.setFont(new Font("Segoe UI", Font.BOLD, 13));
        spinManualDelay = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
        spinManualDelay.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        topBar.add(spinManualDelay);
        topBar.add(lblDelay);
        topBar.add(comboQCount);
        topBar.add(lblQCount);

        questionsContainer = new JPanel();
        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        questionsContainer.setOpaque(false);

        comboQCount.addActionListener(e -> renderQuestionFields());
        renderQuestionFields();

        JScrollPane questionsScroll = new JScrollPane(questionsContainer);
        questionsScroll.setBorder(null);
        questionsScroll.setOpaque(false);
        questionsScroll.getViewport().setOpaque(false);

        AnimatedRoundedButton btnSubmit = new AnimatedRoundedButton("הפעל סקר", new Color(30, 41, 59), new Color(15, 23, 42));
        btnSubmit.addActionListener(e -> executeManualSurveyCreation());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.add(btnSubmit);

        mainLayout.add(topBar, BorderLayout.NORTH);
        mainLayout.add(questionsScroll, BorderLayout.CENTER);
        mainLayout.add(bottomPanel, BorderLayout.SOUTH);

        card.add(mainLayout, BorderLayout.CENTER);
        return card;
    }

    private void renderQuestionFields() {
        questionsContainer.removeAll();
        manualQuestionFields.clear();
        manualOptionsFields.clear();

        int qCount = (Integer) comboQCount.getSelectedItem();

        for (int i = 1; i <= qCount; i++) {
            RoundedPanel qCard = new RoundedPanel(12, new Color(248, 250, 252));
            qCard.setLayout(new GridLayout(4, 1, 6, 6));
            qCard.setBorder(new EmptyBorder(12, 16, 12, 16));

            JLabel lblQ = new JLabel("שאלה " + i + ":");
            lblQ.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblQ.setForeground(new Color(30, 41, 59));

            JTextField txtQ = createStyledTextField("הזן את נוסח השאלה...");
            JLabel lblOpts = new JLabel("תשובות אפשריות (מופרדות בפסיק, 2-4 תשובות):");
            lblOpts.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            JTextField txtOpts = createStyledTextField("תשובה 1, תשובה 2");

            manualQuestionFields.add(txtQ);
            manualOptionsFields.add(txtOpts);

            qCard.add(lblQ);
            qCard.add(txtQ);
            qCard.add(lblOpts);
            qCard.add(txtOpts);

            questionsContainer.add(qCard);
            questionsContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    private JPanel createAISurveyPanel() {
        RoundedPanel card = createCardPanel("יצירת סקר חכם באמצעות ChatGPT");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTopic = new JLabel("נושא הסקר:");
        lblTopic.setFont(new Font("Segoe UI", Font.BOLD, 13));
        txtAITopic = createStyledTextField("לדוגמה: טכנולוגיה, ספורט, אוכל...");

        JLabel lblDelay = new JLabel("השהייה בשליחה (בדקות):");
        lblDelay.setFont(new Font("Segoe UI", Font.BOLD, 13));
        spinAIDelay = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));

        AnimatedRoundedButton btnSubmit = new AnimatedRoundedButton("צור והפעל סקר AI", new Color(37, 99, 235), new Color(29, 78, 216));
        btnSubmit.addActionListener(e -> executeAISurveyCreation());

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        form.add(lblTopic, gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        form.add(txtAITopic, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        form.add(lblDelay, gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        form.add(spinAIDelay, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.insets = new Insets(24, 12, 12, 12);
        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrapper.setOpaque(false);
        btnWrapper.add(btnSubmit);
        form.add(btnWrapper, gbc);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private void executeManualSurveyCreation() {
        if (AppState.community.size() < MIN_COMMUNITY_MEMBERS) {
            JOptionPane.showMessageDialog(this, "נדרשים לפחות " + MIN_COMMUNITY_MEMBERS + " חברים בקהילה להפעלת סקר.", "שגיאה", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (AppState.currentSurvey != null) {
            JOptionPane.showMessageDialog(this, "יש כבר סקר פעיל או מושהה במערכת.", "שגיאה", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int qCount = (Integer) comboQCount.getSelectedItem();
        int delayMins = (Integer) spinManualDelay.getValue();

        AppState.ActiveSurvey newSurvey = new AppState.ActiveSurvey();
        for (int i = 0; i < qCount; i++) {
            String qText = manualQuestionFields.get(i).getText().trim();
            String[] optsArr = manualOptionsFields.get(i).getText().split(",");

            if (qText.isEmpty() || qText.equals("הזן את נוסח השאלה...")) {
                JOptionPane.showMessageDialog(this, "אנא הזן נוסח תקין לשאלה " + (i + 1), "שגיאה", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> options = new ArrayList<>();
            for (String opt : optsArr) {
                if (!opt.trim().isEmpty()) options.add(opt.trim());
            }

            if (options.size() < 2 || options.size() > 4) {
                JOptionPane.showMessageDialog(this, "שאלה " + (i + 1) + " חייבת להכיל בין 2 ל-4 תשובות.", "שגיאה", JOptionPane.ERROR_MESSAGE);
                return;
            }

            newSurvey.questions.add(new AppState.Question(qText, options));
        }

        setupSurveyParticipants(newSurvey);
        startSurveyWorkflow(newSurvey, delayMins);

        // איפוס שדות השאלות והתשובות למצב התחלתי נקי
        renderQuestionFields();
    }

    private void executeAISurveyCreation() {
        if (AppState.community.size() < MIN_COMMUNITY_MEMBERS) {
            JOptionPane.showMessageDialog(this, "נדרשים לפחות " + MIN_COMMUNITY_MEMBERS + " חברים בקהילה להפעלת סקר.", "שגיאה", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (AppState.currentSurvey != null) {
            JOptionPane.showMessageDialog(this, "יש כבר סקר פעיל במערכת.", "שגיאה", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String topic = txtAITopic.getText().trim();
        if (topic.isEmpty() || topic.startsWith("לדוגמה:")) {
            JOptionPane.showMessageDialog(this, "אנא הזן נושא לסקר.", "שגיאה", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int delayMins = (Integer) spinAIDelay.getValue();

        try {
            AppState.ActiveSurvey newSurvey = new AppState.ActiveSurvey();
            newSurvey.questions = ChatGPTService.generateSurvey(topic);
            setupSurveyParticipants(newSurvey);
            startSurveyWorkflow(newSurvey, delayMins);

            // איפוס שדה הנושא בחזרה לטקסט ברירת המחדל
            txtAITopic.setText("לדוגמה: טכנולוגיה, ספורט, אוכל...");
            txtAITopic.setForeground(Color.GRAY);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "שגיאה ביצירת סקר AI: " + ex.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupSurveyParticipants(AppState.ActiveSurvey newSurvey) {
        newSurvey.participants.addAll(AppState.community);
        for (AppState.CommunityUser u : newSurvey.participants) {
            newSurvey.userProgress.put(u.chatId, 0);
        }
        for (int i = 0; i < newSurvey.questions.size(); i++) {
            newSurvey.results.add(new HashMap<>());
        }
    }

    private void updateSurveyProgressInTable() {
        if (AppState.currentSurvey == null) return;

        AppState.ActiveSurvey survey = AppState.currentSurvey;
        int totalQuestions = survey.questions.size();
        int completedCount = 0;

        surveyModel.setRowCount(0);
        for (AppState.CommunityUser u : survey.participants) {
            int answered = survey.userProgress.getOrDefault(u.chatId, 0);

            String status;
            if (answered == totalQuestions) {
                status = "🟢 השלים";
                completedCount++;
            } else if (answered > 0) {
                status = "🟡 בתהליך";
            } else {
                status = "🔴 טרם ענה";
            }

            surveyModel.addRow(new Object[]{u.firstName, answered + "/" + totalQuestions, status});
        }

        lblSurveyDetails.setText(String.format("משתתפים: %d | השלימו: %d", survey.participants.size(), completedCount));

        if (completedCount == survey.participants.size() && completedCount > 0) {
            if (workflowTimer != null) workflowTimer.stop();
            finishSurveyAutomatically();
        }
    }

    private void finishSurveyAutomatically() {
        if (AppState.currentSurvey == null) return;

        AppState.ActiveSurvey survey = AppState.currentSurvey;
        survey.isFinished = true;

        if (lblEmptyHistoryNotice != null) {
            historyContainerPanel.remove(lblEmptyHistoryNotice);
        }

        RoundedPanel finishedSurveyCard = buildFinishedSurveyCard(survey, finishedSurveyCounter++);
        historyContainerPanel.add(finishedSurveyCard, 0);
        historyContainerPanel.add(Box.createRigidArea(new Dimension(0, 14)), 1);

        historyContainerPanel.revalidate();
        historyContainerPanel.repaint();

        // איפוס הלוח ללא שליחת הודעת ביטול למשתמשים
        stopAndResetSurvey(false);

        mainTabs.setSelectedIndex(1);
    }

    private RoundedPanel buildFinishedSurveyCard(AppState.ActiveSurvey survey, int surveyId) {
        RoundedPanel card = new RoundedPanel(12, new Color(248, 250, 252));
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        int totalParticipants = survey.participants.size();
        int completedCount = 0;
        for (AppState.CommunityUser u : survey.participants) {
            if (survey.userProgress.getOrDefault(u.chatId, 0) == survey.questions.size()) {
                completedCount++;
            }
        }

        JLabel lblTitle = new JLabel("📋 סקר שהסתיים #" + surveyId + " | משתתפים: " + completedCount + "/" + totalParticipants);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(15, 23, 42));

        JPanel qContainer = new JPanel();
        qContainer.setLayout(new BoxLayout(qContainer, BoxLayout.Y_AXIS));
        qContainer.setOpaque(false);

        for (int i = 0; i < survey.questions.size(); i++) {
            AppState.Question q = survey.questions.get(i);
            Map<String, Integer> qRes = survey.results.get(i);
            int totalVotesForQ = qRes.values().stream().mapToInt(Integer::intValue).sum();

            JLabel lblQ = new JLabel("שאלה " + (i + 1) + ": " + q.text);
            lblQ.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblQ.setForeground(new Color(51, 65, 85));
            qContainer.add(lblQ);
            qContainer.add(Box.createRigidArea(new Dimension(0, 4)));

            for (String opt : q.options) {
                int votes = qRes.getOrDefault(opt, 0);
                int pct = totalVotesForQ > 0 ? (votes * 100) / totalVotesForQ : 0;

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);

                JLabel lblOpt = new JLabel(opt + " (" + votes + " הצבעות)");
                lblOpt.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                lblOpt.setPreferredSize(new Dimension(200, 20));

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue(pct);
                bar.setStringPainted(true);
                bar.setString(pct + "%");
                bar.setFont(new Font("Segoe UI", Font.BOLD, 10));
                bar.setForeground(new Color(37, 99, 235));
                bar.setBackground(new Color(226, 232, 240));
                bar.setUI(new BasicProgressBarUI());

                row.add(lblOpt, BorderLayout.WEST);
                row.add(bar, BorderLayout.CENTER);

                qContainer.add(row);
                qContainer.add(Box.createRigidArea(new Dimension(0, 4)));
            }
            qContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(qContainer, BorderLayout.CENTER);

        return card;
    }

    private void stopAndResetSurvey(boolean isManualCancel) {
        if (workflowTimer != null && workflowTimer.isRunning()) {
            workflowTimer.stop();
        }

        // שליחת הודעת ביטול לטלגרם אך ורק אם האיפוס בוצע ידנית בלחיצה על הכפתור
        if (isManualCancel && AppState.currentSurvey != null && AppState.currentSurvey.userMessageIds != null) {
            for (Map.Entry<Long, Integer> entry : AppState.currentSurvey.userMessageIds.entrySet()) {
                long chatId = entry.getKey();
                int messageId = entry.getValue();
                try {
                    bot.cancelSurveyMessage(chatId, messageId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        AppState.currentSurvey = null;
        isDelayPhase = false;
        lblPhaseBadge.setText("מצב: ממתין");
        lblPhaseBadge.setForeground(new Color(100, 116, 139));
        lblTimerDisplay.setText("00:00");
        lblTimerDisplay.setForeground(new Color(100, 116, 139));
        visualCountdownBar.setValue(0);
        visualCountdownBar.setForeground(new Color(100, 116, 139));
        lblSurveyDetails.setText("אין סקר פעיל כרגע");
        surveyModel.setRowCount(0);
    }

    private void sendSurveyToParticipants(AppState.ActiveSurvey survey) {
        if (survey.questions == null || survey.questions.isEmpty()) return;
        for (AppState.CommunityUser user : survey.participants) {
            try {
                int msgId = bot.sendQuestionToUser(user.chatId, 0, survey.questions.get(0));
                if (msgId != -1) {
                    survey.userMessageIds.put(user.chatId, msgId); // שמירת מזהה ההודעה
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendRemindersToParticipants(AppState.ActiveSurvey survey) {
        if (survey == null) return;
        int totalQuestions = survey.questions.size();
        for (AppState.CommunityUser user : survey.participants) {
            int answered = survey.userProgress.getOrDefault(user.chatId, 0);
            if (answered < totalQuestions) {
                try {
                    bot.sendReminderToUser(user.chatId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateCommunityData() {
        communityModel.setRowCount(0);
        for (AppState.CommunityUser u : AppState.community) {
            communityModel.addRow(new Object[]{u.firstName, u.username, u.joinTime});
        }
        lblCommunityCount.setText("חברים בקהילה: " + AppState.community.size());
    }

    private JTextField createStyledTextField(String placeholder) {
        JTextField tf = new JTextField(placeholder);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setForeground(Color.GRAY);
        tf.setBorder(new CompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (tf.getText().equals(placeholder)) {
                    tf.setText("");
                    tf.setForeground(Color.BLACK);
                }
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (tf.getText().isEmpty()) {
                    tf.setText(placeholder);
                    tf.setForeground(Color.GRAY);
                }
            }
        });
        return tf;
    }

    private RoundedPanel createCardPanel(String title) {
        RoundedPanel panel = new RoundedPanel(16, Color.WHITE);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(15, 23, 42));
        panel.add(titleLabel, BorderLayout.BEFORE_FIRST_LINE);
        return panel;
    }

    private JTable createStyledTable(DefaultTableModel model, boolean applyStatusColors) {
        JTable table = new JTable(model);
        table.setRowHeight(38);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(241, 245, 249));
        table.getTableHeader().setForeground(new Color(51, 65, 85));
        table.setSelectionBackground(new Color(224, 231, 255));
        table.setShowGrid(false);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.CENTER);

                if (applyStatusColors && column == 2 && value != null) {
                    String status = value.toString();
                    if (status.contains("השלים")) {
                        c.setForeground(new Color(22, 101, 52));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("בתהליך")) {
                        c.setForeground(new Color(180, 83, 9));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("טרם ענה")) {
                        c.setForeground(new Color(225, 29, 72));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                } else {
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });
        return table;
    }

    private JScrollPane createStyledScrollPane(JTable table) {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(new Color(241, 245, 249), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        return scrollPane;
    }

    static class RoundedPanel extends JPanel {
        private int cornerRadius;
        private Color backgroundColor;

        public RoundedPanel(int radius, Color bgColor) {
            super();
            this.cornerRadius = radius;
            this.backgroundColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(backgroundColor != null ? backgroundColor : getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
            g2.setColor(new Color(226, 232, 240));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
            g2.dispose();
        }
    }

    static class AnimatedRoundedButton extends JButton {
        private final Color normalBg;
        private final Color hoverBg;

        public AnimatedRoundedButton(String text, Color normalBg, Color hoverBg) {
            super(text);
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;

            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(210, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getModel().isRollover() ? hoverBg : normalBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setColor(getForeground());
            g2.setFont(getFont());

            int stringWidth = fm.stringWidth(getText());
            int stringHeight = fm.getAscent();
            g2.drawString(getText(), (getWidth() - stringWidth) / 2, (getHeight() + stringHeight) / 2 - 2);

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MainApp app = new MainApp();
            botsApi.registerBot(app.bot);

            SwingUtilities.invokeLater(() -> app.setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}