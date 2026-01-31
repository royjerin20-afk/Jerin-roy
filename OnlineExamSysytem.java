import javax.swing.*;
import java.awt.Font;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

class DBConnection {
    private static final String DB_URL = "jdbc:sqlite:exam_system.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, "SQLite JDBC driver not found!");
        }
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database connection failed: " + e.getMessage());
            return null;
        }
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS exams (" +
                    "exam_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS questions (" +
                    "question_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "exam_id INTEGER NOT NULL," +
                    "question_text TEXT NOT NULL," +
                    "correct_index INTEGER NOT NULL," +
                    "difficulty TEXT NOT NULL," +
                    "FOREIGN KEY (exam_id) REFERENCES exams(exam_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS options (" +
                    "option_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "question_id INTEGER NOT NULL," +
                    "option_text TEXT NOT NULL," +
                    "FOREIGN KEY (question_id) REFERENCES questions(question_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS results (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "student_id INTEGER NOT NULL," +
                    "student_name TEXT NOT NULL," +
                    "exam_title TEXT NOT NULL," +
                    "score INTEGER NOT NULL," +
                    "total_marks INTEGER NOT NULL," +
                    "percentage REAL NOT NULL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

            System.out.println("✅ Database initialized successfully.");
            insertSampleExamData(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void insertSampleExamData(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM exams";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        int examId;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO exams (title) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "Java Programming Exam");
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            keys.next();
            examId = keys.getInt(1);
        }

        String[][] data = {
                {"Which of these is NOT a Java keyword?", "Easy", "1",
                        "static", "Boolean", "void", "class"},
                {"Which method is the entry point of a Java program?", "Easy", "0",
                        "main()", "start()", "run()", "init()"},
                {"Which data type is used to store decimal numbers in Java?", "Medium", "1",
                        "int", "double", "char", "boolean"},
                {"What is inheritance in Java?", "Medium", "1",
                        "Ability of an object to take many forms",
                        "Process where one class acquires properties of another",
                        "Hiding data from other classes", "A loop structure"},
                {"Which of these is used to handle exceptions in Java?", "Hard", "0",
                        "try-catch", "if-else", "for-loop", "switch"}
        };

        String qSql = "INSERT INTO questions (exam_id, question_text, correct_index, difficulty) VALUES (?, ?, ?, ?)";
        String oSql = "INSERT INTO options (question_id, option_text) VALUES (?, ?)";

        for (String[] row : data) {
            int questionId;
            try (PreparedStatement qStmt = conn.prepareStatement(qSql, Statement.RETURN_GENERATED_KEYS)) {
                qStmt.setInt(1, examId);
                qStmt.setString(2, row[0]);
                qStmt.setInt(3, Integer.parseInt(row[2]));
                qStmt.setString(4, row[1]);
                qStmt.executeUpdate();

                ResultSet qKey = qStmt.getGeneratedKeys();
                qKey.next();
                questionId = qKey.getInt(1);
            }

            try (PreparedStatement oStmt = conn.prepareStatement(oSql)) {
                for (int i = 3; i < row.length; i++) {
                    oStmt.setInt(1, questionId);
                    oStmt.setString(2, row[i]);
                    oStmt.addBatch();
                }
                oStmt.executeBatch();
            }
        }

        System.out.println("✅ Sample exam data inserted into database.");
    }
}

class Question {
    private String text;
    private List<String> options;
    private int correctAnswerIndex;

    public Question(String text, List<String> options, int correctAnswerIndex) {
        this.text = text;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getText() { return text; }
    public List<String> getOptions() { return options; }
    public boolean isCorrect(int choice) { return choice == correctAnswerIndex; }
}

class MCQQuestion extends Question {
    private int mark;
    private String difficultyLevel;

    public MCQQuestion(String text, List<String> options, int correctAnswerIndex, String difficultyLevel) {
        super(text, options, correctAnswerIndex);
        this.difficultyLevel = difficultyLevel;
        this.mark = switch (difficultyLevel.toLowerCase()) {
            case "easy" -> 1;
            case "medium" -> 2;
            case "hard" -> 3;
            default -> 1;
        };
    }

    public int getMark() { return mark; }
    public String getDifficultyLevel() { return difficultyLevel; }
}

class Exam {
    private String title;
    private List<Question> questions = new ArrayList<>();

    public Exam(String title) { this.title = title; }
    public void addQuestion(Question q) { questions.add(q); }
    public List<Question> getQuestions() { return questions; }
    public String getTitle() { return title; }

    public int getTotalMarks() {
        return questions.stream()
                .filter(q -> q instanceof MCQQuestion)
                .mapToInt(q -> ((MCQQuestion) q).getMark())
                .sum();
    }
}

class Student {
    private int id;
    private String name;

    public Student(int id, String name) { this.id = id; this.name = name; }
    public String getName() { return name; }
    public int getId() { return id; }
}

class Result {
    private Student student;
    private Exam exam;
    private int score;

    public Result(Student student, Exam exam, int score) {
        this.student = student;
        this.exam = exam;
        this.score = score;
    }

