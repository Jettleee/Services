# LAB 16 - Chronomètre Service Jettlee
## Maîtriser les Services dans une Application Android

### 📋 Description du Projet

Ce projet implémente un **Chronomètre complet avec Service Foreground** en Java pur. L'objectif pédagogique est de comprendre en profondeur le fonctionnement des Services Android, notamment :

- ✅ **Foreground Service** (obligatoire depuis Android 8.0)
- ✅ **Notification persistante** mise à jour en temps réel
- ✅ **Bound Service** pour communication bidirectionnelle
- ✅ **Cycle de vie des Services**
- ✅ **Gestion des permissions** (POST_NOTIFICATIONS, FOREGROUND_SERVICE)
- ✅ **Bonnes pratiques** de sécurité et de performance

### 🎯 Objectifs d'Apprentissage

Après ce lab, vous comprendrez :

1. **La différence entre Started Service et Bound Service**
   - Started Service : exécution en arrière-plan
   - Bound Service : communication bidirectionnelle

2. **Le cycle de vie des Services**
   - `onCreate()` → `onStartCommand()` → `onBind()` → `onDestroy()`

3. **Les méthodes critiques**
   - `startForeground()` : affiche une notification persistante
   - `startService()` vs `startForegroundService()`
   - `bindService()` / `unbindService()` : liaison/déliaison

4. **Les restrictions modernes d'Android**
   - Android 8.0+ : obligation du Foreground Service
   - Android 12+ : permissions statiques obligatoires
   - Android 14+ : `foregroundServiceType` explicite

5. **Patterns de communication**
   - **Binder local** : pour communication intra-processus
   - **LocalBinder** : classe interne pour retourner l'instance du service

### 📁 Architecture du Projet

```
ServiceChronometreJettlee/
├── app/
│   ├── src/main/
│   │   ├── java/com/jettlee/chronometre/
│   │   │   ├── service/
│   │   │   │   └── JettleeChronometreService.java
│   │   │   └── ui/
│   │   │       └── JettleeMainActivity.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_jettlee_main.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── build.gradle.kts
```

### 🔧 Installation & Configuration

#### 1. **Créer le Projet**
```bash
# Depuis Android Studio :
File → New → New Project → Empty Activity
- Nom : ServiceChronometreJettlee
- Language : Java
- Minimum SDK : API 24
```

#### 2. **Ajouter les Dépendances**

Dans `app/build.gradle.kts`, assurez-vous d'avoir :
```gradle
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
```

#### 3. **Vérifier AndroidManifest.xml**

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Service Declaration -->
<service
    android:name=".service.JettleeChronometreService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 📝 Composants Principaux

#### **JettleeChronometreService.java**

```java
// Points clés :
- LocalBinder : permet à l'Activity de récupérer l'instance du service
- onStartCommand() : décide du comportement au démarrage
- START_STICKY : redémarre automatiquement si tué par le système
- startForeground() : affiche la notification persistante (OBLIGATOIRE)
- ScheduledExecutorService : incrément sécurisé du temps
```

**Méthodes principales :**
- `onCreate()` : initialise le NotificationManager
- `onStartCommand()` : démarre le chronomètre en mode Foreground
- `demarrerChronometre()` : utilise ScheduledExecutorService
- `creerNotification()` : crée la notification persistante
- `updateNotification()` : met à jour en temps réel
- `onBind()` : retourne le Binder pour la communication

#### **JettleeMainActivity.java**

```java
// Points clés :
- ServiceConnection : interface obligatoire pour Bound Service
- onServiceConnected() : récupère l'instance du service
- bindService() : établit la liaison
- unbindService() : nettoie la liaison
- onDestroy() : toujours nettoyer pour éviter les fuites mémoire
```

**Workflow :**
1. Clic sur "DÉMARRER" → appel à `startForegroundService()`
2. Bind au service via `bindService()`
3. Récupération de l'instance via le Binder
4. Mise à jour du TextView avec `Handler` toutes les 500ms
5. Clic sur "ARRÊTER" → envoi de l'action "STOP"
6. `unbindService()` et `stopSelf()`

### 🚀 Étapes d'Exécution

#### **Étape 1 : Lancer le Service**
```java
Intent intent = new Intent(this, JettleeChronometreService.class);
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    startForegroundService(intent);  // Android 8+
} else {
    startService(intent);             // Avant Android 8
}
```

#### **Étape 2 : Se Lier au Service**
```java
bindService(intent, connection, Context.BIND_AUTO_CREATE);
```

#### **Étape 3 : Communiquer**
```java
// Récupérer le temps actuel
String temps = chronometreService.getTempsActuel();
tvTemps.setText(temps);
```

#### **Étape 4 : Arrêter Proprement**
```java
Intent intent = new Intent(this, JettleeChronometreService.class);
intent.setAction("STOP");
startService(intent);

if (isBound) {
    unbindService(connection);
}
```

