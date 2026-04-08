package com.example.expressionphotobooth.data.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.Concept;
import com.example.expressionphotobooth.domain.model.Frame;

import java.util.List;
import java.util.Map;

public class ConceptAdapter extends RecyclerView.Adapter<ConceptAdapter.ConceptViewHolder> {

    public interface OnFrameSelectedListener {
        void onFrameSelected(Frame frame);
    }

    private final List<Concept> conceptList;
    private final OnFrameSelectedListener onFrameSelectedListener;
    private int selectedFrameId;

    /**
     * Optional: map of frameId -> rank for the trending section.
     * When the concept name matches TRENDING_LABEL this map is injected
     * into the FrameAdapter so badges are shown.
     */
    private Map<Integer, Integer> rankMap;
    public static final String TRENDING_LABEL_KEY = "__trending__";

    public ConceptAdapter(List<Concept> conceptList, int selectedFrameId,
                          OnFrameSelectedListener onFrameSelectedListener) {
        this.conceptList = conceptList;
        this.selectedFrameId = selectedFrameId;
        this.onFrameSelectedListener = onFrameSelectedListener;
    }

    public void setRankMap(Map<Integer, Integer> rankMap) {
        this.rankMap = rankMap;
    }

    @NonNull
    @Override
    public ConceptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_concept, parent, false);
        return new ConceptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConceptViewHolder holder, int position) {
        Concept concept = conceptList.get(position);
        holder.tvConceptName.setText(concept.getConceptName());

        // Hide divider for the first item (position 0)
        if (holder.dividerView != null) {
            holder.dividerView.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        }

        FrameAdapter frameAdapter = new FrameAdapter(concept.getFrames(), selectedFrameId, frame -> {
            selectedFrameId = frame.getId();
            notifyDataSetChanged();
            if (onFrameSelectedListener != null) {
                onFrameSelectedListener.onFrameSelected(frame);
            }
        });

        // Inject rank badges only for the trending row
        if (rankMap != null && concept.isTrending()) {
            frameAdapter.setRankMap(rankMap);
        }

        GridLayoutManager layoutManager = new GridLayoutManager(holder.itemView.getContext(), 3);
        holder.rvFrames.setLayoutManager(layoutManager);
        holder.rvFrames.setAdapter(frameAdapter);
    }

    @Override
    public int getItemCount() {
        return conceptList != null ? conceptList.size() : 0;
    }

    static class ConceptViewHolder extends RecyclerView.ViewHolder {
        TextView tvConceptName;
        RecyclerView rvFrames;
        View dividerView;

        public ConceptViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConceptName = itemView.findViewById(R.id.tvConceptName);
            rvFrames = itemView.findViewById(R.id.rvFrames);
            dividerView = itemView.findViewById(R.id.conceptDivider);
        }
    }
}