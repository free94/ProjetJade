import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class AgentConsommateur extends Agent {

	// quantité d'electricité (provenant d'un fournisseur) consommée par ce
	// consommateur par unité de temps,en KW
	private int consommation;
	// capacité de production du consommateur, en KW
	private int capaciteProduction;
	// fournisseur associé
	private AID monFournisseur;
	// Liste des agents connus, càd les fournisseurs trouvés lors du search dans
	// le DF
	private AID[] fournisseurs;
	//une liste pour enregistrer les factures
	private ArrayList<Facture> factures = new ArrayList<Facture>();

	protected void setup() {
		// obtenir les arguments passés lors de la création de l'agent
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			this.consommation = Integer.parseInt(args[0].toString());
			this.capaciteProduction = Integer.parseInt(args[1].toString());
		} else {
			// Terminer l'agent si les arguments sont incorrect
			System.out
					.println("Il manque des arguments pour l'instanciation de l'agentConsommateur");
			doDelete();
		}

		//Cherche à s'abonner à un fournisseur tous les 5 secondes si non abonné
		if (null==monFournisseur) {
			// recherche des fournisseurs dans le DF
				DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("Fournisseur");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(this, template);
						fournisseurs = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							fournisseurs[i] = result[i].getName();
						}
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// on dispose ici de la liste des fournisseurs disponibles, on va donc
					// lancer le nécessaire pour trouver celui ayant le prix le plus bas
					addBehaviour(new DemandeAbonnement());
		}
			
		//ajouter le traitement pour les factures recu
		addBehaviour(new EnregFacture());
		
		//Changer le founisseur aléatoirement tous les 5 secondes
		addBehaviour(new TickerBehaviour(this, 5000) {
			protected void onTick() {
					// recherche des fournisseurs dans le DF
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("Fournisseur");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template);
						fournisseurs = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							fournisseurs[i] = result[i].getName();
						}
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}

					//Desabonner d'abord puis chercher à abonner en raisonnant avec les prix
					myAgent.addBehaviour(new Desabonnement());
					myAgent.addBehaviour(new DemandeAbonnement());
				
			}
		} );
		
		// Message de Bonjour
		System.out.println("le consommateur " + getAID().getName()
				+ " est prêt.");
	}

	protected void takeDown() {
		System.out.println("le consommateur " + getAID().getName()
				+ " est terminé,");

	}
	
	//Comportement permettant au consommateur de se désabonner de l'AgentFournisseur
	private class Desabonnement extends OneShotBehaviour{
		private MessageTemplate mt;
		@Override
		public void action() {
			
			ACLMessage cfp = new ACLMessage(ACLMessage.INFORM);
			cfp.addReceiver(monFournisseur);
			cfp.setContent("Desabonnement");
			cfp.setConversationId("desabonnement");
			myAgent.send(cfp);
			monFournisseur = null;
			// ------------ Le contexte du projet fait que les consommateurs :
			// ------------------------ ne sont jamais engagés sur une durée
			// ------------------------ et n'auront jamais de problème lors d'envoi des messages
			// ------------ On peut donc ne pas attendre de réponse suite au désabonnement et considérer à l'envoi du message que le désabonnement est effectif
		}
		
	}
	
	//cherche à s'abonner à un fournisseur
	private class DemandeAbonnement extends Behaviour {
		private ArrayList<AID> fournisseurMoinsCher = new ArrayList<AID>(); // l'agent ayant la meilleure offre
		private int tarifMoinsCher; // Le tarif le plus bas
		private int nombreReponse = 0;
		private MessageTemplate mt; // le template dont on veut des réponses
		private int step = 0;

		public void action() {
			switch (step) {
			//demander les prix
			case 0:
				// Envoi de la demande de prix a tous les fournisseurs
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < fournisseurs.length; ++i) {
					cfp.addReceiver(fournisseurs[i]);
				}
				cfp.setContent("DemandePrix");
				cfp.setConversationId("tarif-energie");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique																		// value
				myAgent.send(cfp);
				// Preparer le template pour recevoir la réponse
				mt = MessageTemplate.and(
						MessageTemplate.MatchConversationId("tarif-energie"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			//choisir le(s) meilleur prix
			case 1:
				// Reception de toutes les réponses/rejets des fournisseurs
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						int prix = Integer.parseInt(reply.getContent());
						if (fournisseurMoinsCher.size() == 0 || tarifMoinsCher == prix) {
							tarifMoinsCher = prix;
							fournisseurMoinsCher.add(reply.getSender());
						} else if (tarifMoinsCher > prix){
							fournisseurMoinsCher.clear();
							fournisseurMoinsCher.add(reply.getSender());
						}
					}
					nombreReponse++;
					if (nombreReponse >= fournisseurs.length) {
						// We received all replies
						step = 2;
					}
				} else {
					block();
				}
				break;
			//Demander un abonnement
			case 2:
							
				int indexFour = 0;
				//selection aléatoire parmis nos fournisseurs
				if (fournisseurMoinsCher.size()>1) {
					Random rm = new Random();
					indexFour = rm.nextInt(fournisseurMoinsCher.size());
				}
				//envoi du message d'abonnement
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(fournisseurMoinsCher.get(indexFour));
				// le contenu est la quatité d'abonnement souhaitée
				order.setContent("" + consommation);
				order.setConversationId("abonnement");
				order.setReplyWith("" + System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(
						MessageTemplate.MatchConversationId("abonnement"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			//Traiter la réponse d'abonnement d'un fournisseur
			case 3:
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// INFORM dans le cas où le fournisseur a accepté notre abonnement
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Le fournisseur accepte notre demande d'abonnement
						// (il dispose suffisament de capacité)						
						monFournisseur = reply.getSender();
						System.out
								.println(myAgent.getLocalName()+">>Abonnement effectué avec le fournisseur: "
										+ reply.getSender().getLocalName()
										+ " pour une quantité de: "
										+ consommation + "KW au prix de: "+tarifMoinsCher);
					}
					else {
						//Notre demande est refusé par le fournisseur
						System.out.println(myAgent.getLocalName()+">>Abonnement refusé: pas assez de capacité de production!");
					}
					step = 4;
				} else {
					block();
				}
				break;
			}
		}

		public boolean done() {
			if (step == 2 && fournisseurMoinsCher == null) {
				System.out.println(myAgent.getLocalName()+">>les capacités de production de tous les fournisseurs sont saturées!");
			}
			return ((step == 2 && fournisseurMoinsCher == null) || step == 4);
		}
	}
	
	//Enregistrement des factures et ses paiement 
	//le message de retour simule LA CONFIRMATION DE LIVRAISON d'énergie
	private class EnregFacture extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("facture"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					Facture facture =  (Facture) msg.getContentObject();
					if (null!=facture) {
						//System.out.println(facture.toString());
						factures.add(facture);
						//payer le facture par un message de retour au founrisseur
						ACLMessage reply = msg.createReply();
						reply.setPerformative(ACLMessage.CONFIRM);
						reply.setConversationId("paiementFacture");
						reply.setContent((int)facture.getMontant()+"");
						myAgent.send(reply);
					}
					else {
						System.out.println("Erreur: la facture recu ne contient aucune information");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}				
			}
			else {
				block();
			}
		}
	}
}
