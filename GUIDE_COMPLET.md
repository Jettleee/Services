# Guide Complet - Chronomètre Service Jettlee

## Table des Matières
1. [Architecture du Service](#architecture-du-service)
2. [Explication du Code](#explication-du-code)
3. [Cycle de Vie](#cycle-de-vie)
4. [Communication Service-Activity](#communication)
5. [Patterns Avancés](#patterns-avancés)
6. [Dépannage](#dépannage)

---

## Architecture du Service

### Structure Générale

```
┌──────────────────────────────────────┐
│   JettleeMainActivity (UI Layer)     │
│   - Gère l'interface utilisateur     │
│   - Lance/arrête le service          │
│   - Affiche le temps en temps réel   │
└──────────────┬──────────────────────┘
               │ bindService()
               │ startForegroundService()
               │
┌──────────────▼──────────────────────┐
│ JettleeChronometreService            │
│ - Gère le chronomètre                │
│ - Affiche la notification            │
│ - Retourne le temps via Binder       │
└──────────────────────────────────────┘
```

---

## Explication du Code

### 1. **JettleeChronometreService.java**

#### Classe LocalBinder

```java
public class LocalBinder extends Binder {
    public JettleeChronometreService getService() {
        return JettleeChronometreService.this;
    }
}
```

**Pourquoi ?** 
- Permet à l'Activity de récupérer l'instance du service
- Sûr car fonctionne dans le même processus
- Pattern IPC (Inter-Process Communication)

---

#### Méthode onCreate()

```java
@Override
public void onCreate() {
    super.onCreate();
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    creerNotificationChannel();
}
```

**Exécution :** Une seule fois, au démarrage du service

**Responsabilités :**
- Initialiser les ressources lourdes
- Créer le canal de notification (obligatoire Android 8+)
- Préparer le NotificationManager

---

#### Méthode onStartCommand()

```java
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    String action = (intent != null) ? intent.getAction() : null;

    if ("STOP".equals(action)) {
        arreterChronometre();
        stopSelf();
        return START_NOT_STICKY;
    }

    if (!isRunning) {
        isRunning = true;
        startForeground(NOTIFICATION_ID, creerNotification());
        demarrerChronometre();
    }
    return START_STICKY;
}
```

**Points Clés :**

| Élément | Explication |
|---------|-------------|
| `String action` | Récupère l'action envoyée via Intent |
| `"STOP".equals(action)` | Arrête le service si l'action est STOP |
| `!isRunning` | Évite de redémarrer si déjà actif |
| `startForeground()` | **OBLIGATOIRE** depuis Android 8.0 |
| `return START_STICKY` | Le système redémarre automatiquement le service s'il le tue |

**Retour Possible :**
- `START_STICKY` : redémarre automatiquement
- `START_NOT_STICKY` : ne redémarre pas
- `START_REDELIVER_INTENT` : redémarre et renvoie l'Intent

---

#### Méthode demarrerChronometre()

```java
private void demarrerChronometre() {
    executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
            secondes++;
            updateNotification();
        }
    }, 0, 1, TimeUnit.SECONDS);
}
```

**Pourquoi ScheduledExecutorService ?**
- ✅ Thread-safe (pas de race condition)
- ✅ Timing précis
- ✅ Gestion automatique du thread
- ❌ Handler seul serait moins fiable

**Paramètres :**
- `0` : délai avant première exécution
- `1` : intervalle entre exécutions
- `TimeUnit.SECONDS` : unité de temps

---

#### Méthode creerNotificationChannel()

```java
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
```

**Obligatoire depuis Android 8.0 (API 26)**

**Niveaux d'Importance :**
- `IMPORTANCE_NONE` : silencieux, pas de son/vibration
- `IMPORTANCE_MIN` : silencieux avec notification
- `IMPORTANCE_LOW` : ✅ Pour les services de fond
- `IMPORTANCE_DEFAULT` : son/vibration
- `IMPORTANCE_HIGH` : alerte complète

---

#### Méthode creerNotification()

```java
private Notification creerNotification() {
    return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chronomètre Jettlee")
            .setContentText("Temps : " + formatTemps(secondes))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
}
```

**Propriétés :**
- `.setOngoing(true)` : utilisateur ne peut pas supprimer
- `.setSmallIcon()` : OBLIGATOIRE
- `.setContentTitle()` : titre affiché
- `.setContentText()` : description

---

### 2. **JettleeMainActivity.java**

#### ServiceConnection

```java
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
```

**Callbacks :**
- `onServiceConnected()` : appelé quand la liaison réussit
- `onServiceDisconnected()` : appelé quand le service est tué/déconnecté

---

#### Démarrage du Service

```java
private void demarrerService() {
    Intent intent = new Intent(this, JettleeChronometreService.class);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);  // Android 8+
    } else {
        startService(intent);            // Avant Android 8
    }
    
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
}
```

**Étapes :**
1. Créer l'Intent
2. Lancer le Foreground Service (Android 8+)
3. Binder au service pour accéder à ses méthodes

**Contexte.BIND_AUTO_CREATE :**
- Crée le service s'il n'existe pas
- Le service reste actif tant qu'il y a des bindings actifs

---

#### Mise à Jour Périodique

```java
private void mettreAJourPeriodique() {
    handler.post(new Runnable() {
        @Override
        public void run() {
            mettreAJourAffichage();
            handler.postDelayed(this, 500);  // 500ms = 2 Hz
        }
    });
}

private void mettreAJourAffichage() {
    if (isBound && chronometreService != null) {
        tvTemps.setText(chronometreService.getTempsActuel());
    }
}
```

**Avantages de 500ms :**
- Fluide visuellement (2 images/sec)
- Ne consomme pas trop d'énergie
- Pas de saccades perceptibles

---

#### Arrêt Propre

```java
private void arreterService() {
    if (chronometreService != null) {
        Intent intent = new Intent(this, JettleeChronometreService.class);
        intent.setAction("STOP");
        startService(intent);  // Envoie l'action STOP
    }

    if (isBound) {
        unbindService(connection);
        isBound = false;
    }
    
    handler.removeCallbacksAndMessages(null);
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
```

**Points Critiques :**
- ✅ Appeler `unbindService()` sinon fuite mémoire
- ✅ Arrêter les Handlers
- ✅ Gérer les cas où isBound = false

**Erreur Commune :**
```java
// ❌ ERREUR : pas de vérification
unbindService(connection);  // Exception si pas bindé

// ✅ CORRECT : vérifier d'abord
if (isBound) {
    unbindService(connection);
}
```

---

## Cycle de Vie

### Timeline Complète

```
USER ACTION                     ACTIVITY                 SERVICE
────────────────────────────────────────────────────────────────

Clic "DÉMARRER"  ──────────>  demarrerService()
                              startForegroundService()
                              bindService()
                              
                                              ──────>  onCreate()
                                                       [une seule fois]
                                                       
                                                       onStartCommand()
                                                       startForeground()
                                                       demarrerChronometre()
                                              
                              onServiceConnected()
                              isBound = true
                              mettreAJourPeriodique()
                              
Quitter app      ──────────>  onDestroy()
Notification          |        unbindService()
reste visible    <────┘
Service continue
en arrière-plan
                                              Service continue
                                              les mises à jour
                                              
Clic "ARRÊTER"   ──────────>  arreterService()
                              intent.setAction("STOP")
                              startService(intent)
                              
                                              ──────>  onStartCommand()
                                                       action = "STOP"
                                                       arreterChronometre()
                                                       stopSelf()
                                              
                              unbindService()
                              
                              onServiceDisconnected()
                                              
                                              ──────>  onDestroy()
                                                       stopForeground()
                                                       executor.shutdown()

[FIN]            ──────────>  tvTemps = "00:00"
```

---

## Communication

### Patterns Disponibles

#### 1. **Bound Service (Utilisé ici)**

```java
// Service
public class MyService extends Service {
    public class LocalBinder extends Binder {
        public MyService getService() {
            return MyService.this;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }
}

// Activity
ServiceConnection connection = new ServiceConnection() {
    public void onServiceConnected(ComponentName name, IBinder service) {
        MyService.LocalBinder binder = (MyService.LocalBinder) service;
        myService = binder.getService();
    }
};

bindService(intent, connection, Context.BIND_AUTO_CREATE);
```

**Avantages :**
- ✅ Communication synchrone
- ✅ Accès direct aux méthodes du service
- ✅ Simple pour intra-process

**Inconvénients :**
- ❌ Uniquement même processus
- ❌ Nécessite binding

---

#### 2. **BroadcastReceiver (Alternative)**

```java
// Service envoie
Intent broadcast = new Intent("CHRONO_UPDATE");
broadcast.putExtra("temps", secondes);
sendBroadcast(broadcast);

// Activity reçoit
BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        int temps = intent.getIntExtra("temps", 0);
        tvTemps.setText(formatTemps(temps));
    }
};

IntentFilter filter = new IntentFilter("CHRONO_UPDATE");
registerReceiver(receiver, filter);
```

**Avantages :**
- ✅ Asynchrone
- ✅ Entre processus possibles

**Inconvénients :**
- ❌ Plus lourd en ressources
- ❌ Plus complexe

---

#### 3. **SharedPreferences (Persistance)**

```java
// Service écrit
SharedPreferences prefs = getSharedPreferences("chrono", Context.MODE_PRIVATE);
prefs.edit().putInt("secondes", secondes).apply();

// Activity lit
int temps = prefs.getInt("secondes", 0);
```

---

### Choix du Pattern

| Pattern | Utiliser si |
|---------|-----------|
| **Bound Service** | Communication temps réel, même processus |
| **BroadcastReceiver** | Événements asynchrones |
| **SharedPreferences** | Persistance uniquement |
| **Messenger** | Entre processus complexe |
| **AIDL** | Performance critique entre processus |

---

## Patterns Avancés

### 1. **IntentService vs Service**

```java
// ❌ Service normal = bloque le Main Thread
public class MyService extends Service {
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Ceci s'exécute sur le MAIN THREAD !
        doDatabaseOperation();  // CRASH
        return START_STICKY;
    }
}

// ✅ IntentService = automatiquement sur Worker Thread
public class MyIntentService extends IntentService {
    public MyIntentService() {
        super("MyIntentService");
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        // Ceci s'exécute sur un WORKER THREAD
        doDatabaseOperation();  // OK
    }
}
```

---

### 2. **Listener Pattern avec Interface**

```java
// Service
public class MusicService extends Service {
    private OnMusicListener listener;
    
    public interface OnMusicListener {
        void onPlayStatusChanged(boolean isPlaying);
    }
    
    public void setOnMusicListener(OnMusicListener listener) {
        this.listener = listener;
    }
    
    private void play() {
        if (listener != null) {
            listener.onPlayStatusChanged(true);
        }
    }
}

// Activity
public class MainActivity extends AppCompatActivity 
        implements MusicService.OnMusicListener {
    
    @Override
    public void onPlayStatusChanged(boolean isPlaying) {
        tvStatus.setText(isPlaying ? "Playing" : "Stopped");
    }
}
```

---

### 3. **LiveData Observer (Moderne)**

```java
// Service
public class ChronometerService extends Service {
    private MutableLiveData<Integer> timeData = new MutableLiveData<>();
    
    public LiveData<Integer> getTimeData() {
        return timeData;
    }
    
    private void updateTime() {
        timeData.postValue(secondes);
    }
}

// Activity
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    chronometreService.getTimeData().observe(this, secondes -> {
        tvTemps.setText(formatTemps(secondes));
    });
}
```

---

## Dépannage

### Problème 1 : "FATAL EXCEPTION: Service not properly unbound"

```
java.lang.RuntimeException: Application has leaked ServiceConnection
```

**Solution :**
```java
@Override
protected void onDestroy() {
    if (isBound) {
        unbindService(connection);
        isBound = false;
    }
    super.onDestroy();
}
```

---

### Problème 2 : Service s'arrête quand app fermée

**Cause :** Pas de `startForeground()`

**Solution :**
```java
public int onStartCommand(Intent intent, int flags, int startId) {
    startForeground(ID, notification);  // OBLIGATOIRE
    return START_STICKY;
}
```

---

### Problème 3 : Notification n'apparaît pas

**Cause :** Permissions manquantes

**Solution :**
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Ou demander à l'runtime (Android 13+) :**
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, 
            Manifest.permission.POST_NOTIFICATIONS) 
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
    }
}
```

---

### Problème 4 : Service tue après quelques minutes

**Cause :** Pas de `START_STICKY`

**Solution :**
```java
public int onStartCommand(Intent intent, int flags, int startId) {
    // ...
    return START_STICKY;  // ✅ Redémarre automatiquement
}
```

---

### Problème 5 : Temps incorrect / saccadé

**Cause :** Mise à jour trop rapide ou lente

**Solution :**
```java
// Équilibre entre précision et ressources
handler.postDelayed(this, 500);  // 2 Hz = idéal
```

---

## Debugging Pratique

```bash
# Voir les services actifs
adb shell dumpsys activity services

# Voir les processus
adb shell ps | grep jettlee

# Logs en temps réel
adb logcat | grep JettleeChronometreService

# Tuer l'app
adb shell am force-stop com.jettlee.chronometre

# Voir les notifications
adb shell dumpsys notification
```

---

## Ressources

- [Android Developers - Services](https://developer.android.com/guide/components/services)
- [Foreground Services Guide](https://developer.android.com/guide/components/foreground-services)
- [Bound Services](https://developer.android.com/guide/components/bound-services)

---

**Créé par : Jettlee**
**Dernière mise à jour : 2024**
