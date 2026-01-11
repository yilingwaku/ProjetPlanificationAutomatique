# Projet Planification Automatique
**Monte Carlo Tree Search Planning (MCTS)** VS **A-Star Planner (ASP)** 


Ce projet a pour objectif de comparer deux approches de planification automatique :
- ASP : algorithme de recherche informée, complet, utilisant une heuristique
- MCTS : algorithme de recherche stochastique basé sur des simulations


### Membres
- Jinyang Zhang
jinyang.zhang@etu.univ-grenoble-alpes.fr

- Matis Basso
matis.basso@etu.univ-grenoble-alpes.fr

- Nathanaël Rasoamana
rasambaharinosy.nathanael@etu.univ-grenoble-alpes.fr

## Les mesures d'évaluation

- taux de succès (succes) : plan trouvé ou non
- temps de calcul (runtime_ms) : temps d'exécution en millisecondes
- plan_length : longueur du plan
- timeout_killed : arrêt par dépassement du temps limite. Cette mesure révèle la robustesse face à la difficulté

## Les domaines d'évaluation

- Blocks World (dépendances entre actions et réordonnancement)

L’objectif est d’empiler et de déplacer des blocs sur une table afin d’atteindre une configuration cible. 

- Gripper (contraintes de capacité et de séquentialité des actions)

Un robot muni de deux pinces doit déplacer des objets entre différentes pièces. 

- Logistics (planification de trajets, allocation de ressources et coordination de plusieurs types d’actions)

Des colis doivent être déplacés entre des villes à l’aide de camions et d’avions. 

- Depots

Domaine hybride combinant Blocks World et Logistics. Transporter et empiler des caisses dans des dépôts en utilisant des camions et des grues.


# Comparaison des résultats


### **Taux de succès**

1. **Blocks World (STRIPS typed + untyped)**

- Pour ASP 
20 / 20 instances résolues (100 %)

- Pour MCTS
11 / 20 instances résolues (55 %).Échec systématique à partir de p006.

2. **Gripper (STRIPS + ADL)**

- Pour ASP 
7 instances résolues (Échecs à partir de p06 (timeouts))

- Pour MCTS
3 instances résolues
Échec quasi total dès p02–p03

3. **Logistics (toutes variantes)**

- Pour ASP 
4 instances résolues (adl p01, adl p05, strips-round2 p01–p02).
Mais il y a nombreux timeouts.

- Pour MCTS
0 instance résolue

***ASP résout plus de deux fois plus d’instances (≈ 31 que) MCTS (≈14)***

### **Temps de calcul**
- Pour ASP 

ASP (A*) est très rapide sur les petites instances, avec des temps d’exécution compris entre **10 et 100 ms dans le domaine Blocks** et d’environ **50 ms pour Gripper p01**. En revanche, le temps de calcul augmente fortement avec la difficulté du problème : il atteint **4518 ms pour Gripper p05** et **2168 ms pour Logistics p01**, et de nombreuses instances plus complexes se soldent par des timeouts à 120 000 ms, illustrant l’explosion combinatoire inhérente à la recherche A*.

***Temps très variable, mais lié à la difficulté réelle du problème***

- Pour MCTS

MCTS présente des temps d’exécution relativement stables, avec un temps compris **entre 500 et 650 ms lorsqu’il échoue**, et pouvant atteindre **30 à 90 secondes sur certaines instances du domaine Logistics**. Malgré ces temps parfois élevés, l’algorithme **s’arrête souvent sans atteindre le timeout**, tout en ne produisant aucune solution. Il y a donc **une difficulté à converger vers l’état but plutôt qu’un manque de temps de calcul**.

***Temps plus stable, mais sans produire de résultat utile***

### **Longueur du plan**

Lorsque les deux algorithmes parviennent à trouver un plan, **ASP produit des plans plus courts et plus efficaces que MCTS**. 

Dans le **domaine Blocks World** par exemple, **ASP génère des plans de 12 actions contre 28 pour MCTS sur l’instance p004**, et de **10 actions contre 24 sur p005**, tandis que **MCTS échoue sur des instances plus complexes comme p006 et p009**. 

De manière générale, **lorsque MCTS réussit, ses plans sont 2 à 3 fois plus longs que ceux d’ASP**. Cette tendance se confirme dans le **domaine Gripper (STRIPS)**, où ASP trouve un plan de **9 actions pour p01, contre 15 actions pour MCTS**.

### **Arrêt par dépassement du temps limite**

- Pour ASP 

Les échecs d’ASP sont principalement dus à des timeouts, ce qui reflète le coût élevé d’une exploration exhaustive de l’espace d’états lorsque la taille du problème augmente. Mais si suffisamment de temps lui est accordé, il est garanti de trouver une solution lorsqu’elle existe.

- Pour MCTS

MCTS échoue souvent rapidement, sans atteindre l’état but, ce qui montre une difficulté à orienter efficacement la recherche dans l’espace des états. Les échecs de MCTS ne sont donc pas liés au temps, mais à la structure du problème. 
