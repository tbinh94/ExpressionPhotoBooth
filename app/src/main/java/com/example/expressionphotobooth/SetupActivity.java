package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.adapter.ConceptAdapter;
import com.example.expressionphotobooth.data.repository.FrameStatsRepository;
import com.example.expressionphotobooth.domain.model.Concept;
import com.example.expressionphotobooth.domain.model.Frame;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.model.TopFrame;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.example.expressionphotobooth.utils.FrameConfig;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetupActivity extends AppCompatActivity {

    private MaterialButton btnNext;
    private SessionRepository sessionRepository;
    private FrameStatsRepository frameStatsRepository;

    private RecyclerView rvConcepts;
    private Frame selectedFrame;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();
        frameStatsRepository = new FrameStatsRepository();

        btnNext = findViewById(R.id.btnNext);
        rvConcepts = findViewById(R.id.rvConcepts);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnHelpSetup = findViewById(R.id.btnHelpSetup);
        btnNext.setEnabled(false);
        btnNext.setAlpha(0.5f);

        setupRecyclerView();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnHelpSetup.setOnClickListener(v -> showSetupHelpDialog());

        btnNext.setOnClickListener(v -> {
            if (selectedFrame == null) {
                Toast.makeText(this, getString(R.string.setup_select_frame_required), Toast.LENGTH_SHORT).show();
                return;
            }

            // Record frame selection into `top_frames` collection
            frameStatsRepository.recordFrameSelection(this, selectedFrame);

            // Save session
            SessionState session = sessionRepository.getSession();
            if (session == null) {
                session = new SessionState();
            }
            int requiredSelectionCount;
            if (selectedFrame.isRemote()) {
                requiredSelectionCount = FrameConfig.getSlotCountForLayout(selectedFrame.getLayoutType());
                session.setSelectedFrameBase64(selectedFrame.getRemoteBase64());
                session.setSelectedFrameLayout(selectedFrame.getLayoutType());
                session.setSelectedFirestoreFrameId(selectedFrame.getFirestoreId());
                session.setSelectedFrameResId(-1);
            } else {
                requiredSelectionCount = FrameConfig.getSlotCountForFrame(selectedFrame.getImageResId());
                session.setSelectedFrameResId(selectedFrame.getImageResId());
                session.setSelectedFrameBase64(null);
                session.setSelectedFrameLayout(null);
            }
            session.setPhotoCount(requiredSelectionCount);
            session.setEditState(new EditState());
            sessionRepository.saveSession(session);

            getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                    .edit()
                    .putInt("SELECTED_FRAME_ID", selectedFrame.getImageResId())
                    .apply();

            startActivity(new Intent(this, MainActivity.class));
        });
    }

    private void setupRecyclerView() {
        rvConcepts.setLayoutManager(new LinearLayoutManager(this));
        List<Concept> baseConcepts = createConceptData();

        ConceptAdapter.OnFrameSelectedListener listener = frame -> {
            selectedFrame = frame;
            btnNext.setEnabled(true);
            btnNext.setAlpha(1.0f);
            Toast.makeText(
                    this,
                    getString(R.string.setup_frame_selected, frame.getLabel()),
                    Toast.LENGTH_SHORT
            ).show();
        };

        // Show all concepts immediately (no blank screen while loading)
        ConceptAdapter adapter = new ConceptAdapter(new ArrayList<>(baseConcepts), -1, listener);
        rvConcepts.setAdapter(adapter);

        // Build flat map of all frames for lookup by id
        List<Frame> allFrames = new ArrayList<>();
        for (Concept c : baseConcepts) allFrames.addAll(c.getFrames());

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("frames")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(framesTask -> {
                    List<Frame> remoteFrames = new ArrayList<>();
                    if (!framesTask.isSuccessful()) {
                        android.util.Log.e("SetupActivity", "Error fetching frames: ", framesTask.getException());
                    }
                    if (framesTask.isSuccessful() && framesTask.getResult() != null) {
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : framesTask.getResult()) {
                            String base64 = doc.getString("base64");
                            String label = doc.getString("label");
                            String layoutType = doc.getString("layoutType");
                            if (base64 != null && label != null && layoutType != null) {
                                int id = doc.getId().hashCode();
                                remoteFrames.add(new Frame(id, base64, label, layoutType, doc.getId(), EditState.FrameStyle.NONE));
                            }
                        }
                    }
                    
                    allFrames.addAll(remoteFrames);

                    frameStatsRepository.fetchTop3().addOnCompleteListener(topTask -> {
                        List<Frame> trendingFrames = new ArrayList<>();
                        Map<Integer, Integer> rankMap = new HashMap<>();

                        if (topTask.isSuccessful() && topTask.getResult() != null && !topTask.getResult().isEmpty()) {
                            List<TopFrame> topFrames = frameStatsRepository.parseTopFrames(topTask.getResult());
                            int displayRank = 1;
                            for (TopFrame tf : topFrames) {
                                if (displayRank > 3) break;
                                try {
                                    int fId = Integer.parseInt(tf.getFrameId());
                                    for (Frame f : allFrames) {
                                        if (f.getId() == fId) {
                                            trendingFrames.add(f);
                                            rankMap.put(fId, displayRank++);
                                            break;
                                        }
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                        List<Concept> finalConcepts = new ArrayList<>();
                        if (!trendingFrames.isEmpty()) {
                            finalConcepts.add(new Concept(getString(R.string.setup_trending_frames), trendingFrames, true));
                        }
                        if (!remoteFrames.isEmpty()) {
                            finalConcepts.add(new Concept("Khung trực tuyến", remoteFrames));
                        }
                        finalConcepts.addAll(baseConcepts);

                        ConceptAdapter finalAdapter = new ConceptAdapter(finalConcepts, -1, listener);
                        finalAdapter.setRankMap(rankMap);
                        rvConcepts.setAdapter(finalAdapter);
                    });
                });
    }

    private List<Concept> createConceptData() {
        List<Concept> concepts = new ArrayList<>();

        List<Frame> shinCrayonChan = new ArrayList<>();
        shinCrayonChan.add(new Frame(1, R.drawable.frm_3x4_cushin, "Cushin", EditState.FrameStyle.NONE));
        shinCrayonChan.add(new Frame(2, R.drawable.frm_3x4_movie, "Movie", EditState.FrameStyle.CORTIS));
        shinCrayonChan.add(new Frame(3, R.drawable.frm_3x4_pig_hero, "Pig Hero", EditState.FrameStyle.T1));
        concepts.add(new Concept(getString(R.string.setup_concept_shin), shinCrayonChan));

        List<Frame> cuteFrames = new ArrayList<>();
        cuteFrames.add(new Frame(6, R.drawable.frm3_16x9_red_star, "Red Star", EditState.FrameStyle.NONE));
        concepts.add(new Concept(getString(R.string.setup_concept_cute), cuteFrames));

        List<Frame> themeFrames = new ArrayList<>();
        themeFrames.add(new Frame(9, R.drawable.frm4_16x9_food, "Food", EditState.FrameStyle.NONE));
        concepts.add(new Concept(getString(R.string.setup_concept_theme), themeFrames));

        return concepts;
    }

    private void showSetupHelpDialog() {
        HelpDialogUtils.showPhotoboothHelp(
                this,
                getString(R.string.help_setup_title),
                getString(R.string.help_setup_subtitle),
                Arrays.asList(
                        getString(R.string.help_setup_bullet_1),
                        getString(R.string.help_setup_bullet_2),
                        getString(R.string.help_setup_bullet_3)
                )
        );
    }
}
