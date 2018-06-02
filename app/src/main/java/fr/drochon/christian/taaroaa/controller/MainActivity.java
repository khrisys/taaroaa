package fr.drochon.christian.taaroaa.controller;

import android.app.PendingIntent;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import fr.drochon.christian.taaroaa.R;
import fr.drochon.christian.taaroaa.auth.ConnectionActivity;
import fr.drochon.christian.taaroaa.base.BaseActivity;
import fr.drochon.christian.taaroaa.notifications.MyFirebaseMessagingService;

import static fr.drochon.christian.taaroaa.R.id;
import static fr.drochon.christian.taaroaa.R.layout;
import static fr.drochon.christian.taaroaa.R.string;
import static fr.drochon.christian.taaroaa.R.string.app_name;
import static fr.drochon.christian.taaroaa.R.style;


public class MainActivity extends BaseActivity implements ComponentCallbacks2 {

    //Id de connexion dans l'activité courante
    private static final int RC_SIGN_IN = 123;
    public static boolean isAppRunning;
    private Button mConnexion;
    private Button creationCompte;
    private TextView mTextViewHiddenForSnackbar;

    // --------------------
    // LIFE CYCLE
    // --------------------

    /**
     * Methode permettant de creer l'ecran de l'activité destinée à la connexion d'un utilisateur
     *
     * @param savedInstanceState : sauvegarde du tel
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);
        configureToolbar();
        giveToolbarAName(app_name);
        onTrimMemory(TRIM_MEMORY_BACKGROUND);


        mTextViewHiddenForSnackbar = findViewById(R.id.test_coordinator);
        creationCompte = findViewById(id.creation_compte_btn);
        mConnexion = findViewById(id.connection_valid_btn);

        Button deconnexion = findViewById(id.deconnexion_btn);

        // lorsque je suis connecté, c'est que j'ai un compte et je n'ai pas besoin de voir le bouton "creer un compte"
        //if(isCurrentUserLogged()) creationCompte.setVisibility(View.GONE);
        isAppRunning = true;

        // Souscription aux notifications
        FirebaseMessaging.getInstance().subscribeToTopic("courses");
        FirebaseMessaging.getInstance().subscribeToTopic("covoiturages");

        // --------------------
        // LISTENERS
        // --------------------

        creationCompte.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCurrentUserLogged()) {
                    startConnectionActivity();
                } else {
                    Toast.makeText(MainActivity.this, "Vous etes déjà connecté, vous ne pouvez pas créer un compte !", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Lancement de la page de connection à un compte existant
        mConnexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCurrentUserLogged()) {
                    //CREATION DU USER
                    createUserInFirestore();
                    startSummaryActivity(); // connecté : renvoyé vers le sommaire
                } else {
                    startSignInActivity(); // non connecté : inscription
                }
            }
        });

        // Deconnexion de l'utilisateur
        deconnexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSnackBar(getString(string.connection_end));
                signOutUserFromFirebase();
                creationCompte.setVisibility(View.VISIBLE);
            }
        });

        // Si l'utilisateur a oublié son mot de passe, il clique sur le lien et est envoyé sur la page de recuperation du mot de passe
        EditText passwordRecovery = findViewById(id.mdp_oubli_lien);
        passwordRecovery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PasswordActivity.class);
                startActivity(intent);
            }
        });
    }

    // --------------------
    // UI
    // --------------------

    @Override
    public int getFragmentLayout() {
        return layout.activity_main;
    }



    /**
     * Methode permettant un affichage different en fonction de si l'user a dejà été loggé ou pas
     */
    @Override
    protected void onResume() {
        super.onResume();
        this.updateUIWhenResuming(); // affiche la vue lorsque le tel est dans le cycle de vie onResume()

        if(isCurrentUserLogged()) creationCompte.setVisibility(View.GONE);

        Bundle bundle = new Bundle();
        bundle.putString("title", "titre du message");
        bundle.putString("text", "message");


        Intent intent = new Intent(this, MyFirebaseMessagingService.class).putExtra("titre", "titre du message").putExtra("text", "message");
        PendingIntent operation = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);


