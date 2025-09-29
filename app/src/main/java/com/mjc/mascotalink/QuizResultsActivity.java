package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizResultsActivity extends AppCompatActivity {

    private TextView tvResult, tvScore, tvCriticalScore, tvFeedback;
    private Button btnRetry, btnFinish;
    private LinearLayout feedbackContainer;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_results);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Firebase emuladores
        String host = "192.168.0.147";
        db = FirebaseFirestore.getInstance();
        try {
            db.useEmulator(host, 8080);
        } catch (IllegalStateException e) {
            // Emulator may already be set, ignore
        }

        mAuth = FirebaseAuth.getInstance();
        try {
            mAuth.useEmulator(host, 9099);
        } catch (IllegalStateException e) {
            // Emulator may already be set, ignore
        }

        TextView tvResultStatus = findViewById(R.id.tv_result_status);
        TextView tvResultScore = findViewById(R.id.tv_result_score);
        TextView tvResultMessage = findViewById(R.id.tv_result_message);
        TextView tvFeedbackHeader = findViewById(R.id.tv_feedback_header);
        LinearLayout feedbackContainer = findViewById(R.id.ll_feedback_container);
        Button btnContinue = findViewById(R.id.btn_continue);

        boolean passed = getIntent().getBooleanExtra("passed", false);
        int totalScore = getIntent().getIntExtra("totalScore", 0);
        int maxScore = getIntent().getIntExtra("maxScore", 18);
        ArrayList<Integer> incorrectIndices = getIntent().getIntegerArrayListExtra("incorrectIndices");

        tvResultScore.setText(String.format("Tu puntaje: %d de %d", totalScore, maxScore));

        if (passed) {
            tvResultStatus.setText("¡Aprobado!");
            tvResultStatus.setTextColor(ContextCompat.getColor(this, R.color.green_success));
            tvResultMessage.setText("¡Felicidades! Has demostrado tener los conocimientos necesarios para ser un excelente paseador.");
            btnContinue.setText("Continuar con el Registro");
        } else {
            tvResultStatus.setText("No Aprobado");
            tvResultStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red));
            tvResultMessage.setText("Necesitas repasar algunos conceptos clave. Revisa tus respuestas incorrectas a continuación.");
            btnContinue.setText("Volver a Intentar");
        }

        if (incorrectIndices != null && !incorrectIndices.isEmpty()) {
            tvFeedbackHeader.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int index : incorrectIndices) {
                QuestionBank.Question q = QuestionBank.QUESTIONS[index];
                View feedbackView = inflater.inflate(R.layout.item_quiz_feedback, feedbackContainer, false);

                TextView tvQuestion = feedbackView.findViewById(R.id.tv_feedback_question);
                TextView tvYourAnswer = feedbackView.findViewById(R.id.tv_feedback_your_answer);
                TextView tvCorrectAnswer = feedbackView.findViewById(R.id.tv_feedback_correct_answer);
                TextView tvExplanation = feedbackView.findViewById(R.id.tv_feedback_explanation);

                tvQuestion.setText(q.getText());
                // Note: We don't have the user's incorrect answer text, so we'll just label it as incorrect.
                tvYourAnswer.setText("Tu respuesta fue incorrecta.");
                tvCorrectAnswer.setText("Respuesta correcta: " + q.getOptions()[q.getCorrectAnswer()]);
                tvExplanation.setText("Explicación: " + q.getExplanation());

                feedbackContainer.addView(feedbackView);
            }
        }

        btnContinue.setOnClickListener(v -> {
            if (passed) {
                // If passed, go to the next step of the registration
                Intent intent = new Intent(this, PaseadorRegistroPaso5Activity.class);
                startActivity(intent);
                finish();
            } else {
                // If failed, go back to the quiz to try again
                Intent intent = new Intent(this, QuizActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}