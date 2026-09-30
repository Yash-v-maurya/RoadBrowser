package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityWelcomeBinding

/**
 * First-launch agreement. The browser only opens once the user has ticked that they agree to
 * the Terms of Use, have read the Privacy Policy, and take responsibility for how they use the
 * app in a vehicle. Shown again whenever [BrowserPreferences.TERMS_VERSION] goes up. After
 * accepting, it offers the Android Auto setup checklist.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { BrowserPreferences.createScaledContext(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val boxes = listOf(binding.checkTerms, binding.checkPrivacy, binding.checkSafety)
        boxes.forEach { box ->
            box.setOnCheckedChangeListener { _, _ -> binding.acceptButton.isEnabled = boxes.all { it.isChecked } }
        }
        binding.acceptButton.isEnabled = boxes.all { it.isChecked }

        binding.readTerms.setOnClickListener { startActivity(LegalActivity.intent(this, LegalActivity.TERMS)) }
        binding.readPrivacy.setOnClickListener { startActivity(LegalActivity.intent(this, LegalActivity.PRIVACY)) }
        binding.readSafety.setOnClickListener { startActivity(LegalActivity.intent(this, LegalActivity.DRIVING_SAFETY)) }

        binding.acceptButton.setOnClickListener {
            if (!boxes.all { it.isChecked }) return@setOnClickListener
            BrowserPreferences.acceptCurrentTerms(this)
            binding.consentPanel.visibility = View.GONE
            binding.setupPanel.visibility = View.VISIBLE
        }
        binding.declineButton.setOnClickListener { finishAffinity() }

        binding.setupNowButton.setOnClickListener {
            startActivities(arrayOf(browserIntent(), CarSetupActivity.intent(this)))
            finish()
        }
        binding.startBrowsingButton.setOnClickListener {
            startActivity(browserIntent())
            finish()
        }

        if (BrowserPreferences.hasAcceptedCurrentTerms(this)) {
            binding.consentPanel.visibility = View.GONE
            binding.setupPanel.visibility = View.VISIBLE
        }
    }

    /** The intent the browser was originally opened with (a link, a voice search), or a plain launch. */
    private fun browserIntent(): Intent {
        val original = intent.getParcelableExtra(EXTRA_NEXT, Intent::class.java)
        return (original?.let { Intent(it) } ?: Intent()).apply {
            setClass(this@WelcomeActivity, MainActivity::class.java)
            flags = 0
        }
    }

    companion object {
        private const val EXTRA_NEXT = "next"

        fun intent(context: Context, next: Intent?): Intent =
            Intent(context, WelcomeActivity::class.java).putExtra(EXTRA_NEXT, next)
    }
}
