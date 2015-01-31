import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

//Agent de transport principal supposé appartenir à une compagnie tierce
public class AgentTransportPrincipal extends Agent {

	// prix de transport du KWh
	private static int prixTransport = 2;
	private static int coutTransport = 1;
	// *-*-*-*-*-*-*-*-*-*variables*-*-*-*-*-*-*-*-*-*
	private HashMap<AID, Devis> devis = new HashMap<AID, Devis>();
	private ArrayList<AID> abonnes = new ArrayList<AID>();
	private int CA = 0;
	private int benefice = 0;

	protected void setup() {
		// Enregistrement du service dans le DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("TransporteurPrincipal");
		sd.setName(getAID().getLocalName());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		//ajouter le comportement pour traiter les demandes de prix
		addBehaviour(new ServiceDemandePrix());
		//ajouter le comportement pour traiter les abonnements
		addBehaviour(new ServiceAbonnement());
		//ajouter le comportement pour traiter les desabonnements
		addBehaviour(new ServiceDesabonnement());
		//ajouter le comportement pour la facturation
		addBehaviour(new ServiceFacturation());
		//ajout du service pour recevoir les paiement des factures
		addBehaviour(new ServiceReceptionPaiement());
		//adoute du service pour fournir information à l'observateur
		addBehaviour(new ServiceObservateur());
	}

	protected void takeDown() {
		// deregister from the yellow pages
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("Le transporteur tierce: " + getAID().getName()
				+ " est terminé.");
	}

	//comportement lors de la demande du tarif au kwh : plus de notion de devis
	private class ServiceDemandeTarif extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("demandeTarif-transporteurPrincipal"), MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();
				try {
					reply.setPerformative(ACLMessage.PROPOSE);
					msg.setContentObject(prixTransport);
					msg.setConversationId("reponseTarif-transporteurPrincipal");
					myAgent.send(msg);					
				} catch (IOException e) {
					System.out.println("Erreur génération du devis");
				}
			} 
			else {
				block();
			}
		}
	}
	
	// comportement du transporteur quand il reçoit une demande d'un fournisseur
	// (facture)
	private class ServiceDemandePrix extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("demandeDevis"), MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// CFP Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				int quantiteDemandee = Integer.parseInt(msg.getContent());
				// creation du devis
				Devis d = new Devis(msg.getSender(), quantiteDemandee,
						(int) System.currentTimeMillis(), quantiteDemandee
								* prixTransport);
				try {
					// Le message est de type "propose" contient le devis d et a
					// pour conversation id "propositionDevis"
					reply.setPerformative(ACLMessage.PROPOSE);
					msg.setContentObject(d);
					msg.setConversationId("propositionDevis");
					myAgent.send(msg);
					// S'il n'y a pas eu de problème on ajoute ce devis à la
					// liste des devis émis
					devis.put(msg.getSender(), d);
				} catch (IOException e) {
					System.out.println("Erreur génération du devis");
				}
			} else {
				block();
			}
		}
	}

	private class ServiceAbonnement extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchPerformative(ACLMessage.REQUEST), MessageTemplate
					.MatchConversationId("abonnementTransporteur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				abonnes.add(msg.getSender());
				System.out.println("Le fournisseur "
						+ msg.getSender().getLocalName()
						+ " est abonné au transporteur principal");
			} else {
				block();
			}

		}
	}

	private class ServiceDesabonnement extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchPerformative(ACLMessage.REQUEST), MessageTemplate
					.MatchConversationId("desabonnementTransporteur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				if (abonnes.contains(msg.getSender())) {
					abonnes.remove(msg.getSender());
					System.out.println("Le fournisseur "
							+ msg.getSender().getLocalName()
							+ " est désabonné au transporteur principal");
				} else {
					System.out
							.println("Erreur désabonnement transporteur: Le fournissuer "
									+ msg.getSender().getLocalName()
									+ " n'est pas abonné au transporteur principal");
				}

			} else {
				block();
			}

		}
	}

	private class ServiceFacturation extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("honorerContrat"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				AID abonne = msg.getSender();
				//System.out.println(msg.getContent()+" "+msg.getSender().getLocalName());
				if (abonnes.contains(abonne)) {
					int montant= Integer.parseInt(msg.getContent())*prixTransport;
					FactureTransporteur f = new FactureTransporteur(getLocalName(), montant);
						
						ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
						try {
							msg1.addReceiver(abonne);
							msg1.setContentObject(f);
							msg1.setConversationId("factureTransport");
							myAgent.send(msg1);
							benefice += Integer.parseInt(msg.getContent())*coutTransport;
							CA += montant;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							System.out.println("Erreur génération de facture");
						}
				} else {
					System.out
							.println("Erreur facturation transporteur: Le fournissuer "
									+ msg.getSender().getLocalName()
									+ " n'est pas abonné au transporteur principal");
				}

			} else {
				block();
			}

		}

	}

	private class ServiceReceptionPaiement extends CyclicBehaviour{

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
					MessageTemplate.MatchConversationId("paiementFactureTransporteur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				CA += Integer.parseInt(msg.getContent());
				benefice += Integer.parseInt(msg.getContent());
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
				InfoAgent info = new InfoAgent(getLocalName(),
						abonnes.size() + "", CA + "", benefice + "");
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
}
