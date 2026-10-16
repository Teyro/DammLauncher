/*
 * DammLauncher – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammlauncher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import de.oejendorferdamm.dammlauncher.hintergrund.FotoDesTages
import de.oejendorferdamm.dammlauncher.konfig.Geraet
import de.oejendorferdamm.dammlauncher.konfig.KonfigSpeicher
import de.oejendorferdamm.dammlauncher.update.Update

/**
 * Läuft alle paar Stunden mit Netz: holt das Unsplash-Foto des Tages und lädt ein neues Update
 * schon vor (installiert wird es erst, wenn der Startbildschirm vorne ist).
 */
class TagesJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                erledigen(applicationContext)
            } catch (_: Throwable) {
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 3102

        fun planen(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, TagesJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(3 * 60 * 60_000L)
                    .build()
            )
        }

        /** Was gerade fällig ist – auch beim Start des Launchers aufgerufen (im Hintergrund-Thread). */
        fun erledigen(context: Context) {
            KonfigSpeicher.init(context)
            Geraet.init(context)
            if (FotoDesTages.faellig()) FotoDesTages.laden(context)
            if (Geraet.autoUpdate && System.currentTimeMillis() - Geraet.letzterUpdateCheck > 20 * 60 * 60_000L) updateVorladen(context)
        }

        @Synchronized
        fun updateVorladen(context: Context): Update.Ergebnis? {
            val e = Update.pruefen() ?: return null
            Geraet.letzterUpdateCheck = System.currentTimeMillis()
            Geraet.veraltetGemeldet = e.gesperrt
            if (e.neuer && Geraet.updateBereit != e.info.version) {
                if (Update.vorbereiten(context, e.info) == null) Geraet.updateBereit = e.info.version
            }
            return e
        }
    }
}
