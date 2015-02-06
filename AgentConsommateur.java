import java.io.IOException;
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
	private AID monFournisseur = null;
	// Liste des agents connus, càd les fournisseurs trouvés lors du search dans
	// le DF
	private AID[] fournisseurs;
	//la dernière facture recue
	private Facture facture;
	// Agent horloge
	private AID horloge;
	// Random pour générer les quantité de consommation et de production
	private Random rand = new Random();
	// prix d'abonnement pour s'abonner à un fournisseur
	private int prixAbonnement = 20;
	// Compter le nombre de tours passés pour la décision de changement de
	// fournisseurs
	private int compteurTours = 0;
	// prix d'achat et de revente d'électricité au fournisseur actuel
	private int prixAchat = 0;
	private int prixVente = 0;

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

		// Récupérer l'agent horloge
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription serviceDesc = new ServiceDescription();
		serviceDesc.setType("Horloge");
		template.addServices(serviceDesc);
		DFAgentDescription[] result;
		try {
			result = DFService.search(this, template);
			this.horloge = result[0].getName();
		} catch (FIPAException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// Inscription à l'hologe
		addBehaviour(new Inscription(horloge, "consommateur"));

		// Cherche à s'abonner à un fournisseur tous les 5 secondes si non
		// abonné
		if (null == monFournisseur) {
			// recherche des fournisseurs dans le DF
			template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Fournisseur");
			template.addServices(sd);
			try {
				DFAgentDescription[] result1 = DFService.search(this, template);
				fournisseurs = new AID[result1.length];
				for (int i = 0; i < result1.length; ++i) {
					fournisseurs[i] = result1[i].getName();
				}
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

			// on dispose ici de la liste des fournisseurs disponibles, on va
			// donc
			// lancer le nécessaire pour trouver celui ayant le prix le plus bas
			addBehaviour(new DemandeAbonnement(true));
		}
		addBehaviour(new ServiceTour());
		// ajouter le traitement pour les factures recu
		addBehaviour(new EnregFacture());
		


		
		// Message de Bonjour
		System.out.println("le consommateur " + getAID().getName()
				+ " est prêt.");
	}

	protected void takeDown() {
		System.out.println("le consommateur " + getAID().getName()
				+ " est terminé,");

	}

	// Comportement permettant au consommateur de se désabonner de
	// l'AgentFournisseur
	private class Desabonnement extends OneShotBehaviour {
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
			// ------------------------ et n'auront jamais de problème lors
			// d'envoi des messages
			// ------------ On peut donc ne pas attendre de réponse suite au
			// désabonnement et considérer à l'envoi du message que le
			// désabonnement est effectif
		}

	}

	// cherche à s'abonner à un fournisseur en raisonnant avec le prix
	// d'abonnement (cf. rapport)
	// On ne regard pas le prix d'abonnement pour le premier abonnement premier
	// = true
	// (ou pour forcer un changement)
	private class DemandeAbonnement extends Behaviour {
		// Ecart entre prix d'achat et prix de vente pour notre fournisseur
		// actuel
		private int ecart;
		// Les attributs suivants se sert pour l'abonnement
		private ArrayList<AID> meilleurFournisseur = new ArrayList<AID>(); // l'agent
																			// ayant
																			// la
																			// meilleure
																			// offre
		private int meilleurEcart; // Le tarif le plus bas
		private int nombreReponse = 0;
		private MessageTemplate mt; // le template dont on veut des réponses
		private int step = 0;

		public DemandeAbonnement(boolean premier) {
			if (premier || monFournisseur == null) {
				// Mettre une valeur qui favorisera le changement
				ecart = -1-prixAbonnement;
			} else {
				ecart = prixVente - prixAchat;
			}
		}

		public void action() {
			switch (step) {
			// demander les prix de vente et d'achat
			case 0:
				// Envoi de la demande de prix a tous les fournisseurs
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < fournisseurs.length; ++i) {
					cfp.addReceiver(fournisseurs[i]);
				}
				cfp.setContent("DemandePrix");
				cfp.setConversationId("tarif-energie");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique
																		// //
																		// value
				myAgent.send(cfp);
				// Preparer le template pour recevoir la réponse
				mt = MessageTemplate.and(
						MessageTemplate.MatchConversationId("tarif-energie"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			// choisir le(s) meilleur prix
			case 1:
				// Reception de toutes les réponses/rejets des fournisseurs
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						int[] devis;
						try {
							devis = (int[]) reply.getContentObject();
							// nouveau ecart = prix vente - prix achat
							int nvEcart = devis[0] - devis[1];
							if (meilleurFournisseur.size() == 0
									|| meilleurEcart == nvEcart) {
								meilleurEcart = nvEcart;
								meilleurFournisseur.add(reply.getSender());
							} else if (meilleurEcart > nvEcart) {
								meilleurFournisseur.clear();
								meilleurFournisseur.add(reply.getSender());
							}
						} catch (UnreadableException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
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
			// Demander un abonnement ou non
			case 2:
				if ((meilleurEcart-ecart)*5<=prixAbonnement) {
					//On ne change pas de fournisseur si l'écart cumulé dans
					//5 tours ne dépasse pas prix d'abonnement
					step = 4;
					break;
				}
				//On rentre dans la décision d'abonnement
				//désabonne si on a déja un fournisseur
				if (monFournisseur!=null) {
					myAgent.addBehaviour(new Desabonnement());
				}
				int indexFour = 0;
				// selection aléatoire parmis nos fournisseurs moins chers
				if (meilleurFournisseur.size() > 1) {
					Random rm = new Random();
					indexFour = rm.nextInt(meilleurFournisseur.size());
				}
				// envoi du message d'abonnement
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(meilleurFournisseur.get(indexFour));
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
			// Traiter la réponse d'abonnement d'un fournisseur
			case 3:
				reply = myAgent.receive(mt);
				if (reply != null) {
					// INFORM dans le cas où le fournisseur a accepté notre
					// abonnement
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Le fournisseur accepte notre demande d'abonnement
						// (il dispose suffisament de capacité)
						monFournisseur = reply.getSender();
						int[] devis;
						try {
							devis = (int[]) reply.getContentObject();
							prixVente = devis[0];
							prixAchat = devis[1];
							System.out.println(myAgent.getLocalName()
									+ ">>Abonnement effectué avec le fournisseur: "
									+ reply.getSender().getLocalName()
									+ " pour une quantité de: " + consommation
									+ "KW au prix de vente: " + devis[0]
									+ " et prix d'achat: " + devis[1]);
						} catch (UnreadableException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					} else {
						// Notre demande est refusé par le fournisseur
						System.out
								.println(myAgent.getLocalName()
										+ ">>Abonnement refusé: pas assez de capacité de production!");
					}
					step = 4;
				} else {
					block();
				}
				break;
			}
		}

		public boolean done() {
			if (step == 2 && meilleurFournisseur == null) {
				System.out
						.println(myAgent.getLocalName()
								+ ">>les capacités de production de tous les fournisseurs sont saturées!");
			}
			return ((step == 2 && meilleurFournisseur == null) || step == 4);
		}
	}

	// Enregistrement des factures et ses paiement
	// le message de retour simule LA CONFIRMATION DE LIVRAISON d'énergie
	private class EnregFacture extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId("facture"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					Facture f = (Facture) msg.getContentObject();
					if (null != f) {
						// System.out.println(facture.toString());
						facture = f;
						//maj prix fournisseur
						prixVente = f.getPrixVente();
						prixAchat = f.getPrixAchat();
						// payer le facture par un message de retour au
						// founrisseur
						ACLMessage reply = msg.createReply();
						reply.setPerformative(ACLMessage.CONFIRM);
						reply.setConversationId("paiementFacture");
						reply.setContent((int) f.getMontant() + "");
						myAgent.send(reply);
					} else {
						System.out
								.println("Erreur: la facture recu ne contient aucune information");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				block();
			}
		}
	}

	private class ServiceTour extends CyclicBehaviour {

		@Override
		public void action() {
			// Transmet au fournisseur la production du tour actuel
			// et la prévision de la consommation au tour prochain
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("msgDebutTour"),
					MessageTemplate.MatchSender(horloge));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				compteurTours+=1;
				//Prévoir la consommation au prochain tour
				consommation = rand.nextInt(10);
				if (consommation == 0) {
					consommation = 1;
				}
				//production du tour actuel
				if (consommation < 2) {
					capaciteProduction = 0;
				}else {
					capaciteProduction = rand.nextInt(consommation/2);
				}
				
				//transmettre ces info au fournisseur
				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				msg1.addReceiver(monFournisseur);
				int[] infoConso = {consommation,capaciteProduction};
				try {
					msg1.setContentObject(infoConso);
					msg1.setConversationId("infoConso");
					myAgent.send(msg1);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.err.println("Erreur génération de facture");
				}
				//Essaie de changer le fournisseur tous les 5 tours
				if (compteurTours%5 == 0) {
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

					//chercher à abonner en raisonnant avec les prix
					myAgent.addBehaviour(new DemandeAbonnement(false));
				}
				//Enovie message fin de tour au horloge
				ACLMessage msgFinDeTour = new ACLMessage(ACLMessage.INFORM);
				msgFinDeTour.setConversationId("msgFinDeTourConso");
				msgFinDeTour.addReceiver(horloge);
				myAgent.send(msgFinDeTour);
			} else {
				block();
			}
			
		}
		
	}

}
