package com.example.expressionphotobooth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.data.adapter.UserAdapter;
import com.example.expressionphotobooth.domain.model.User;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersActivity extends AppCompatActivity {

    private RecyclerView rvUsers;
    private UserAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private ProgressBar progressBar;
    private View tvEmpty;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force Light Theme for Admin management
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_users);

        firestore = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvUsers = findViewById(R.id.rvUsers);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(userList, user -> togglePremium(user));
        rvUsers.setAdapter(adapter);

        loadUsers();
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        firestore.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    userList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String uid = doc.getId();
                        String email = doc.getString("email");
                        String name = doc.getString("displayName");
                        String roleStr = doc.getString("role");
                        UserRole role = UserRole.from(roleStr);
                        Long premiumUntil = doc.getLong("premiumUntil");
                        userList.add(new User(uid, email, name, role, premiumUntil != null ? premiumUntil : 0L));
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(userList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void togglePremium(User user) {
        if (user.getRole() == UserRole.ADMIN) return;

        UserRole newRole = (user.getRole() == UserRole.PREMIUM) ? UserRole.USER : UserRole.PREMIUM;
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long newPremiumUntil = (newRole == UserRole.PREMIUM) ? (now + thirtyDaysMs) : 0L;

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("role", newRole.toFirestoreValue());
        updates.put("premiumUntil", newPremiumUntil);

        progressBar.setVisibility(View.VISIBLE);
        firestore.collection("users")
                .document(user.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    progressBar.setVisibility(View.GONE);
                    user.setRole(newRole);
                    user.setPremiumUntil(newPremiumUntil);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.admin_users_save_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.admin_users_save_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
