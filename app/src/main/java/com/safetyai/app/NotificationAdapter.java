package com.safetyai.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public static class NotificationModel {
        String message;
        String timestamp;

        public NotificationModel(String message, String timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    private List<NotificationModel> notificationList;

    public NotificationAdapter(List<NotificationModel> notificationList) {
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel item = notificationList.get(position);
        holder.tvMessage.setText(item.message);
        holder.tvTimestamp.setText(item.timestamp);
        
        if (item.message.contains("ACTIVATED") || item.message.contains("EMERGENCY")) {
            holder.statusIndicator.setBackgroundResource(R.drawable.dot_red);
        } else {
            holder.statusIndicator.setBackgroundResource(R.drawable.dot_green);
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp;
        View statusIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}
