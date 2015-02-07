import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

//agent de transport que tout fournisseur peut créer
public class AgentTransportFournisseur extends Agent {

	// nos variables
	private AID createur = null;// AID du créateur du transporteur
	private AID abonne = null;// Un seul fournisseur peut utiliser la ligne de
								// transport créer par un fournisseur
	private int CA = 0;
	private int benefice = 0;
	// Cout de transport pour l'ensembe de sa capacité
	private int coutTransport = 0;
	private int prixTransport = 70;// prix de l'utilisation de la ligne de
									// transport pour un
									// autre fournisseur que le créateur ! Peut
									// être maj
	private boolean disponible = false;// indique si le transporteur est laissé
										// disponible par son créateur pour les
										// autres fournisseurs en début de tour
										// puis indique simplement si le
										// transporteur peut être pris par un
										// fournisseur ou pas
	private static int capaciteTransport = 30;
	// profit réalisé dans un tour
	private int profit = 0;
	private int nbClient = 0;

	protected void setup() {
		// Récupére son créateur et lui informer de notre AID
		Object[] args = getArguments();
		createur = (AID) args[0];
		ACLMessage informeAID = new ACLMessage(ACLMessage.INFORM);
		informeAID.addReceiver(createur);
		informeAID.setConversationId("aidTransporteurCree");
		try {
			informeAID.setContentObject(getAID());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		send(informeAID);
		// Enregistrement du service dans le DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("TransporteurFournisseur");
		sd.setName(getAID().getLocalName());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		addBehaviour(new ServiceDemandeTarif());
		addBehaviour(new ServiceReservation());
		addBehaviour(new ServiceFacturation());
		addBehaviour(new ServiceObservateur());
		addBehaviour(new ServiceReceptionPaiement());
		addBehaviour(new ServiceinitDisponibilite());
		addBehaviour(new ServiceDemandeProfit());
		addBehaviour(new ServiceMAJPrixTransport());

		System.out.println("Le transporteur fournisseur: " + getAID().getName()
				+ " est prêt.");
	}

	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Le transporteur fournisseur: " + getAID().getName()
				+ " est terminé.");
	}

	// comportement en cas de demande du tarif de location de la ligne de
	// transport -> réponse favorable uniquement si disponible
	private class ServiceDemandeTarif extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
					MessageTemplate.MatchConversationId("demandeTarif"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage msg1 = msg.createReply();
				try {
					msg1.addReceiver(msg.getSender());
					msg1.setConversationId("tarifTransporteurFournisseur");
					if (disponible == false) {
						msg1.setPerformative(ACLMessage.REJECT_PROPOSAL);
					} else {
						msg1.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
						int date = (int) System.currentTimeMillis();
						Devis devis = new Devis(msg.getSender(),
								myAgent.getAID(), capaciteTransport, date,
								prixTransport);
						msg1.setContentObject(devis);
					}
					myAgent.send(msg1);
				} catch (IOException e) {
					System.out.println("Erreur génération de facture");
				}
			} else {
				block();
			}
		}
	}

	private class ServiceinitDisponibilite extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("initDisponibilite"),
					MessageTemplate.MatchSender(createur));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					disponible = (Boolean) msg.getContentObject();
				} catch (UnreadableException e) {
					e.printStackTrace();
				}
			} else {
				block();
			}
		}
	}

	// comportement lorsqu'on souhaite réserver la ligne de transport (le rendre
	// indisponible et l'utiliser)
	// même fonction que ça soit lorsqu'il s'agit du créateur(si cela n'a pas
	// décider de garder cet transporteur à l'interne du tour précédent) ou d'un
	// autre fournisseur mais pas dans le contexte fin de tour
	// où les créateurs on des droits supplémentaires : cf fonction
	// reserverCreateur()
	// SI disponible, on se met indisponible, on indique que notre abonne est
	// l'expéditeur du message et on le facture directement
	private class ServiceReservation extends CyclicBehaviour {
		private int montant = 0;
		ACLMessage msg1;

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("demandeReservation"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// SI la demande provient du créateur ET qu'on ne s'est pas
				// engagé avec un autre fournisseur
				if (disponible) {
					disponible = false;
					abonne = msg.getSender();
					nbClient = 1;
					if (msg.getSender() == createur) {
						montant = 0;
					} else {
						montant = prixTransport;
					}
					// la demande a été faite en étant disponible, on y répond
					// favorablement que ça soit pourle créateur ou un autre
					msg1 = new ACLMessage(ACLMessage.AGREE);
					try {
						msg1.addReceiver(abonne);
						msg1.setContentObject(montant);
						msg1.setConversationId("reponseReservation");
						myAgent.send(msg1);
					} catch (IOException e) {
						System.out
								.println("Probleme envoie message acceptation reservation transporteurFournisseur : "
										+ myAgent.getLocalName());
					}

				}
				// Sinon si la demande provient d'un fournisseur autre
				else {
					// Si on nous fait la demande de réservation mais qu'on est
					// indisponible
					msg1 = new ACLMessage(ACLMessage.REFUSE);
					try {
						msg1.addReceiver(msg.getSender());
						msg1.setConversationId("reponseReservation");
						myAgent.send(msg1);
					} catch (Exception e) {
						System.out
								.println("Probleme envoie message refus reservation transporteurFournisseur : "
										+ myAgent.getLocalName());
					}

				}
			} else {
				block();
			}
		}

	}

	private class ServiceFacturation extends CyclicBehaviour {

		private int montant = 0;
		private AID receiver;

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("honorerContrat"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// Si la demande provient du créateur et qu'on a bien que lui
				// comme client
				if (createur == msg.getSender()) {
					montant = 0;
					receiver = createur;
					nbClient = 1;
				}
				// Sinon c'est que la demande vient d'un abonné qui n'est pas
				// notre créateur
				else if (msg.getSender() != createur) {
					montant = prixTransport;
					receiver = abonne;
					nbClient = 1;
				} else {
					System.out
							.println("ERREUR : le transporteurFournisseur :"
									+ myAgent.getLocalName()
									+ " a une demande de facturation d'un fournisseur qui n'est pas son client du tour, le fournisseur : "
									+ msg.getSender());
				}

				FactureTransporteur f = new FactureTransporteur(getLocalName(),
						montant);
				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				try {
					msg1.addReceiver(receiver);
					msg1.setContentObject(f);
					msg1.setConversationId("factureTransport");
					myAgent.send(msg1);
					benefice -= coutTransport;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Erreur génération de facture");
				}
			} else {
				block();
			}
		}
	}

	private class ServiceObservateur extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("observateur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage reply = msg.createReply();
				int dateActuelle = (int) System.currentTimeMillis();
				InfoAgent info = new InfoAgent(getLocalName(), nbClient + "",
						CA + "", benefice + "", prixTransport + "",
						capaciteTransport + "");
				nbClient = 0;
				try {
					reply.setContentObject(info);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				myAgent.send(reply);

			} else {
				block();
			}

		}

	}

	private class ServiceReceptionPaiement extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchPerformative(ACLMessage.CONFIRM), MessageTemplate
					.MatchConversationId("paiementFactureTransporteur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				CA += Integer.parseInt(msg.getContent());
				benefice += Integer.parseInt(msg.getContent());
			} else {
				block();
			}
		}
	}

	private class ServiceDemandeProfit extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchSender(createur),
					MessageTemplate.MatchConversationId("demandeProfit"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage msg1 = msg.createReply();
				if (disponible) {
					msg1.setPerformative(ACLMessage.DISCONFIRM);
				} else {
					msg1.setPerformative(ACLMessage.CONFIRM);
				}
				msg1.setConversationId("reponseProfit");
				msg1.setContent(profit + "");
				profit = 0;
				myAgent.send(msg1);

			} else {
				block();
			}

		}

	}

	private class ServiceMAJPrixTransport extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchSender(createur),
					MessageTemplate.MatchConversationId("MAJPrixTransport"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				prixTransport = Integer.parseInt(msg.getContent());

			} else {
				block();
			}
		}

	}
}
