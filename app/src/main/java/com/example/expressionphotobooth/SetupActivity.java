package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
    private Frame selectedFrame = null; // Chuẩn bị sẵn biến để lưu Frame được chọn

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);

        // Khởi tạo Repository để lưu dữ liệu phiên chụp
        sessionRepository = ((AppContainer) getApplication()).getSessionRepository();

        // 1. ÁNH XẠ: Tìm các View từ bản thiết kế XML
        btnNext = findViewById(R.id.btnNext);
        rvConcepts = findViewById(R.id.rvConcepts);

        // 2. CÀI ĐẶT RECYCLERVIEW: Đổ dữ liệu vào danh sách
        setupRecyclerView();

        // 3. XỬ LÝ GIAO DIỆN TRÀN VIỀN (System Bars)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 4. GIAO NHIỆM VỤ: Lắng nghe sự kiện click nút NEXT
        btnNext.setOnClickListener(v -> {

            // GHI CHÚ: Vì giao diện mới đã bỏ nút chọn số lượng ảnh,
            // tạm thời tôi fix cứng số lượng ảnh là 4.
            // (Thường thì sau này số lượng ảnh sẽ phụ thuộc vào cái Frame mà user chọn)
            int photoCount = 4;

            // Tạo session mới để tránh dính dữ liệu cũ
            SessionState session = new SessionState();
            session.setPhotoCount(photoCount);
            session.setEditState(new EditState());
            sessionRepository.saveSession(session);

            // Chuyển sang MainActivity
            Intent intent = new Intent(SetupActivity.this, MainActivity.class);
            intent.putExtra(IntentKeys.EXTRA_PHOTO_COUNT, photoCount);
            startActivity(intent);
        });
    }

    // Hàm cài đặt RecyclerView
    private void setupRecyclerView() {
        rvConcepts.setLayoutManager(new LinearLayoutManager(this));
        List<Concept> mockData = createMockData();
        conceptAdapter = new ConceptAdapter(mockData);
        rvConcepts.setAdapter(conceptAdapter);
    }

    // Hàm tạo dữ liệu giả lập (Mock Data) để test
    private List<Concept> createMockData() {
        List<Concept> concepts = new ArrayList<>();

        List<Frame> princessFrames = new ArrayList<>();
        princessFrames.add(new Frame(1, R.drawable.ic_launcher_background));
        princessFrames.add(new Frame(2, R.drawable.ic_launcher_background));
        princessFrames.add(new Frame(3, R.drawable.ic_launcher_background));
        concepts.add(new Concept("1 Princess Concept", princessFrames));

        List<Frame> cortisFrames = new ArrayList<>();
        cortisFrames.add(new Frame(4, R.drawable.ic_launcher_background));
        cortisFrames.add(new Frame(5, R.drawable.ic_launcher_background));
        concepts.add(new Concept("2 Cortis Concept", cortisFrames));

        return concepts;
    }
}