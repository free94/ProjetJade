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
	// Les fournisseur vers lesquels on a envoyé les messages de début de tour et attend leur réponses
	private ArrayList<AID> listeFourReponse = new ArrayList<AID>();
	// Les transporteur inscrit dans le système
	private ArrayList<AID> listeTrans = new ArrayList<AID>();
	//L'observateur
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
		//ajouter le service pour traiter les demandes d'inscription des fournisseur/Transporteur/observateur
		addBehaviour(new ServiceInscription());
		
		// Traiter les messages de fin de tour à chaque tic d'horloge
		addBehaviour(new TickerBehaviour(this, periodeTic) {
			protected void onTick() {
					addBehaviour(new Tour());
			}
		});
		
		System.out.println("L'horloge : " + getAID().getName()
				+ " est prêt.");
	}

	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("L'agent horlogue est terminé.");
	}
	
	private class ServiceInscription extends CyclicBehaviour{

		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId("inscriptionHorloge"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String demandeur = msg.getContent();
				if (demandeur.equals("fournisseur")) {
					listeFour.add(msg.getSender());
				}else if (demandeur.equals("transporteur")) {
					listeTrans.add(msg.getSender());
				}else if (demandeur.equals("observateur")) {
					observateur=msg.getSender();
				}
				System.out.println("un agent "+demandeur+":"+msg.getSender().getLocalName()+" est incrit à l'horloge");
			}
			else {
				block();
			}

		}		
	}
	
	private class Tour extends Behaviour{
		private int step = 0;
		private MessageTemplate mt = MessageTemplate
		.MatchConversationId("msgFinDeTour");
		@Override
		public void action() {
			switch (step) {
			case 0:
				//Reception de messages fin de tour
				if (listeFourReponse.size() == 0) {
					//Tous les messages fin de tour sont recu
					step = 1;
				}else{
					ACLMessage msg = receive(mt);
					if (msg != null) {
						listeFourReponse.remove(msg.getSender());
					}else{
						block();
					}
				}
				break;
			case 1:
				//Emettre les messages debut de tour
				ACLMessage cmd = new ACLMessage(ACLMessage.INFORM);
				for (AID fournisseur : listeFour) {
					cmd.addReceiver(fournisseur);
					listeFourReponse.add(fournisseur);
				}
				cmd.setConversationId("msgDebutTour");
				myAgent.send(cmd);
				step = 2;
				break;
			}

//		for (AID transporteur : listeTrans) {
//			MessageTemplate mt = MessageTemplate.and(MessageTemplate
//					.MatchConversationId("msgFinDeTour"),
//					MessageTemplate.MatchSender(transporteur));
//			ACLMessage msg = blockingReceive(mt);
//		}
//		MessageTemplate mt = MessageTemplate.and(MessageTemplate
//				.MatchConversationId("msgFinDeTour"),
//				MessageTemplate.MatchSender(observateur));
//		ACLMessage msg = blockingReceive(mt);
		//Informer les agents de commencer un nouveau tour
		
//		for (AID transporteur : listeTrans) {
//			cmd.addReceiver(transporteur);
//		}
//		cmd.addReceiver(observateur);

		}

		@Override
		public boolean done() {
			if (step == 2) {
				return true;
			}
			return false;
		}
		
	}
	
}
