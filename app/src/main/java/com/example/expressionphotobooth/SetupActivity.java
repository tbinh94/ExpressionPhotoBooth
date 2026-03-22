package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.adapter.ConceptAdapter;
import com.example.expressionphotobooth.domain.model.Concept;
import com.example.expressionphotobooth.domain.model.Frame;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {

    private MaterialButton btnNext;
    private SessionRepository sessionRepository;

    private RecyclerView rvConcepts;
    private ConceptAdapter conceptAdapter;
    private int selectedFrameResId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();

        btnNext = findViewById(R.id.btnNext);
        rvConcepts = findViewById(R.id.rvConcepts);
        MaterialButton btnBack = findViewById(R.id.btnBack);

        setupRecyclerView();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnBack.setOnClickListener(v -> {
            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnBack.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    finish();
                }
            });
        });

        btnNext.setOnClickListener(v -> {
            if (selectedFrameResId == -1) {
                Toast.makeText(this, "Please select a frame first!", Toast.LENGTH_SHORT).show();
                return;
            }

            Animation press = AnimationUtils.loadAnimation(this, R.anim.btn_press);
            btnNext.startAnimation(press);
            press.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    int photoCount = 4;
                    
                    // Lấy session hiện tại hoặc tạo mới
                    SessionState session = sessionRepository.getSession();
                    if (session == null) session = new SessionState();
                    
                    session.setPhotoCount(photoCount);
                    session.setSelectedFrameResId(selectedFrameResId);
                    session.setEditState(new EditState());
                    sessionRepository.saveSession(session);
                    
                    Intent intent = new Intent(SetupActivity.this, MainActivity.class);
                    intent.putExtra(IntentKeys.EXTRA_PHOTO_COUNT, photoCount);
                    startActivity(intent);
                }
            });
        });
    }

    private void setupRecyclerView() {
        rvConcepts.setLayoutManager(new LinearLayoutManager(this));
        List<Concept> mockData = createMockData();
        conceptAdapter = new ConceptAdapter(mockData, frame -> {
            selectedFrameResId = frame.getImageResId();
        });
        rvConcepts.setAdapter(conceptAdapter);
    }

    private List<Concept> createMockData() {
        List<Concept> concepts = new ArrayList<>();
        List<Frame> summerFrames = new ArrayList<>();
        // Đảm bảo R.drawable.sample_frame là hợp lệ (có file sample_frame.jpg/png)
        summerFrames.add(new Frame(1, R.drawable.sample_frame));
        summerFrames.add(new Frame(2, R.drawable.sample_frame));
        summerFrames.add(new Frame(3, R.drawable.sample_frame));
        concepts.add(new Concept("1 Summer Concept", summerFrames));

        List<Frame> cortisFrames = new ArrayList<>();
        cortisFrames.add(new Frame(4, R.drawable.sample_frame));
        cortisFrames.add(new Frame(5, R.drawable.sample_frame));
        concepts.add(new Concept("2 Cortis Concept", cortisFrames));
        return concepts;
    }
}
