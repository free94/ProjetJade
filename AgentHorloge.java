import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class AgentHorloge extends Agent {
	// Durée d'un tic d'horloge, en unité de ms
	private int periodeTic = 1000;
	// Les fournisseur inscrit dans le système
	private ArrayList<AID> listeFour = new ArrayList<AID>();
	// Les fournisseur vers lesquels on a envoyé les messages de début de tour
	// et attend leur réponses
	private ArrayList<AID> listeFourReponse = new ArrayList<AID>();
	// Les transporteurs inscrit dans le système
	private ArrayList<AID> listeTrans = new ArrayList<AID>();
	// Les consommateurs inscrit dans le système
	private ArrayList<AID> listeConso = new ArrayList<AID>();
	// Les consommateurs vers lesquels on a envoyé les messages de début de tour
	// et attend leur réponses
	private ArrayList<AID> listeConsoReponse = new ArrayList<AID>();
	// L'observateur
	private AID observateur = null;

	protected void setup() {
		// Enregistrement du service dans le DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Horloge");
		sd.setName(getAID().getLocalName());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// ajouter le service pour traiter les demandes d'inscription des
		// fournisseur/Transporteur/observateur
		addBehaviour(new ServiceInscription());

		// Traiter les messages de fin de tour à chaque tic d'horloge
		addBehaviour(new TickerBehaviour(this, periodeTic) {
			protected void onTick() {
				addBehaviour(new Tour());
			}
		});

		System.out.println("L'horloge : " + getAID().getName() + " est prêt.");
	}

	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("L'agent horlogue est terminé.");
	}

	private class ServiceInscription extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("inscriptionHorloge"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String demandeur = msg.getContent();
				if (demandeur.equals("fournisseur")) {
					listeFour.add(msg.getSender());
				} else if (demandeur.equals("transporteur")) {
					listeTrans.add(msg.getSender());
				} else if (demandeur.equals("observateur")) {
					observateur = msg.getSender();
				} else if (demandeur.equals("consommateur")) {
					listeConso.add(msg.getSender());
				}
				System.out.println("un agent " + demandeur + ":"
						+ msg.getSender().getLocalName()
						+ " est incrit à l'horloge");
			} else {
				block();
			}

		}
	}

	private class Tour extends Behaviour {
		private int step = 0;
		private MessageTemplate mtFour = MessageTemplate
				.MatchConversationId("msgFinDeTourFour");
		private MessageTemplate mtConso = MessageTemplate
		.MatchConversationId("msgFinDeTourConso");
		private MessageTemplate mtObs = MessageTemplate
		.MatchConversationId("msgFinDeTourObs");
		@Override
		public void action() {
			switch (step) {
			case 0:
				// Reception de messages fin de tour fournisseurs
				if (listeFourReponse.size() == 0) {
					// Tous les messages fin de tour de fournisseur sont recu
					step = 1;
				} else {
					ACLMessage msg = receive(mtFour);
					if (msg != null) {
						listeFourReponse.remove(msg.getSender());
					}
				}
				break;
			case 1:
				// Emettre les messages debut de tour
				ACLMessage cmd = new ACLMessage(ACLMessage.INFORM);
				if (observateur!=null) {
					cmd.addReceiver(observateur);
				}
				for (AID fournisseur : listeFour) {
					cmd.addReceiver(fournisseur);
					listeFourReponse.add(fournisseur);
				}
				for (AID consommateur : listeConso) {
					cmd.addReceiver(consommateur);
					listeConsoReponse.add(consommateur);
				}
				cmd.setConversationId("msgDebutTour");
				myAgent.send(cmd);
				step = 2;
				break;
			case 2:
				// Reception de messages fin de tour des consommateurs
				if (listeConsoReponse.size() == 0) {
					// Tous les messages fin de tour de consommateurs sont recu
					step = 3;
				} else {
					ACLMessage msg = receive(mtConso);
					if (msg != null) {
						listeConsoReponse.remove(msg.getSender());
					} 
				}
				break;
			case 3:
				// envoie les messages finConso aux fournisseurs
				//pour les demander de commencer la phase de décison
				//pour les politiques de prix
				// Emettre les messages debut de tour
				ACLMessage msgFinConso = new ACLMessage(ACLMessage.INFORM);
				for (AID fournisseur : listeFour) {
					msgFinConso.addReceiver(fournisseur);
				}
				msgFinConso.setConversationId("msgFinTourConso");
				myAgent.send(msgFinConso);
				step = 4;
				break;
			case 4:
				// Reception de messages fin de tour fournisseurs
				if (observateur!=null) {
					ACLMessage msg = receive(mtObs);
					if (msg != null) {
						step = 5;
					} 
				} else {
					step = 5;
				}
				break;
			
			}


		}

		@Override
		public boolean done() {
			if (step == 5) {
				return true;
			}
			return false;
		}

	}

}
