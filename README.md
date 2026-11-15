# Notification Lab

Application Android Java (Android 5.0+) de test de notifications locales.

- Titre et description obligatoires.
- Icône vectorielle cloche ou étoile ; image facultative via le sélecteur de documents.
- Envoi immédiat ou programmation à une date et heure locales.
- Liste persistante des notifications programmées, avec annulation individuelle.
- Reprogrammation après redémarrage et mise à jour de l’application.
- Demande d’autorisation Android 13+, accès aux paramètres et possibilité de réessayer les envois bloqués.

La programmation utilise des alarmes indicatives : Android peut retarder leur livraison. Une fermeture normale de l’application ne supprime pas les alarmes ; un arrêt forcé dans les paramètres Android empêche leur exécution. Les images supprimées ou devenues inaccessibles sont remplacées par une notification textuelle. Les icônes de statut sont monochromes conformément au fonctionnement Android.

## Structure Android Java

- `app/src/main/res/layout/activity_main.xml` : écran principal modifiable dans Android Studio.
- `app/src/main/res/layout/item_scheduled_notification.xml` : présentation d’une notification dans la liste.
- `app/src/main/res/values/strings.xml` : textes, messages et choix des icônes.
- `MainActivity.java` : liaison des vues avec `findViewById` et gestion des interactions.
- `NotificationStore.java` et `NotificationReceiver.java` : stockage, programmation et livraison.

## Vérification

`./gradlew :app:assembleDebug :app:lintDebug`

Scénarios à vérifier sur téléphone ou émulateur :

1. Refuser puis autoriser les notifications ; vérifier les messages et l’accès aux paramètres.
2. Envoyer un titre et une description, puis recommencer avec chaque icône et une image.
3. Programmer deux notifications futures ; annuler l’une et attendre l’autre (délai système possible).
4. Fermer et rouvrir l’application, puis redémarrer le téléphone : vérifier la conservation des programmations.
5. Tourner l’écran pendant la saisie : vérifier titre, description, icône, image et date.
6. Désactiver les notifications avant l’échéance, puis réactiver et utiliser « Réessayer maintenant ».
7. Vérifier les erreurs pour les champs vides et une date passée.
