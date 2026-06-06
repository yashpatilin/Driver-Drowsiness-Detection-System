package com.example.drowsiness.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.drowsiness.data.DrowsinessLog
import com.example.drowsiness.databinding.LogItemBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private var logs: List<DrowsinessLog>) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    class ViewHolder(val binding: LogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = LogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        val timeString = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(log.timestamp))
        val durationSec = log.duration / 1000f
        
        holder.binding.tvTimestamp.text = timeString
        holder.binding.tvDuration.text = String.format(Locale.getDefault(), "Duration: %.1f sec", durationSec)
        holder.binding.tvSeverity.text = log.status
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<DrowsinessLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
