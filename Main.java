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


public class Main {

	/*
	 * void methode() throws StaleProxyException{
	 * 
	 * }
	 */
	private int nombreFournisseur = 0;

	static public void main(String[] args) throws StaleProxyException {

		/* Création du runtime */
		Runtime rt = Runtime.instance();
		rt.setCloseVM(true);
		/* Lancement de la plateforme */
		Profile pMain = new ProfileImpl("localhost", 8888, null);
		AgentContainer mc = rt.createMainContainer(pMain);
		/* Lancement des agents */
		// Initialiser d'abord l'agent horloge
		AgentController horloge = mc.createNewAgent("horloge",
				AgentHorloge.class.getName(), null);
		horloge.start();
		// Transporteur principal
		AgentController transPrincipal = mc.createNewAgent(
				"transporteurPrincipal",
				AgentTransportPrincipal.class.getName(), null);
		transPrincipal.start();
		// Fournisseurs
		AgentController fournisseur1 = mc.createNewAgent("fournisseur1",
				AgentFournisseur.class.getName(), null);
		fournisseur1.start();
		AgentController fournisseur2 = mc.createNewAgent("fournisseur2",
				AgentFournisseur.class.getName(), null);
		fournisseur2.start();
		AgentController fournisseur3 = mc.createNewAgent("fournisseur3",
				AgentFournisseur.class.getName(), null);
		fournisseur3.start();
		AgentController observateur = mc.createNewAgent("observateur",
				AgentObservateur.class.getName(), null);
		observateur.start();
		// Consommateurs
		int maxConso = 10;
		int conso = 0;
		Random rand = new Random();
		ArrayList<AgentController> listeClient = new ArrayList<AgentController>();
		for (int i = 0; i < 20; i++) {
			conso = rand.nextInt(maxConso);
			String[] argsConsommateur1 = { "" + conso, "0" };
			AgentController consommateur1 = mc.createNewAgent("consommateur"
					+ i, AgentConsommateur.class.getName(), argsConsommateur1);
			consommateur1.start();
			listeClient.add(consommateur1);
		}

	}
}
