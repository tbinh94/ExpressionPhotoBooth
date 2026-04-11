package com.example.expressionphotobooth;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.domain.model.AdminAiChatRequest;
import com.example.expressionphotobooth.domain.model.AdminAiChatResponse;
import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.example.expressionphotobooth.domain.repository.AdminAiChatRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.expressionphotobooth.utils.LocaleManager;

import java.util.ArrayList;
import java.util.List;

public class AdminAiChatBottomSheet extends BottomSheetDialogFragment {

    private AdminAiChatRepository repository;
    private AdminDashboardStats statsSnapshot;
    private String languageTag;

    private RecyclerView rvChat;
    private EditText etChatInput;
    private View btnSend;
    private View btnClose;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    
    private long lastSentTime = 0;
    private static final long COOLDOWN_MS = 5000; // 5 giây

    public static AdminAiChatBottomSheet newInstance(AdminDashboardStats statsSnapshot, String languageTag) {
        AdminAiChatBottomSheet fragment = new AdminAiChatBottomSheet();
        fragment.statsSnapshot = statsSnapshot;
        fragment.languageTag = languageTag;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_admin_ai_chat_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ((AppContainer) requireActivity().getApplication()).getAdminAiChatRepository();

        rvChat = view.findViewById(R.id.rvChat);
        etChatInput = view.findViewById(R.id.etChatInput);
        btnSend = view.findViewById(R.id.btnSend);
        btnClose = view.findViewById(R.id.btnClose);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvChat.setAdapter(adapter);

        btnClose.setOnClickListener(v -> dismiss());

        btnSend.setOnClickListener(v -> {
            String query = etChatInput.getText().toString().trim();
            if (query.isEmpty()) return;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSentTime < COOLDOWN_MS) {
                long waitSec = (COOLDOWN_MS - (currentTime - lastSentTime)) / 1000;
                Toast.makeText(requireContext(), "Vui lòng chờ " + (waitSec + 1) + " giây để tiếp tục hỏi.", Toast.LENGTH_SHORT).show();
                return;
            }

            lastSentTime = currentTime;
            sendQuery(query);
        });

        // Welcome message
        messages.add(new ChatMessage(getString(R.string.admin_ai_chat_welcome), false));
        adapter.notifyItemInserted(0);
    }

    private void sendQuery(String query) {
        messages.add(new ChatMessage(query, true));
        int userMsgPos = messages.size() - 1;
        adapter.notifyItemInserted(userMsgPos);
        rvChat.scrollToPosition(userMsgPos);
        etChatInput.setText("");

        // Show thinking
        messages.add(new ChatMessage(getString(R.string.admin_ai_chat_thinking), false, true));
        int thinkingPos = messages.size() - 1;
        adapter.notifyItemInserted(thinkingPos);
        rvChat.scrollToPosition(thinkingPos);

        hideKeyboard();

        repository.sendQuery(new AdminAiChatRequest(query, languageTag, statsSnapshot), new AdminAiChatRepository.Callback() {
            @Override
            public void onSuccess(AdminAiChatResponse response) {
                if (!isAdded()) return;
                messages.remove(thinkingPos);
                adapter.notifyItemRemoved(thinkingPos);
                
                messages.add(new ChatMessage(response.getAnswer(), false));
                adapter.notifyItemInserted(messages.size() - 1);
                rvChat.scrollToPosition(messages.size() - 1);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                messages.remove(thinkingPos);
                adapter.notifyItemRemoved(thinkingPos);

                String errorMsg = getString(R.string.admin_ai_chat_error);
                messages.add(new ChatMessage(errorMsg + "\n" + message, false));
                adapter.notifyItemInserted(messages.size() - 1);
                rvChat.scrollToPosition(messages.size() - 1);
            }
        });
    }

    private void hideKeyboard() {
        View view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static class ChatMessage {
        String text;
        boolean isUser;
        boolean isThinking;

        ChatMessage(String text, boolean isUser) {
            this(text, isUser, false);
        }

        ChatMessage(String text, boolean isUser, boolean isThinking) {
            this.text = text;
            this.isUser = isUser;
            this.isThinking = isThinking;
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> messages;

        ChatAdapter(List<ChatMessage> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 0 ? R.layout.item_chat_bubble_bot : R.layout.item_chat_bubble_user;
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.tvText.setText(msg.text);
            if (msg.isThinking) {
                holder.tvText.setAlpha(0.6f);
            } else {
                holder.tvText.setAlpha(1.0f);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isUser ? 1 : 0;
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvText;
            ViewHolder(View v) {
                super(v);
                tvText = v.findViewById(R.id.tvChatText);
            }
        }
    }
}
