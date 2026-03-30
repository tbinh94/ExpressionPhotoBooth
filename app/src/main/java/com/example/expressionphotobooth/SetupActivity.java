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
import com.example.expressionphotobooth.domain.model.Concept;
import com.example.expressionphotobooth.domain.model.Frame;
import com.example.expressionphotobooth.domain.model.EditState;
import com.example.expressionphotobooth.domain.model.SessionState;
import com.example.expressionphotobooth.domain.repository.SessionRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetupActivity extends AppCompatActivity {

    private MaterialButton btnNext;
    private SessionRepository sessionRepository;

    private RecyclerView rvConcepts;
    private Frame selectedFrame;
    private final int selectedPhotoCount = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        // Khởi tạo Repository để lưu dữ liệu phiên chụp
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();

        // 1. ÁNH XẠ: Tìm các thành phần từ bản thiết kế XML
        btnNext = findViewById(R.id.btnNext);
        rvConcepts = findViewById(R.id.rvConcepts);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnHelpSetup = findViewById(R.id.btnHelpSetup);
        btnNext.setEnabled(false);

        // 2. CÀI ĐẶT RECYCLERVIEW: Đổ dữ liệu vào danh sách
        setupRecyclerView();

        // 3. XỬ LÝ GIAO DIỆN TRÀN VIỀN (System Bars)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Lắng nghe sự kiện click: Khi bấm vào thì quay lại màn hình trước đó
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnHelpSetup.setOnClickListener(v -> showSetupHelpDialog());

        // GIAO NHIỆM VỤ: Lắng nghe sự kiện click nút NEXT
        btnNext.setOnClickListener(v -> {
            if (selectedFrame == null) {
                Toast.makeText(SetupActivity.this, getString(R.string.setup_select_frame_required), Toast.LENGTH_SHORT).show();
                return;
            }

            // Tạo session mới để tránh dính dữ liệu cũ
            SessionState session = sessionRepository.getSession();
            if (session == null) {
                session = new SessionState();
            }

            session.setPhotoCount(selectedPhotoCount);
            EditState editState = new EditState();
            editState.setFrameStyle(selectedFrame.getFrameStyle());
            session.setEditState(editState);
            sessionRepository.saveSession(session);

            getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                    .edit()
                    .putInt("SELECTED_FRAME_ID", selectedFrame.getImageResId())
                    .apply();

            // Chuyển sang MainActivity
            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }

    // Hàm cài đặt RecyclerView
    private void setupRecyclerView() {
        rvConcepts.setLayoutManager(new LinearLayoutManager(this));
        List<Concept> conceptData = createConceptData();
        ConceptAdapter conceptAdapter = new ConceptAdapter(conceptData, -1, frame -> {
            selectedFrame = frame;
            btnNext.setEnabled(true);
            btnNext.setAlpha(1.0f);
            Toast.makeText(
                    SetupActivity.this,
                    getString(R.string.setup_frame_selected, frame.getLabel()),
                    Toast.LENGTH_SHORT
            ).show();
        });
        rvConcepts.setAdapter(conceptAdapter);

        // Mặc định làm mờ nút Next
        btnNext.setAlpha(0.5f);
        btnNext.setEnabled(false);
    }

    private List<Concept> createConceptData() {
        List<Concept> concepts = new ArrayList<>();


        List<Frame> shinCrayonChan = new ArrayList<>();
        shinCrayonChan.add(new Frame(1, R.drawable.frm_3x4_cushin, "Cushin", EditState.FrameStyle.NONE));
        shinCrayonChan.add(new Frame(2, R.drawable.frm_3x4_movie, "Movie", EditState.FrameStyle.CORTIS));
        shinCrayonChan.add(new Frame(3, R.drawable.frm_3x4_pig_hero, "Pig Hero", EditState.FrameStyle.T1));
        concepts.add(new Concept("Shin Crayon Chan", shinCrayonChan));


        List<Frame> cuteFrames = new ArrayList<>();
//        cuteFrames.add(new Frame(4, R.drawable.frm3_16x9_blue_canvas, "Blue Canvas", EditState.FrameStyle.AESPA));
//        cuteFrames.add(new Frame(5, R.drawable.frm3_16x9_green_doodle, "Green Doodle", EditState.FrameStyle.NONE));
        cuteFrames.add(new Frame(6, R.drawable.frm3_16x9_red_star, "Red Star", EditState.FrameStyle.NONE));
        concepts.add(new Concept("Cute Concept", cuteFrames));


        List<Frame> themeFrames = new ArrayList<>();
//        themeFrames.add(new Frame(7, R.drawable.frm4_16x9_bow, "Bow", EditState.FrameStyle.AESPA));
//        themeFrames.add(new Frame(8, R.drawable.frm4_16x9_heart, "Heart", EditState.FrameStyle.NONE));
        themeFrames.add(new Frame(9, R.drawable.frm4_16x9_food, "Food", EditState.FrameStyle.NONE));
//        themeFrames.add(new Frame(10, R.drawable.frm4_16x9_bow, "Bow", EditState.FrameStyle.AESPA));
//        themeFrames.add(new Frame(11, R.drawable.frm4_16x9_heart, "Heart", EditState.FrameStyle.NONE));
        themeFrames.add(new Frame(12, R.drawable.frm4_16x9_food, "Food", EditState.FrameStyle.NONE));
        concepts.add(new Concept("Theme Concept", themeFrames));


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
