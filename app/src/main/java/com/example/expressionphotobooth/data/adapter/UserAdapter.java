package com.example.expressionphotobooth.data.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.User;
import com.example.expressionphotobooth.domain.model.UserRole;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    public interface OnUserActionListener {
        void onApprove(User user);
    }

    private final List<User> users;
    private final OnUserActionListener listener;

    public UserAdapter(List<User> users, OnUserActionListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = holder.itemView.getContext().getString(R.string.admin_users_unknown_name);
        }
        holder.tvName.setText(displayName);
        holder.tvEmail.setText(user.getEmail());
        holder.tvRole.setText(user.getRole().toFirestoreValue().toUpperCase());
        holder.tvAvatar.setText(resolveInitial(user));

        if (user.getRole() == UserRole.ADMIN) {
            holder.roleIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E91E63")));
            holder.btnApprove.setVisibility(View.GONE);
            holder.tvExpiration.setVisibility(View.GONE);
        } else if (user.getRole() == UserRole.PREMIUM) {
            holder.roleIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676")));
            holder.btnApprove.setText(holder.itemView.getContext().getString(R.string.admin_users_revoke));
            holder.btnApprove.setVisibility(View.VISIBLE);

            if (user.getPremiumUntil() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                String dateStr = sdf.format(new Date(user.getPremiumUntil()));
                holder.tvExpiration.setText(holder.itemView.getContext().getString(R.string.admin_users_expires_on_format, dateStr));
                holder.tvExpiration.setVisibility(View.VISIBLE);
            } else {
                holder.tvExpiration.setVisibility(View.GONE);
            }
        } else {
            holder.roleIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
            holder.btnApprove.setText(holder.itemView.getContext().getString(R.string.admin_users_approve));
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.tvExpiration.setVisibility(View.GONE);
        }

        holder.btnApprove.setOnClickListener(v -> listener.onApprove(user));
    }

    private String resolveInitial(User user) {
        String source = user.getDisplayName();
        if (source == null || source.trim().isEmpty()) {
            source = user.getEmail();
        }
        if (source == null || source.trim().isEmpty()) {
            return "U";
        }
        return source.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvEmail, tvRole, tvExpiration;
        View roleIndicator;
        MaterialButton btnApprove;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvUserAvatar);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            tvRole = itemView.findViewById(R.id.tvUserRole);
            tvExpiration = itemView.findViewById(R.id.tvExpiration);
            roleIndicator = itemView.findViewById(R.id.viewUserRoleIndicator);
            btnApprove = itemView.findViewById(R.id.btnApprove);
        }
    }
}
