package com.example.fast.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fast.R
import com.example.fast.adapter.ChatMessageAdapter
import com.example.fast.databinding.ActivityChatBinding
import com.example.fast.util.PermissionManager
import com.example.fast.viewmodel.ChatActivityViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.prexoft.prexocore.fadeIn
import com.prexoft.prexocore.hide
import com.prexoft.prexocore.onClick
import com.prexoft.prexocore.show

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatMessageAdapter
    private val viewModel: ChatActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ViewModel is injected via Hilt, no need to create manually
        
        // Get contact info from intent
        val contactNumber = intent.getStringExtra("contact_number") ?: ""
        val contactName = intent.getStringExtra("contact_name") ?: contactNumber
        
        // Initialize ViewModel with contact info
        viewModel.initialize(contactNumber, contactName)
        
        binding.headerTitle3.text = contactName.firstOrNull()?.toString() ?: "?"
        binding.contactNameText.text = contactName

        setupUI()
        setupRecyclerView()
        setupViewModelObservers()
        
        // Messages are loaded automatically via ViewModel.initialize()
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            binding.inputLayout.setPadding(0, 0, 0, if (keyboard > 0) keyboard else systemBars.bottom)
            binding.headerLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }
    
    /**
     * Setup ViewModel observers
     */
    private fun setupViewModelObservers() {
        // Observe messages list
        viewModel.messages.observe(this) { messages ->
            adapter.updateMessages(messages)
            binding.messagesRecyclerView.post {
                if (adapter.itemCount > 0) {
                    binding.messagesRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
        }
        
        // Observe empty state
        viewModel.isEmpty.observe(this) { isEmpty ->
            if (isEmpty) {
                binding.emptyStateText.show()
                binding.messagesRecyclerView.hide()
            } else {
                binding.emptyStateText.hide()
                binding.messagesRecyclerView.show()
            }
        }
        
        // Observe send message result
        viewModel.sendMessageResult.observe(this) { result ->
            when (result) {
                is ChatActivityViewModel.SendMessageResult.Success -> {
                    binding.editTextText.text?.clear()
                    viewModel.clearSendMessageResult()
                }
                is ChatActivityViewModel.SendMessageResult.Error -> {
                    result.exception.printStackTrace()
                    viewModel.clearSendMessageResult()
                }
                null -> { /* No action needed */ }
            }
        }
    }

    private fun setupUI() {
        binding.backButton.onClick { finish() }
        binding.cardView4.onClick { sendMessage() }

        binding.editTextText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        var isBlue = false
        binding.editTextText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                if (hasText && !isBlue) {
                    binding.cardView4.setCardBackgroundColor(getColor(R.color.primary_blue))
                    binding.imageView3.setImageResource(R.drawable.ic_send)
                    binding.cardView4.fadeIn(300)
                    isBlue = true
                } else if (!hasText && isBlue) {
                    binding.cardView4.setCardBackgroundColor(getColor(R.color.background_gray))
                    binding.imageView3.setImageResource(R.drawable.waveform)
                    binding.cardView4.fadeIn(300)
                    isBlue = false
                }
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter()
        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesRecyclerView.adapter = adapter
    }

    private fun sendMessage() {
        val messageText = binding.editTextText.text.toString().trim()
        if (messageText.isBlank()) return
        
        // Silent permission check - requests permissions directly if missing (bypasses PermissionFlowActivity UI)
        if (!PermissionManager.checkAndRedirectSilently(this)) {
            return // Permissions were requested, waiting for user response
        }
        
        // Send message via ViewModel
        viewModel.sendMessage(messageText)
    }

    // Permission request handling removed - now handled by PermissionFlowActivity
    
    override fun onResume() {
        super.onResume()
        // Optionally reload messages if needed (messages sync in real-time automatically)
        // viewModel.reloadMessages() // Uncomment if you want to force reload on resume
    }
}

