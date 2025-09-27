package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class QuizResultsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        boolean passed = getIntent().getBooleanExtra("passed", false);
        int total = getIntent().getIntExtra("totalScore", 0);
        int max = getIntent().getIntExtra("maxScore", 18);
        int critical = getIntent().getIntExtra("criticalScore", 0);
        tv.setText((passed ? "Aprobado" : "No aprobado") + "\nPuntaje: " + total + "/" + max + "\nCrítico: " + critical);
        setContentView(tv);
    }
}
