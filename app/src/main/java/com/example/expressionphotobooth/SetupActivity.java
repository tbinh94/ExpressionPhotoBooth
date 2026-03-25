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
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {

    private MaterialButton btnNext;
    private SessionRepository sessionRepository;

    private RecyclerView rvConcepts;
    private Frame selectedFrame;
    private int selectedPhotoCount = 4;

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
        MaterialButtonToggleGroup togglePhotoCount = findViewById(R.id.togglePhotoCount);
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

        togglePhotoCount.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedPhotoCount = checkedId == R.id.btnCount6 ? 6 : 4;
        });

        // GIAO NHIỆM VỤ: Lắng nghe sự kiện click nút NEXT
        btnNext.setOnClickListener(v -> {
            if (selectedFrame == null) {
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

            if (selectedFrame != null) {
                getSharedPreferences("PhotoboothPrefs", MODE_PRIVATE)
                        .edit()
                        .putInt("SELECTED_FRAME_ID", selectedFrame.getImageResId())
                        .apply();
            } else {
                Toast.makeText(SetupActivity.this, "Vui lòng chọn 1 frame!", Toast.LENGTH_SHORT).show();
                return; // Chặn lại nếu user chưa chọn frame
            }

            // Chuyển sang MainActivity
            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            intent.putExtra(IntentKeys.EXTRA_PHOTO_COUNT, selectedPhotoCount);
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
            Toast.makeText(SetupActivity.this, "Đã chọn frame!", Toast.LENGTH_SHORT).show();
        });
        rvConcepts.setAdapter(conceptAdapter);
    }

    private List<Concept> createConceptData() {
        List<Concept> concepts = new ArrayList<>();

        List<Frame> summerFrames = new ArrayList<>();
        summerFrames.add(new Frame(1, R.drawable.frm_basic_white, "White", EditState.FrameStyle.NONE));
        summerFrames.add(new Frame(2, R.drawable.frm_brown_caro, "Brown Caro", EditState.FrameStyle.CORTIS));
        summerFrames.add(new Frame(3, R.drawable.sample_frame, "T1", EditState.FrameStyle.T1));
        concepts.add(new Concept("Summer Concept", summerFrames));

        List<Frame> cortisFrames = new ArrayList<>();
        cortisFrames.add(new Frame(4, R.drawable.sample_frame, "Aespa", EditState.FrameStyle.AESPA));
        cortisFrames.add(new Frame(5, R.drawable.sample_frame, "Basic", EditState.FrameStyle.NONE));
        concepts.add(new Concept("Idol Concept", cortisFrames));

        return concepts;
    }
}