### 📊 Cycle de Vie Visuel

```
┌─────────────────────────────────────────────┐
│         FOREGROUND SERVICE LIFECYCLE         │
├─────────────────────────────────────────────┤
│ startForegroundService(intent)              │
│            ↓                                 │
│ Service → onCreate() [une seule fois]       │
│            ↓                                 │
│ Service → onStartCommand() [à chaque appel] │
│            ↓                                 │
│ Service → startForeground(ID, notification) │
│            ↓                                 │
│ [SERVICE CONTINUE EN ARRIÈRE-PLAN]          │
│            ↓                                ╱
│ [Fermer app → SERVICE CONTINUE]    ──────╱
│            ↓                        
│ Service → onDestroy()               
│            ↓                        
│ [FIN]                               
└─────────────────────────────────────────────┘
```

### ⚠️ Points Critiques

1. **startForeground() est OBLIGATOIRE**
   - Sans cela sur Android 8+ : `AndroidRuntime: FATAL EXCEPTION`
   - Une notification DOIT être affichée

2. **Permissions pour notifications (Android 13+)**
   ```xml
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   ```

3. **foregroundServiceType est obligatoire (Android 14+)**
   ```xml
   <service android:foregroundServiceType="dataSync" />
   ```

4. **Toujours unbindService() dans onDestroy()**
   - Sinon : fuite mémoire
   - Exception : `java.lang.RuntimeException: Service not properly unbound`

5. **START_STICKY vs START_NOT_STICKY**
   - `START_STICKY` : redémarre automatiquement si tué
   - `START_NOT_STICKY` : ne redémarre pas

### 🧪 Tests Recommandés

```bash
# Lancer l'app
1. Clic "DÉMARRER" → notification apparaît + chrono tourne

# Teste le Foreground Service
2. Quitter l'app complètement (glisser l'app de recents)
   → La notification reste visible
   → Le chronomètre continue (ouvrir notifications)

# Teste le Bound Service
3. Clic "ARRÊTER" → tout s'arrête
   → TextView revient à 00:00
   → Notification disparaît

# Teste le nettoyage
4. Ouvrir Settings → Developer Options
   → Kill background processes
   → Service redémarre automatiquement (START_STICKY)
```

### 📚 Concepts Avancés (Bonus)

#### **Handler pour mise à jour UI**
```java
private Handler handler = new Handler(Looper.getMainLooper());

handler.post(new Runnable() {
    public void run() {
        tvTemps.setText(chronometreService.getTempsActuel());
        handler.postDelayed(this, 500); // Rafraîchir toutes les 500ms
    }
});
```

#### **ScheduledExecutorService pour timing précis**
```java
executor = Executors.newSingleThreadScheduledExecutor();
executor.scheduleAtFixedRate(new Runnable() {
    public void run() {
        secondes++;  // Sûr (thread-safe)
        updateNotification();
    }
}, 0, 1, TimeUnit.SECONDS);
```

#### **BroadcastReceiver (Alternative)**
Pour communiquer sans binding :
```java
// Service envoie
Intent intent = new Intent("CHRONO_UPDATE");
intent.putExtra("temps", secondes);
sendBroadcast(intent);

// Activity reçoit
BroadcastReceiver receiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
        int temps = intent.getIntExtra("temps", 0);
        tvTemps.setText(formatTemps(temps));
    }
};
```

### 🎓 Exemples du Monde Réel

Ce même pattern est utilisé par :

- **Spotify** : Service pour la lecture audio en arrière-plan
- **Google Maps** : Service pour la géolocalisation continue
- **WhatsApp** : Service pour les appels VoIP
- **Strava** : Service pour le suivi GPS

### 📖 Ressources Officielles

- [Android Services Documentation](https://developer.android.com/guide/components/services)
- [Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [Bound Services](https://developer.android.com/guide/components/bound-services)
- [Android 8.0 Changes](https://developer.android.com/about/versions/oreo/background)

### ✅ Checklist de Validation

- [ ] Service déclé dans AndroidManifest.xml
- [ ] `startForegroundService()` utilisé sur Android 8+
- [ ] Notification persistante créée
- [ ] NotificationChannel créé (Android 8+)
- [ ] Permissions déclarées
- [ ] `unbindService()` appelé dans `onDestroy()`
- [ ] `ScheduledExecutorService` correctement fermé
- [ ] Handler nettoyé dans `onDestroy()`
- [ ] Pas de crash lors de fermeture de l'app
- [ ] Service continue après fermeture de l'app

### 🔍 Debugging

```bash
# Voir les services en cours
adb shell dumpsys activity services

# Voir les processus
adb shell ps | grep jettlee

# Logcat filtré
adb logcat | grep JettleeChronometreService
```

---

**Créé par : Jettlee**  
**Date : 2024**  
**Objectif : Maîtriser les Services Android**