        //CRASHLYTICS : force application to crash
        //Crashlytics.getInstance().crash();
    }
    /**
     * Methode permettant de changer d'ecran lors d'une connexion valide
     */
    private void startSummaryActivity() {
        Intent intent = new Intent(MainActivity.this, SummaryActivity.class);
        startActivity(intent);
    }

    /**
     * Methode permettant d'aller sur la page de rensignement des identifiants de l'utilisateur (email, password)
     */
    private void startConnectionActivity() {
        Intent intent = new Intent(MainActivity.this, ConnectionActivity.class);
        startActivity(intent);
    }

    /**
     * Methode permettant d'afficher sur le bouton de connexion soit la direction de l'ecran de connexion
     * soit la direction de l'ecran sommaire en fonction de si l'user est connexté ou pas, et de
     * rediriger l'user vers l'affichage de la page adequate
     */
    private void updateUIWhenResuming() {

        this.mConnexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCurrentUserLogged()) {
                    startSummaryActivity();
                } else {
                    startSignInActivity();
                }
            }
        });
    }



    // --------------------
    // PROVIDERS & AUTHENTIFICATION
    // --------------------

    /**
     * Methode lancant une page autogenerée par Firebase permettant la connexion/inscription à l'app
     */
    private void startSignInActivity() {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder() // lance une activité de connexion/inscrption autogeneree
                        .setTheme(style.LoginTheme) // definir un style dans le fichier res/values/styles.xml
                        .setAvailableProviders( // ajoute des moyens divers de connexion (email, google, fb..)
                                Collections.singletonList(new AuthUI.IdpConfig.EmailBuilder().build()))
                        .setIsSmartLockEnabled(false, true)
                        .setLogo(R.mipmap.logo1)
                        .build(),
                RC_SIGN_IN); // identifiant de connexion
    }


    // --------------------
    // AUTHENTIFICATION
    // --------------------

    /**
     * Methode de recuperer le resultat renvoyé par l'activité autogénérée lors d'une inscription/connexion.
     * POur utiliser ce resultat, on va dans la methode handleResponseAfterSignIn()
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.handleResponseAfterSignIn(requestCode, resultCode, data);
    }


    /**
     * Methode permettant d'afficher un message personnalisé dans une snackbar en fonction du resultat renvoyé par l'activité d'inscription/connexion
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    private void handleResponseAfterSignIn(int requestCode, int resultCode, Intent data) {

        IdpResponse response = IdpResponse.fromResultIntent(data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) { // SUCCESS
                this.createUserInFirestore();
                this.startSummaryActivity(); // connexion et renvoi vers la page sommaire
            }
        } else { // ERRORS
            if (response == null) {
                showSnackBar(getString(string.error_authentication_canceled));
            }
            assert response != null;
            if (Objects.requireNonNull(response.getError()).getErrorCode() == ErrorCodes.NO_NETWORK) {
                showSnackBar(getString(string.error_no_internet));
            }
            if (Objects.requireNonNull(response.getError()).getErrorCode() == ErrorCodes.UNKNOWN_ERROR) {
                showSnackBar(getString(string.error_unknown_error));
            }
        }
    }

    /**
     * Methode permettant l'affichage de la snackbar.
     * Dans cette app, n'ayant pas besoin de coordinatorLayout, j'ai créé un champ View vide dans mon IHM MainActivity.
     * Cette methode n'a donc pas besoin de 'CoordinatorLayout' en param non plus mais de la view.
     *
     * @param message
     */
    private void showSnackBar(String message) {
        Snackbar.make(mTextViewHiddenForSnackbar, message, Snackbar.LENGTH_LONG).show();
    }



    // --------------------
    // REST REQUESTS
    // --------------------

    /**
     * Methode de creation d'un utilisateur, avec condition de creation en fonction de l'existance ou non d'un user dejà en bdd,
     * et decomposant le nom et le prenom saisi à l'enregistrement de la personne.
     */
    private void createUserInFirestore() {

        if (this.getCurrentUser() != null) {
            Query mQuery = setupDb().collection("users").whereEqualTo("uid", getCurrentUser().getUid());

            // RAJOUTER LE THIS DANS LE LUSTENER PERMET DE RESTREINDRE LE CONTEXT A CETTE ACTIVITE, EVITANT AINSI DE METTRE LES DONNEES
            // A JOUR A CHAUQE FOIS QU'IL Y A UN UPDATE DANS L'APP.
            // SI ON ENLEVE LE THIS, ON CREERA UN NOUVEAU DOCUMENT A CHAQUE FOIS QU'ON EN SUPPRIMERA UN, PAR EX !
            mQuery.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(QuerySnapshot documentSnapshots, FirebaseFirestoreException e) {
                    // condition de creation d'un user ou affichage simple d'un message indiquant que l'user existe dejà en bdd.
                    // Avec les uid, il ne peut y avoir de doublon.
                    if (documentSnapshots.size() == 1) {
                        Log.e("TAG", "Le document existe !");
                    } else {
                        // recuperation des données de l'user
                        String username = getCurrentUser().getDisplayName();
                        // decomposition du nom et du prenom recu dans username
                        String nom = null, prenom;
                        String[] parts;
                        assert username != null;
                        if (username.contains(" ")) {
                            parts = username.split(" ");
                            try {
                                if (parts[1] != null) nom = parts[1];
                                else nom = "";
                            } catch (ArrayIndexOutOfBoundsException e1) {
                                Log.e("TAG", "ArrayOutOfBoundException " + e1.getMessage());
                            }
                            if (parts[0] != null) prenom = parts[0];
                            else prenom = "";
                        } else {
                            nom = username;
                            prenom = "";
                        }
                        String uid = getCurrentUser().getUid();
                        String email = getCurrentUser().getEmail();

                        addNewUser(uid, nom, prenom, email);
                    }
                }
            });
        }
        // si l'utilisateur n'a pas de compte , on lui en fait creer un
        else {
            startSignInActivity();
        }
    }

    /**
     * Methode permettant de creer un user lorsque celui ci vient de se connecter pour la 1ere fois.
     *
     * @param uid
     * @param nom
     * @param prenom
     * @param email
     */
    private void addNewUser(String uid, String nom, String prenom, String email) {

        Map<String, Object> newContact = new HashMap<>();
        newContact.put("uid", uid);
        newContact.put("nom", nom);
        newContact.put("prenom", prenom);
        newContact.put("email", email);
        setupDb().collection("users").document(Objects.requireNonNull(getCurrentUser()).getUid()).set(newContact)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(MainActivity.this, string.create_account,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "ERROR" + e.toString(),
                                Toast.LENGTH_SHORT).show();
                        Log.d("TAG", e.toString());
                    }
                });
    }


    // --------------------
    // OBSERVATION DANS LE LOGCAT DE LA MEMOIRE UTILISEE POUR LE DEBUG DE MON TEL
    // --------------------

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    public void onTrimMemory(int level) {

        // Determine which lifecycle or system event was raised.
        switch (level) {

            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:

                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:

                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:
                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }
    }
}
