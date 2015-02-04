import java.util.ArrayList;
import java.util.Random;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

/*TODO
 * AgentTransport
 * 		Classe abstrait ou interface contient les déclarations des méthodes: facturation,getCapacite,getPrix,getType??,finDeTour
 *
 * 
 * AgentTransport Hérite d'AgentTransport
 * 		
 * 		-facturer le transport de l'elec quand le fournisseur le demande 														: DONE (Florian)
 * 		-envoie du message "fin de tour" au agent horloge
 * 
 * AgentTransportPerso Hérite d'AgentTransport
 * 		-Chaque fournisseur peut créer une instance de cet agent : un transporteur pour lui même
 * 		-envoie du message "fin de tour" au agent horloge
 * 
 * AgentFournisseur 
 * 		-Doit émettre une requête pour avoir un devis d'un AgentTransport d'une autre compagnie 								: DONE en partie (Florian)
 * 		-Doit informer le client du transport
 * 		-Doit verser une indemnité au client si problème de transport :Comment juger??
 * 		-Doit retirer le client de sa liste d'abonnés si le client lui en fait la demande										: DONE (Florian)
 * 		-envoie du message "fin de tour" au agent horloge
 * 
 * AgentConsommateur
 * 		-Doit pouvoir demander le désabonnement 																				: DONE (Florian)
 * 		-Doit contrôler si son fournisseur reçoit bien la notification du transport : si non désabonnement
 * 		-Changement aléatoire de fournisseurs (pas trop souvent)
 * 		-Améliorer le switch (par un choix aléatoire des fournisseurs au début)
 * 		-envoie du message "fin de tour" au agent horloge
 * 
 * AgentHorloge
 * 		-Doit pouvoir gérer un système de "tour" ou "round" pour déclencher seulement la phase de facturation une fois que tous les agents sont "prêts"
 * 		*un premier mécanisme proposé pour ca: chaque Agent one deux attributs: tourActuel et tourProchain. 
 * 		A l'initalisation, les deux sont 0 et 1, Tant que tourActuel<tourProchain, les agents peuvent effectuer
 * 		ses comportements cycliques puis incrémente tourActuel par 1 (pour simuler le fait que ce comportement se fait une fois dans un tour).
 * 		pour tous les comportement cyclique, les délai de répétition de ces comportement doivent etre inférieur
 * 		que un "tic d'horloge" pour éviter d'introduire trop de temps d'attente (?? je ne suis pas sur pour ca).
 * 		Seule la reception des messages de retour d'agentHorlogue fait incrémenter le round
 * 		vont etre légèrement inférieur à la période "tic d'horloge"
 * 		*Autre approche possible: on remplace tous les comportements cyclique d'un "tic d'horloge" par 
 * 		des comportement OneTime (ou des méthodes tout simplement, pour éviter d'instancier les objets 
 * 		à chaque fois et ca seront plus simple à coder) déclanché par un message de retour d'agentHorlogue (et ca va etre le
 * 		comportement qui gere ca qui est le seul cyclique) -> plus simple que le premier à mon avis (Florian : +1 pour la 2nde proposition)
 * 
 * Devis
 * 		-Doit contenir tout le nécessaire pour que le transporteur puisse repondre à la demande de devis du fournisseur 		: DONE (Florian)
 * 		??Est-il vraiment necessaire de créer une classe pour ce fait? on ne peut pas intégré ca dans une négociation (comme le choix du meilleur prix fournisseur par les clients)
 * 		Florian : c'est discutable en effet =) perso je trouvais ça plus propre et plus orienté java, dnc comme c'est ce qu'on fait globalement j'trouvais ça mieux
 * 		Mais on peut en discuter ;)
 * 
 * */

public class Main {

	/*
	 * void methode() throws StaleProxyException{
	 * 
	 * }
	 */
	private int nombreFournisseur = 0;

	static public void main(String[] args) throws StaleProxyException {
//		/* Création du runtime */
//		Runtime rt = Runtime.instance();
//		rt.setCloseVM(true);
//		/* Lancement de la plateforme */
//		Profile pMain = new ProfileImpl("localhost", 8888, null);
//		AgentContainer mc = rt.createMainContainer(pMain);
//		/* Lancement des agents */
//		//Initialiser d'abord l'agent horloge
//		AgentController horloge = mc.createNewAgent("horloge", AgentHorloge.class.getName(), null);
//		horloge.start();
//		//Transporteur principal
//		AgentController transPrincipal = mc.createNewAgent("transporteurPrincipal", AgentTransportPrincipal.class.getName(), null);
//		transPrincipal.start();
//		//Fournisseurs
//		AgentController fournisseur1 = mc.createNewAgent("fournisseur1",
//				AgentFournisseur.class.getName(), null);
//		fournisseur1.start();
//		AgentController fournisseur2 = mc.createNewAgent("fournisseur2",
//				AgentFournisseur.class.getName(), null);
//		fournisseur2.start();
//		AgentController fournisseur3 = mc.createNewAgent("fournisseur3",
//				AgentFournisseur.class.getName(), null);
//		fournisseur3.start();
//		AgentController observateur = mc.createNewAgent("observateur",
//				AgentObservateur.class.getName(), null);
//		observateur.start();
//		//Consommateurs
//		int maxConso = 5;
//		int conso = 0;
//		Random rand = new Random();
//		ArrayList<AgentController> listeClient = new ArrayList<AgentController>();
//		for (int i = 0; i < 20; i++) {
//			conso = rand.nextInt(maxConso);
//			String[] argsConsommateur1 = { ""+conso, "0" };
//			AgentController consommateur1 = mc.createNewAgent("consommateur"+i,
//					AgentConsommateur.class.getName(), argsConsommateur1);
//			consommateur1.start();
//			listeClient.add(consommateur1);
//		}
		
		/* Création du runtime */
		Runtime rt = Runtime.instance();
		rt.setCloseVM(true);
		/* Lancement de la plateforme */
		Profile pMain = new ProfileImpl("localhost", 8888, null);
		AgentContainer mc = rt.createMainContainer(pMain);
		/* Lancement des agents */
		//Initialiser d'abord l'agent horloge
		AgentController horloge = mc.createNewAgent("horloge", AgentHorloge.class.getName(), null);
		horloge.start();
		//Transporteur principal
		AgentController transPrincipal = mc.createNewAgent("transporteurPrincipal", AgentTransportPrincipal.class.getName(), null);
		transPrincipal.start();
		//Fournisseurs
		AgentController fournisseur1 = mc.createNewAgent("fournisseur1",
				AgentFournisseur.class.getName(), null);
		fournisseur1.start();
		AgentController observateur = mc.createNewAgent("observateur",
				AgentObservateur.class.getName(), null);
		observateur.start();
		//Consommateurs
		int maxConso = 10;
		int conso = 0;
		Random rand = new Random();
		ArrayList<AgentController> listeClient = new ArrayList<AgentController>();
		for (int i = 0; i < 2; i++) {
			conso = rand.nextInt(maxConso);
			String[] argsConsommateur1 = { ""+conso, "0" };
			AgentController consommateur1 = mc.createNewAgent("consommateur"+i,
					AgentConsommateur.class.getName(), argsConsommateur1);
			consommateur1.start();
			listeClient.add(consommateur1);
		}
		
	}
}
