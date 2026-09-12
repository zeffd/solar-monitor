package app.solarmonitor.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import app.solarmonitor.R
import app.solarmonitor.repo.Cancellable
import app.solarmonitor.repo.Outcome
import app.solarmonitor.repo.SolarRepo

/** Launcher. Skips straight to the Dashboard when a session is stored. */
class LoginActivity : Activity() {
    private var pending: Cancellable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SolarRepo.get(this)
        if (repo.prefs.hasSession()) {
            openDashboard()
            return
        }
        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)
        val signIn = findViewById<Button>(R.id.signIn)
        val error = findViewById<TextView>(R.id.error)
        val progress = findViewById<ProgressBar>(R.id.progress)
        intent.getStringExtra(Nav.EXTRA_MESSAGE)?.let {
            error.text = it
            error.visibility = View.VISIBLE
        }

        fun attempt() {
            val u = username.text.toString().trim()
            val p = password.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                error.text = getString(R.string.login_missing)
                error.visibility = View.VISIBLE
                return
            }
            error.visibility = View.GONE
            progress.visibility = View.VISIBLE
            signIn.isEnabled = false
            pending?.cancel()
            pending = repo.login(u, p) { outcome ->
                progress.visibility = View.GONE
                signIn.isEnabled = true
                when (outcome) {
                    is Outcome.Fresh, is Outcome.Cached -> openDashboard()
                    is Outcome.Error -> {
                        error.text = Errors.loginMessage(outcome.error)
                        error.visibility = View.VISIBLE
                    }
                }
            }
        }

        signIn.setOnClickListener { attempt() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attempt(); true } else false
        }
    }

    private fun openDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        pending?.cancel()
        super.onDestroy()
    }
}
