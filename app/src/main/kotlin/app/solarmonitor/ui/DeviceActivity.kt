package app.solarmonitor.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import app.solarmonitor.R
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.model.Field
import app.solarmonitor.repo.Cancellable
import app.solarmonitor.repo.Outcome
import app.solarmonitor.repo.SolarRepo

/** The inverter's full last report as a title/value list. */
class DeviceActivity : Activity() {

    private lateinit var repo: SolarRepo
    private var pending: Cancellable? = null
    private lateinit var adapter: FieldAdapter

    private lateinit var title: TextView
    private lateinit var reported: TextView
    private lateinit var banner: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)
        repo = SolarRepo.get(this)

        title = findViewById(R.id.title)
        reported = findViewById(R.id.reported)
        banner = findViewById(R.id.banner)
        progress = findViewById(R.id.progress)
        adapter = FieldAdapter(this)
        findViewById<ListView>(R.id.fields).adapter = adapter

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.refresh).setOnClickListener { load() }
        load()
    }

    override fun onDestroy() {
        pending?.cancel()
        super.onDestroy()
    }

    private fun load() {
        pending?.cancel()
        progress.visibility = View.VISIBLE
        pending = repo.deviceReport { outcome ->
            when (outcome) {
                is Outcome.Cached -> {
                    render(outcome.value)
                    if (outcome.isFinal) progress.visibility = View.INVISIBLE
                }
                is Outcome.Fresh -> {
                    render(outcome.value)
                    banner.visibility = View.GONE
                    progress.visibility = View.INVISIBLE
                }
                is Outcome.Error -> {
                    progress.visibility = View.INVISIBLE
                    if (outcome.error is AuthRequiredException) {
                        Nav.restartAtLogin(this, repo, getString(R.string.session_expired))
                    } else {
                        banner.text = getString(R.string.banner_short, Errors.reason(outcome.error))
                        banner.visibility = View.VISIBLE
                        if (outcome.cachedValue == null) reported.text = getString(R.string.no_data)
                    }
                }
            }
        }
    }

    private fun render(report: SolarRepo.DeviceReport) {
        title.text = report.device.alias
        val timestamp = report.fields.firstOrNull { it.title == "Timestamp" }
        reported.text = timestamp?.let { getString(R.string.reported_at, it.value) } ?: ""
        adapter.set(report.fields.filter { it.title != "Timestamp" && it.title != "id" })
    }
}

private class FieldAdapter(context: Context) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var items: List<Field> = emptyList()

    fun set(fields: List<Field>) {
        items = fields
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Field = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_field, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.fieldTitle).text = item.title
        view.findViewById<TextView>(R.id.fieldValue).text =
            if (item.unit != null) "${item.value} ${item.unit}" else item.value
        return view
    }
}
