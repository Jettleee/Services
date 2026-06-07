package com.jettlee.chronometre.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service Chronomètre Jettlee
 * 
 * Classe service qui gère un chronomètre en arrière-plan.
 * Utilise un Foreground Service pour se conformer aux restrictions Android 8+
 * Communique avec l'Activity via un Bound Service
 * 
 * Auteur : Jettlee
 * Date : 2024
 */
public class JettleeChronometreService extends Service {

    // Binder pour permettre à l'Activity de se connecter au service
    private final IBinder binder = new LocalBinder();
    
    private int secondes = 0;                    // temps écoulé
    private boolean isRunning = false;           // état du chrono
    private ScheduledExecutorService executor;   // pour incrémenter chaque seconde
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "jettlee_chrono_channel";
    private NotificationManager notificationManager;

    /**
     * Classe interne - Binder local pour permettre à l'Activity 
     * de récupérer l'instance du service
     */
    public class LocalBinder extends Binder {
        public JettleeChronometreService getService() {
            return JettleeChronometreService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // On récupère le NotificationManager
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        creerNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        // Action "STOP" permet d'arrêter proprement le service depuis l'Activity
        if ("STOP".equals(action)) {
            arreterChronometre();
            stopSelf();
            return START_NOT_STICKY;
        }

        // Si le service n'est pas déjà lancé, on le démarre en mode Foreground
        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIFICATION_ID, creerNotification());
            demarrerChronometre();
        }
        return START_STICKY;   // redémarre automatiquement si tué par le système
    }

    /**
     * Démarre le chronomètre avec un ScheduledExecutorService
     * Incrémente le temps chaque seconde
     */
    private void demarrerChronometre() {
        executor = Executors.newSingleThreadScheduledExecutor();
        // Toutes les 1 seconde : on incrémente et on met à jour la notification
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                secondes++;
                updateNotification();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Arrête le chronomètre
     */
    private void arreterChronometre() {
        isRunning = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        secondes = 0;
    }

    /**
     * Crée le canal de notification (obligatoire depuis Android 8.0)
     */
    private void creerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chronomètre Jettlee",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Crée la notification persistante pour le Foreground Service
     */
    private Notification creerNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Chronomètre Jettlee")
                .setContentText("Temps : " + formatTemps(secondes))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)                    // impossible à supprimer
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Met à jour la notification en temps réel
     */
    private void updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, creerNotification());
    }

    /**
     * Formate les secondes en format MM:SS
     */
    private String formatTemps(int sec) {
        int minutes = sec / 60;
        int secondesRest = sec % 60;
        return String.format("%02d:%02d", minutes, secondesRest);
    }

    /**
     * Obtient le temps actuel du chronomètre
     */
    public String getTempsActuel() {
        return formatTemps(secondes);
    }

    /**
     * Vérifie si le chronomètre est en cours d'exécution
     */
    public boolean isChronometreRunning() {
        return isRunning;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;   // retourne le binder pour la connexion
    }

    @Override
    public void onDestroy() {
        arreterChronometre();
        stopForeground(true);   // supprime la notification persistante
        super.onDestroy();
    }
}
