package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizActivity extends AppCompatActivity {

    private TextView tvQuestion, tvProgress;
    private RadioGroup rgOptions;
    private RadioButton rb1, rb2, rb3, rb4;
    private Button btnNext;

    private int index = 0;
    private int totalScore = 0;
    private int criticalScore = 0;
    private final Map<String, Integer> categoryScores = new HashMap<>();
    private final List<Integer> answers = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tvQuestion = findViewById(R.id.tv_question);
        tvProgress = findViewById(R.id.tv_progress);
        rgOptions = findViewById(R.id.rg_options);
        rb1 = findViewById(R.id.rb1); rb2 = findViewById(R.id.rb2); rb3 = findViewById(R.id.rb3); rb4 = findViewById(R.id.rb4);
        btnNext = findViewById(R.id.btn_next);

        showQuestion();

        btnNext.setOnClickListener(v -> {
            if (!validateAnswer()) return;
            if (index < QuestionBank.QUESTIONS.length - 1) {
                index++;
                showQuestion();
            } else {
                finishQuiz();
            }
        });
    }

    private void showQuestion() {
        QuestionBank.Question q = QuestionBank.QUESTIONS[index];
        tvQuestion.setText(q.getText());
        tvProgress.setText((index + 1) + "/" + QuestionBank.QUESTIONS.length);
        rb1.setText(q.getOptions()[0]);
        rb2.setText(q.getOptions()[1]);
        rb3.setText(q.getOptions()[2]);
        rb4.setText(q.getOptions()[3]);
        rgOptions.clearCheck();
        btnNext.setText(index == QuestionBank.QUESTIONS.length - 1 ? "Finalizar" : "Siguiente");
    }

    private boolean validateAnswer() {
        int id = rgOptions.getCheckedRadioButtonId();
        if (id == -1) {
            Toast.makeText(this, "Por favor selecciona una respuesta", Toast.LENGTH_SHORT).show();
            return false;
        }
        int selected = (id == R.id.rb1) ? 0 : (id == R.id.rb2) ? 1 : (id == R.id.rb3) ? 2 : 3;
        answers.add(selected);
        QuestionBank.Question q = QuestionBank.QUESTIONS[index];
        if (selected == q.getCorrectAnswer()) {
            totalScore += q.getWeight();
            if (q.getCategory().equals("primeros_auxilios") || q.getCategory().equals("emergencias")) {
                criticalScore += q.getWeight();
            }
            categoryScores.put(q.getCategory(), categoryScores.getOrDefault(q.getCategory(), 0) + q.getWeight());
        }
        return true;
    }

    private void finishQuiz() {
        int maxScore = 18;
        boolean passed = totalScore >= 14;
        boolean criticalPassed = criticalScore >= 6;
        boolean overall = passed && criticalPassed;

        updateFirestoreScores(overall);

        Intent intent = new Intent(this, QuizResultsActivity.class);
        intent.putExtra("passed", overall);
        intent.putExtra("totalScore", totalScore);
        intent.putExtra("maxScore", maxScore);
        intent.putExtra("criticalScore", criticalScore);
        startActivity(intent);
        finish();
    }

    private void updateFirestoreScores(boolean passed) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Object> conocimientos = new HashMap<>();
        conocimientos.put("comportamiento_canino_score", categoryScores.getOrDefault("comportamiento", 0) * 33);
        conocimientos.put("primeros_auxilios_score", categoryScores.getOrDefault("primeros_auxilios", 0) * 17);
        conocimientos.put("manejo_emergencia_score", categoryScores.getOrDefault("emergencias", 0) * 17);

        FirebaseFirestore.getInstance().collection("paseadores").document(uid)
                .update("conocimientos", conocimientos,
                        "quiz_completado", true,
                        "quiz_aprobado", passed,
                        "quiz_score_total", totalScore,
                        "quiz_fecha", FieldValue.serverTimestamp())
                .addOnSuccessListener(v -> {})
                .addOnFailureListener(e -> {});
    }
}