    public void display() {
        int totalMarks = exam.getTotalMarks();
        double percentage = totalMarks > 0 ? ((double) score / totalMarks) * 100 : 0.0;
        JOptionPane.showMessageDialog(null,
                "=== Examination Result ===\n" +
                        "Student: " + student.getName() + " (ID: " + student.getId() + ")\n" +
                        "Exam: " + exam.getTitle() + "\n" +
                        "Score: " + score + " / " + totalMarks + "\n" +
                        String.format("Percentage: %.2f%%", percentage),
                "Exam Result", JOptionPane.INFORMATION_MESSAGE);
    }

    public void saveToDatabase() {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                String sql = "INSERT INTO results (student_id, student_name, exam_title, score, total_marks, percentage) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    int totalMarks = exam.getTotalMarks();
                    double percentage = totalMarks > 0 ? ((double) score / totalMarks) * 100 : 0.0;

                    stmt.setInt(1, student.getId());
                    stmt.setString(2, student.getName());
                    stmt.setString(3, exam.getTitle());
                    stmt.setInt(4, score);
                    stmt.setInt(5, totalMarks);
                    stmt.setDouble(6, percentage);
                    stmt.executeUpdate();

                    System.out.println("✅ Result saved successfully.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

public class OnlineExamSystem extends JFrame {
    private Exam javaExam;
    private Student student;
    private int currentQuestionIndex = 0;
    private int score = 0;

    private JLabel questionLabel, difficultyLabel;
    private JRadioButton[] options = new JRadioButton[4];
    private ButtonGroup optionGroup = new ButtonGroup();
    private JButton nextButton;

    public OnlineExamSystem() {
        setTitle("Online Examination System");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setupStudentAndExam();
        initUI();
        loadQuestion(0);
    }

    private void setupStudentAndExam() {
        String name = "";
        while (name.isEmpty()) {
            name = JOptionPane.showInputDialog(this, "Enter your name:");
            if (name == null || name.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name is required!");
                name = "";
            }
        }

        Integer id = null;
        while (id == null) {
            String idStr = JOptionPane.showInputDialog(this, "Enter your ID (numbers only):");
            if (idStr == null) {
                JOptionPane.showMessageDialog(this, "Student ID is required!");
                continue;
            }
            try {
                id = Integer.parseInt(idStr.trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid numeric ID!");
            }
        }

        student = new Student(id, name);
        javaExam = loadExamFromDatabase("Java Programming Exam");
    }

    private Exam loadExamFromDatabase(String title) {
        Exam exam = new Exam(title);
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT q.question_id, q.question_text, q.correct_index, q.difficulty " +
                    "FROM questions q JOIN exams e ON q.exam_id = e.exam_id WHERE e.title = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int qid = rs.getInt("question_id");
                    String qText = rs.getString("question_text");
                    int correctIdx = rs.getInt("correct_index");
                    String diff = rs.getString("difficulty");

                    List<String> opts = new ArrayList<>();
                    try (PreparedStatement optStmt = conn.prepareStatement("SELECT option_text FROM options WHERE question_id = ?")) {
                        optStmt.setInt(1, qid);
                        ResultSet optRs = optStmt.executeQuery();
                        while (optRs.next()) {
                            opts.add(optRs.getString("option_text"));
                        }
                    }

                    exam.addQuestion(new MCQQuestion(qText, opts, correctIdx, diff));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return exam;
    }

    private void initUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        difficultyLabel = new JLabel();
        questionLabel = new JLabel();
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));

        panel.add(difficultyLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(questionLabel);

        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton();
            optionGroup.add(options[i]);
            panel.add(options[i]);
        }

        nextButton = new JButton("Next");
        nextButton.addActionListener(e -> handleNext());
        panel.add(Box.createVerticalStrut(20));
        panel.add(nextButton);

        add(panel);
    }

    private void loadQuestion(int index) {
        MCQQuestion q = (MCQQuestion) javaExam.getQuestions().get(index);
        difficultyLabel.setText("Difficulty: " + q.getDifficultyLevel() + " | Marks: " + q.getMark());
        questionLabel.setText((index + 1) + ". " + q.getText());

        List<String> opts = q.getOptions();
        for (int i = 0; i < options.length; i++) {
            if (i < opts.size()) {
                options[i].setText(opts.get(i));
                options[i].setVisible(true);
            } else {
                options[i].setVisible(false);
            }
            options[i].setSelected(false);
        }

        optionGroup.clearSelection();
        nextButton.setText(index == javaExam.getQuestions().size() - 1 ? "Finish" : "Next");
    }

    private void handleNext() {
        MCQQuestion q = (MCQQuestion) javaExam.getQuestions().get(currentQuestionIndex);
        int selected = -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].isSelected()) {
                selected = i;
                break;
            }
        }

        if (selected == -1) {
            JOptionPane.showMessageDialog(this, "Please select an answer!");
            return;
        }

        if (q.isCorrect(selected)) score += q.getMark();
        currentQuestionIndex++;

        if (currentQuestionIndex < javaExam.getQuestions().size()) {
            loadQuestion(currentQuestionIndex);
        } else {
            dispose();
            Result result = new Result(student, javaExam, score);
            result.display();
            result.saveToDatabase();
        }
    }

    public static void main(String[] args) {
        DBConnection.initializeDatabase();
        SwingUtilities.invokeLater(() -> new OnlineExamSystem().setVisible(true));
    }
}
