package io.github.joyk0117.paperworknavigator.customtasks.documentreview.util

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

object DocumentIntentBuilder {

    /**
     * Creates a Calendar INSERT intent for a deadline.
     * Returns null if [date] is not a valid ISO 8601 date (YYYY-MM-DD).
     */
    fun calendarIntent(docName: String, date: String, noteJa: String?): Intent? =
        buildAllDayCalendarIntent(title = "$docName - 提出期限", date = date, description = noteJa)

    /**
     * Creates a Calendar INSERT intent for a named event date (EVENT_DATES entries).
     * Returns null if [date] is not a valid ISO 8601 date (YYYY-MM-DD).
     */
    fun calendarIntentForEvent(title: String, date: String, descriptionJa: String?): Intent? =
        buildAllDayCalendarIntent(title = title, date = date, description = descriptionJa)

    private fun buildAllDayCalendarIntent(title: String, date: String, description: String?): Intent? {
        val localDate = try {
            LocalDate.parse(date)
        } catch (_: DateTimeParseException) {
            return null
        }
        val utc = ZoneId.of("UTC")
        val beginMs = localDate.atStartOfDay(utc).toInstant().toEpochMilli()
        val endMs = localDate.plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli()

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
        }
    }

    /**
     * Creates a geo URI intent to open the location in a maps application.
     * Prefers [addressJa] over [nameJa] as the search query.
     * Returns null if both are blank or null.
     */
    fun mapsIntent(addressJa: String?, nameJa: String?): Intent? {
        val query = addressJa?.takeIf { it.isNotBlank() }
            ?: nameJa?.takeIf { it.isNotBlank() }
            ?: return null
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Creates a tel: URI intent to open the phone dialer.
     * Returns null if [phone] is blank.
     * tel: URIs accept digits, +, -, (), and spaces directly — no percent-encoding needed.
     */
    fun phoneIntent(phone: String): Intent? {
        if (phone.isBlank()) return null
        return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    }

    /**
     * Creates a mailto: URI intent to open an email client.
     * Returns null if [email] is blank.
     * Uses Uri.fromParts to avoid percent-encoding the @ sign in the address.
     */
    fun emailIntent(email: String): Intent? {
        if (email.isBlank()) return null
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
    }

    /**
     * Creates an ACTION_VIEW intent to open a URL in a browser.
     * Returns null if [url] is blank.
     */
    fun urlIntent(url: String): Intent? {
        if (url.isBlank()) return null
        val uri = if (url.startsWith("http://") || url.startsWith("https://")) {
            Uri.parse(url)
        } else {
            Uri.parse("https://$url")
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Creates an ACTION_VIEW intent for a parcel tracking number.
     * Uses [carrier] (ML Kit ParcelCarrier int as String) to build a carrier-specific URL,
     * falling back to a Google search for unknown carriers.
     * Returns null if [trackingNumber] is blank.
     */
    fun trackingIntent(carrier: String?, trackingNumber: String): Intent? {
        if (trackingNumber.isBlank()) return null
        val url = when (carrier) {
            "1" -> "https://www.fedex.com/apps/fedextrack/?tracknumbers=${Uri.encode(trackingNumber)}"
            "2" -> "https://www.ups.com/track?tracknum=${Uri.encode(trackingNumber)}"
            "3" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=${Uri.encode(trackingNumber)}"
            "4" -> "https://www.ontrac.com/tracking.asp?tracking=${Uri.encode(trackingNumber)}"
            "5" -> "https://www.dhl.com/en/express/tracking.html?AWB=${Uri.encode(trackingNumber)}"
            "8" -> "https://www.tnt.com/express/en_us/site/shipping-tools/tracking.html?searchType=CON&cons=${Uri.encode(trackingNumber)}"
            "9" -> "https://www.amazon.com/progress-tracker/package/?_encoding=UTF8&packageIndex=0&shipmentId=${Uri.encode(trackingNumber)}"
            else -> "https://www.google.com/search?q=${Uri.encode("track $trackingNumber")}"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    /**
     * Creates an ACTION_VIEW intent for a flight number using FlightAware.
     * [airlineCode] is the IATA airline code (e.g. "AA", "UA").
     * Returns null if [flightNumber] is blank.
     */
    fun flightIntent(airlineCode: String?, flightNumber: String): Intent? {
        if (flightNumber.isBlank()) return null
        val query = if (!airlineCode.isNullOrBlank()) "$airlineCode$flightNumber" else flightNumber
        val url = "https://flightaware.com/live/flight/${Uri.encode(query)}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
}
