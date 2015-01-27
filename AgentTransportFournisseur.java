import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

//agent de transport que tout fournisseur peut créer
public class AgentTransportFournisseur extends Agent {
	
	//nos variables
	AID createur = new AID();
	private ArrayList<AID> abonnes = new ArrayList<AID>();
	private int CA = 0;
	private int benefice = 0;
	
	
	protected void setup() {
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
		addBehaviour(new ServiceObservateur());
	}
	
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Le transporteur fournisseur: "+getAID().getName()+" est terminé.");
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
				//Si (cas rendu 3) la demande provient du créateur : cela ne lui coutera rien
				if (msg.getConversationId() == "abonnementCreateur") {
					montant = 0;
					receiver = createur;
				}
				else{
					//cas rendu 3, où d'autres fournisseurs que le créateur peuvent faire appel à ce transporteur perso
					if (abonnes.contains(msg.getSender())) {
						//montant = ;
						//receiver = msg.getSender();
					}
				}
				
				FactureTransporteur f = new FactureTransporteur(getLocalName(), montant);						
				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				try {
					msg1.addReceiver(receiver);
					msg1.setContentObject(f);
					msg1.setConversationId("factureTransport");
					myAgent.send(msg1);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Erreur génération de facture");
				}
								
			} else {
				block();
			}

		}
	}
	
	private class ServiceAbonnement extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate
					.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				
				//ACLMessage reply = msg.createReply();
				if(msg.getConversationId() == "abonnementCreateur"){
					createur = msg.getSender();
					abonnes.add(createur);
				}
				//Cas rendu 3 : abonnement d'autres fournisseurs
				/*else {
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("capacité insuffisante");
				}
				myAgent.send(reply);*/
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
