package com.example.fast.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fast.R
import com.example.fast.adapter.ContactAdapter
import com.example.fast.databinding.ActivityContactsBinding
import com.example.fast.model.Contact
import com.example.fast.util.ContactHelperOptimized
import com.example.fast.util.PermissionSyncHelper
import com.example.fast.util.PermissionManager
import com.prexoft.prexocore.hide
import com.prexoft.prexocore.onClick
import com.prexoft.prexocore.onScroll
import com.prexoft.prexocore.show

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private lateinit var contactAdapter: ContactAdapter
    private var allContacts: List<Contact> = emptyList()
    private var filteredContacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupUI()
        setupRecyclerView()
        loadContacts()
    }

    private fun setupUI() {
        binding.backButton.onClick { finish() }
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(binding.searchEditText.text.toString()); true } else false
        }
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { performSearch(s?.toString() ?: "") }
        })
        binding.scroller.onScroll { }
    }

    private fun setupRecyclerView() {
        contactAdapter = ContactAdapter { contact ->
            try {
                startActivity(Intent(this@ContactsActivity, ChatActivity::class.java).apply {
                    putExtra("contact_number", contact.phoneNumber)
                    putExtra("contact_name", contact.name)
                })
                finish()
            } catch (e: Exception) { e.printStackTrace() }
        }
        binding.contactsRecyclerView.apply { layoutManager = LinearLayoutManager(this@ContactsActivity); adapter = contactAdapter }
    }

    private fun loadContacts(forceRefresh: Boolean = false) {
        // Permission entry point is remote command or ActivationActivity status card.
        allContacts = ContactHelperOptimized.getAllContacts(this, forceRefresh)
        filteredContacts = allContacts
        updateContactList()
    }

    private fun performSearch(query: String) {
        val searchQuery = query.trim().lowercase()
        filteredContacts = if (searchQuery.isEmpty()) allContacts
        else allContacts.filter { it.name.lowercase().contains(searchQuery) || it.phoneNumber.contains(searchQuery) }
        updateContactList()
    }

    private fun updateContactList() {
        if (filteredContacts.isEmpty()) { binding.emptyStateText.show(); binding.contactsRecyclerView.hide() }
        else { binding.emptyStateText.hide(); binding.contactsRecyclerView.show(); contactAdapter.submitList(filteredContacts) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_LIST_REQUEST_CODE &&
            PermissionManager.handleRequestPermissionListResult(this, requestCode, permissions, grantResults)
        ) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission entry point is remote command or ActivationActivity status card; no permission request here.
        loadContacts(forceRefresh = false) // Cache will be used
        // Check and start sync if permissions are available
        PermissionSyncHelper.checkAndStartSync(this)
    }
}
