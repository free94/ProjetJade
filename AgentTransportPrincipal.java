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
	private static int prixTransport = 4;
	private static int coutTransport = 1;
	// *-*-*-*-*-*-*-*-*-*variables*-*-*-*-*-*-*-*-*-*
	private int CA = 0;
	private int benefice = 0;
	private int nbClient = 0;

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

		// ajouter le comportement pour traiter les demandes de prix
		addBehaviour(new ServiceDemandeTarif());
		// ajouter le comportement pour la facturation
		addBehaviour(new ServiceFacturation());
		// ajout du service pour recevoir les paiement des factures
		addBehaviour(new ServiceReceptionPaiement());
		// adoute du service pour fournir information à l'observateur
		addBehaviour(new ServiceObservateur());
		System.out.println("Le transporteur principal: " + getAID().getName()
				+ " est prêt.");
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

	// comportement lors de la demande du tarif au kwh : plus de notion de devis
	private class ServiceDemandeTarif extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchConversationId("demandeTarif-transporteurPrincipal"),
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage reply = msg.createReply();
				reply.setPerformative(ACLMessage.PROPOSE);
				reply.setContent(prixTransport + "");
				reply.setConversationId("reponseTarif-transporteurPrincipal");
				myAgent.send(reply);
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
				nbClient += 1;
				AID abonne = msg.getSender();
				// System.out.println(msg.getContent()+" "+msg.getSender().getLocalName());
				int montant = Integer.parseInt(msg.getContent())
						* prixTransport;
				FactureTransporteur f = new FactureTransporteur(getLocalName(),
						montant);

				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				try {
					msg1.addReceiver(abonne);
					msg1.setContentObject(f);
					msg1.setConversationId("factureTransport");
					myAgent.send(msg1);
					benefice += Integer.parseInt(msg.getContent())
							* coutTransport;
					CA += montant;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Erreur génération de facture");
				}

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

	private class ServiceObservateur extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("observateur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage reply = msg.createReply();
				InfoAgent info = new InfoAgent(getLocalName(), nbClient + "",
						CA + "", benefice + "", prixTransport + "", 0 + "");
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

}
