package com.jettlee.chronometre.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jettlee.chronometre.R;
import com.jettlee.chronometre.service.JettleeChronometreService;

/**
 * Activity principale du Chronomètre Jettlee
 * 
 * Gère l'interface utilisateur et la communication avec le Service
 * Utilise un Bound Service pour communiquer bidirectionnellement
 * 
 * Auteur : Jettlee
 * Date : 2024
 */
public class JettleeMainActivity extends AppCompatActivity {

    private TextView tvTemps;
    private Button btnStart, btnStop, btnReset;
    private JettleeChronometreService chronometreService;
    private boolean isBound = false;
    private Handler handler;

    /**
     * Connexion au service (Bound Service)
     * Permet à l'Activity de communiquer avec le service
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            JettleeChronometreService.LocalBinder binder = 
                    (JettleeChronometreService.LocalBinder) service;
            chronometreService = binder.getService();
            isBound = true;
            mettreAJourAffichage();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jettlee_main);

        handler = new Handler(Looper.getMainLooper());

        // Récupération des vues
        tvTemps = findViewById(R.id.tvTemps);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnReset = findViewById(R.id.btnReset);

        // Configuration des listeners
        btnStart.setOnClickListener(v -> demarrerService());
        btnStop.setOnClickListener(v -> arreterService());
        btnReset.setOnClickListener(v -> reinitialiserAffichage());

        // Désactiver le bouton Stop au démarrage
        btnStop.setEnabled(false);
    }

    /**
     * Démarre le service Foreground
     * Effectue également la liaison au service
     */
    private void demarrerService() {
        Intent intent = new Intent(this, JettleeChronometreService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        
        // Mettre à jour l'affichage périodiquement
        mettreAJourPeriodique();
    }

    /**
     * Arrête le service
     * Désactive la liaison
     */
    private void arreterService() {
        if (chronometreService != null) {
            Intent intent = new Intent(this, JettleeChronometreService.class);
            intent.setAction("STOP");
            startService(intent);
        }

        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        
        // Arrêter les mises à jour
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Réinitialise l'affichage
     */
    private void reinitialiserAffichage() {
        tvTemps.setText("00:00");
    }

    /**
     * Met à jour l'affichage du temps
     */
    private void mettreAJourAffichage() {
        if (isBound && chronometreService != null) {
            tvTemps.setText(chronometreService.getTempsActuel());
        }
    }

    /**
     * Met à jour l'affichage périodiquement (toutes les 500ms)
     */
    private void mettreAJourPeriodique() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                mettreAJourAffichage();
                handler.postDelayed(this, 500);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
