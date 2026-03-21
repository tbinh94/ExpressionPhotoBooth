package com.example.expressionphotobooth.data.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.Concept;
import com.example.expressionphotobooth.domain.model.Frame;
import java.util.List;

public class ConceptAdapter extends RecyclerView.Adapter<ConceptAdapter.ConceptViewHolder> {

    private List<Concept> conceptList;
    private FrameAdapter.OnFrameClickListener frameClickListener;

    public ConceptAdapter(List<Concept> conceptList, FrameAdapter.OnFrameClickListener listener) {
        this.conceptList = conceptList;
        this.frameClickListener = listener;
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

        // Truyền listener xuống FrameAdapter
        FrameAdapter frameAdapter = new FrameAdapter(concept.getFrames(), frameClickListener);
        LinearLayoutManager layoutManager = new LinearLayoutManager(holder.itemView.getContext(), RecyclerView.HORIZONTAL, false);
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

        public ConceptViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConceptName = itemView.findViewById(R.id.tvConceptName);
            rvFrames = itemView.findViewById(R.id.rvFrames);
        }
    }
}
