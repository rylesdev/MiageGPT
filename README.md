# MiageGPT

MiageGPT est un agent conversationnel développé en Java/JavaFX/CSS, conçu pour répondre aux questions des étudiants MIAGE de l'Université Paris 1 Panthéon-Sorbonne. Le projet a été réalisé en partenariat avec l'Association MIAGE Sorbonne (AMS), développé en équipe selon la méthode Agile Scrum.

L'application permet aux étudiants d'obtenir des réponses rapides sur les cours, les débouchés, les stages et la vie associative MIAGE, via une interface graphique intuitive.

## Stack technique

- Java 21 / JavaFX
- API Groq (LLM)
- API Neon (BDD)
- Agile Scrum

## Téléchargement

Une release est disponible directement sur GitHub, permettant de lancer l'application sans compiler le projet.

Rendez-vous dans l'onglet **Releases** du repository et téléchargez la dernière version du fichier `MiageGPT.jar`.

Java 21+ doit être installé sur votre machine pour exécuter le fichier.

## Prérequis

- Java 21+
- Une clé API Groq (gratuite)

## Configuration de la clé API Groq

Au premier lancement, une fenêtre de configuration s'affiche automatiquement. Voici les étapes à suivre :

1. Cliquez sur le lien https://console.groq.com/keys dans la fenêtre
2. Créez un compte ou connectez-vous sur le site Groq
3. Cliquez sur **Create API Key** et copiez la clé générée (elle commencera par `gsk_`)
4. Collez la clé dans le champ de la fenêtre de configuration
5. Cliquez sur **Confirmer**

Aux lancements suivants, si la clé est toujours valide, alors vous atterrirez directement sur MiageGPT. Sinon, la fenêtre de configuration de la clé API s'affichera de nouveau pour vous permettre de la mettre à jour.